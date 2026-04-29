package by.bsuir.warehouse.client.network;

import by.bsuir.warehouse.common.model.User;
import by.bsuir.warehouse.common.protocol.Request;
import by.bsuir.warehouse.common.protocol.Response;

import java.io.IOException;
import java.util.prefs.Preferences;

/**
 * Контекст клиентского приложения (Singleton).
 *
 * Хранит:
 *   - NetworkClient (соединение с сервером)
 *   - текущего аутентифицированного пользователя
 *   - настройки подключения (хост, порт)
 *
 * Используется всеми контроллерами JavaFX для отправки запросов.
 */
public class ClientContext {

    private static volatile ClientContext instance;

    private NetworkClient client;
    private User          currentUser;

    // Настройки подключения — сохраняются в Preferences
    private String serverHost;
    private int    serverPort;

    private ClientContext() {
        Preferences prefs = Preferences.userNodeForPackage(ClientContext.class);
        this.serverHost = prefs.get("server.host", "localhost");
        this.serverPort = prefs.getInt("server.port", 8888);
    }

    public static ClientContext getInstance() {
        if (instance == null) {
            synchronized (ClientContext.class) {
                if (instance == null) instance = new ClientContext();
            }
        }
        return instance;
    }

    // ── Подключение ───────────────────────────────────────────────────────

    public void connect() throws IOException {
        client = new NetworkClient(serverHost, serverPort);
        client.connect();
    }

    public void disconnect() {
        if (client != null) client.disconnect();
        currentUser = null;
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    // ── Отправка запросов ─────────────────────────────────────────────────

    /**
     * Отправляет запрос и возвращает Response.
     * Автоматически добавляет токен сессии.
     */
    public Response send(Request request) throws IOException {
        if (client == null || !client.isConnected()) {
            connect();
        }
        // Добавляем токен если запрос без него
        if (client.getSessionToken() != null && request.getToken() == null) {
            request = new Request.Builder(request.getAction())
                    .token(client.getSessionToken())
                    .payload(request.getPayload())
                    .build();
        }
        return client.send(request);
    }

    // ── Геттеры/сеттеры ───────────────────────────────────────────────────

    public User getCurrentUser()           { return currentUser; }
    public void setCurrentUser(User user)  { this.currentUser = user; }

    public String getServerHost()          { return serverHost; }
    public int    getServerPort()          { return serverPort; }

    public void saveConnectionSettings(String host, int port) {
        this.serverHost = host;
        this.serverPort = port;
        Preferences prefs = Preferences.userNodeForPackage(ClientContext.class);
        prefs.put("server.host", host);
        prefs.putInt("server.port", port);
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public boolean hasRole(String roleName) {
        return currentUser != null && currentUser.hasRole(roleName);
    }
}
