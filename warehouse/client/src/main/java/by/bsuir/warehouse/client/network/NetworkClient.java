package by.bsuir.warehouse.client.network;

import by.bsuir.warehouse.common.protocol.Request;
import by.bsuir.warehouse.common.protocol.Response;

import java.io.*;
import java.net.Socket;
import java.util.logging.Logger;

public class NetworkClient {

    private static final Logger log = Logger.getLogger(NetworkClient.class.getName());
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 1000;

    private final String host;
    private final int port;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String sessionToken;

    private final Object sendLock = new Object();

    public NetworkClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void connect() throws IOException {
        synchronized (sendLock) {
            closeSilently();
            socket = new Socket(host, port);
            socket.setSoTimeout(30_000);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());
            log.info("Подключено к серверу: " + host + ":" + port);
        }
    }

    public void disconnect() {
        synchronized (sendLock) {
            sessionToken = null;
            closeSilently();
        }
    }

    public boolean isConnected() {
        synchronized (sendLock) {
            return socket != null && socket.isConnected() && !socket.isClosed();
        }
    }

    public Response send(Request request) throws IOException {
        // Добавляем токен к запросу, если он есть и запрос без токена
        if (sessionToken != null && request.getToken() == null) {
            Request.Builder builder = new Request.Builder(request.getAction())
                    .token(sessionToken)
                    .payload((Serializable) request.getPayload());
            // КОПИРУЕМ ВСЕ ИСХОДНЫЕ ПАРАМЕТРЫ с подробным логом
            if (request.getParams() != null) {
                System.out.println("NetworkClient: Копирую параметры: " + request.getParams());
                request.getParams().forEach(builder::param);
            } else {
                System.out.println("NetworkClient: Исходный запрос без параметров!");
            }
            request = builder.build();
        }

        System.out.println("NetworkClient: Отправляю запрос action=" + request.getAction() +
                ", token=" + request.getToken() + ", params=" + request.getParams());

        synchronized (sendLock) {
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

                    if (response.isSuccess() && response.getToken() != null) {
                        sessionToken = response.getToken();
                    }
                    return response;

                } catch (IOException | ClassNotFoundException e) {
                    log.warning("Попытка " + attempt + " неудачна: " + e.getMessage());
                    closeSilently();
                    if (attempt == MAX_RETRIES) {
                        throw new IOException("Не удалось отправить запрос после "
                                + MAX_RETRIES + " попыток: " + e.getMessage(), e);
                    }
                    try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        throw new IOException("Соединение с сервером недоступно");
    }

    private void closeSilently() {
        try { if (out != null) { out.close(); } } catch (IOException ignored) {}
        try { if (in != null) { in.close(); } } catch (IOException ignored) {}
        try { if (socket != null) { socket.close(); } } catch (IOException ignored) {}
        out = null;
        in = null;
        socket = null;
    }

    public String getSessionToken() { return sessionToken; }
    public void clearSessionToken() { sessionToken = null; }
}