package by.bsuir.warehouse.client.network;

import by.bsuir.warehouse.common.protocol.Request;
import by.bsuir.warehouse.common.protocol.Response;

import java.io.*;
import java.net.Socket;
import java.util.logging.Logger;

/**
 * Сетевой клиент — управляет TCP-соединением с сервером.
 *
 * Отправляет Request, получает Response.
 * При разрыве соединения автоматически переподключается (до 3 попыток).
 * Хранит токен сессии после успешного LOGIN.
 *
 * Использование:
 *   NetworkClient client = new NetworkClient("localhost", 8888);
 *   Response resp = client.send(new Request.Builder(Action.LOGIN)
 *       .param("username", "admin")
 *       .param("password", "admin123")
 *       .build());
 */
public class NetworkClient {

    private static final Logger log = Logger.getLogger(NetworkClient.class.getName());
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 1000;

    private final String host;
    private final int    port;

    private Socket           socket;
    private ObjectOutputStream out;
    private ObjectInputStream  in;
    private String             sessionToken;

    public NetworkClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    // ── Подключение ───────────────────────────────────────────────────────

    public void connect() throws IOException {
        socket = new Socket(host, port);
        socket.setSoTimeout(30_000);
        // ВАЖНО: ObjectOutputStream создаётся ПЕРВЫМ, затем ObjectInputStream
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in  = new ObjectInputStream(socket.getInputStream());
        log.info("Подключено к серверу: " + host + ":" + port);
    }

    public void disconnect() {
        sessionToken = null;
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            log.warning("Ошибка отключения: " + e.getMessage());
        }
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    // ── Отправка запроса ──────────────────────────────────────────────────

    /**
     * Отправляет запрос серверу и возвращает ответ.
     * Автоматически добавляет токен сессии к запросу.
     * При обрыве соединения — переподключается и повторяет попытку.
     */
    public Response send(Request request) throws IOException {
        // Добавляем токен к запросу если он есть
        if (sessionToken != null && request.getToken() == null) {
            request = new Request.Builder(request.getAction())
                    .token(sessionToken)
                    .payload((Serializable) request.getPayload())
                    .build();
            // Копируем параметры
        }

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (!isConnected()) {
                    log.info("Переподключение (попытка " + attempt + ")...");
                    connect();
                }
                out.writeObject(request);
                out.flush();
                out.reset();
                Response response = (Response) in.readObject();

                // Сохраняем токен если это ответ на LOGIN
                if (response.isSuccess() && response.getToken() != null) {
                    sessionToken = response.getToken();
                }
                return response;

            } catch (IOException | ClassNotFoundException e) {
                log.warning("Попытка " + attempt + " неудачна: " + e.getMessage());
                closeStreams();
                if (attempt == MAX_RETRIES) {
                    throw new IOException("Не удалось отправить запрос после "
                            + MAX_RETRIES + " попыток: " + e.getMessage(), e);
                }
                try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        throw new IOException("Соединение с сервером недоступно");
    }

    private void closeStreams() {
        try { if (out != null) out.close(); } catch (IOException ignored) {}
        try { if (in  != null) in.close();  } catch (IOException ignored) {}
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    public String getSessionToken()    { return sessionToken; }
    public void   clearSessionToken()  { sessionToken = null; }
}
