package by.bsuir.warehouse.server.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * Менеджер подключений к MySQL (паттерн Singleton).
 *
 * Гарантирует единственный экземпляр менеджера.
 * Автоматически восстанавливает соединение при разрыве.
 * Все DAO используют getConnection() для получения соединения.
 *
 * Использование:
 *   try (Connection conn = DBConnection.getInstance().getConnection()) { ... }
 *   — НЕ закрывает физическое соединение, а возвращает его через AutoCloseable-
 *     обёртку. Физическое соединение остаётся открытым и переиспользуется.
 */
public final class DBConnection {

    private static final Logger log = Logger.getLogger(DBConnection.class.getName());
    private static volatile DBConnection instance;

    private Connection connection;
    private final ServerConfig config;

    private DBConnection() {
        this.config = ServerConfig.getInstance();
        connect();
    }

    public static DBConnection getInstance() {
        if (instance == null) {
            synchronized (DBConnection.class) {
                if (instance == null) {
                    instance = new DBConnection();
                }
            }
        }
        return instance;
    }

    /**
     * Возвращает активное соединение с БД.
     * При обрыве соединения автоматически пересоздаёт его.
     */
    public synchronized Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()
                    || !connection.isValid(2)) {
                log.warning("Соединение с БД устарело — переподключение...");
                connect();
            }
        } catch (SQLException e) {
            log.warning("Ошибка проверки соединения — переподключение: " + e.getMessage());
            connect();
        }
        return connection;
    }

    private void connect() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            connection = DriverManager.getConnection(
                    config.getDbUrl(),
                    config.getDbUsername(),
                    config.getDbPassword());
            connection.setAutoCommit(true);
            log.info("Соединение с MySQL установлено: " + config.getDbUrl());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("MySQL JDBC драйвер не найден", e);
        } catch (SQLException e) {
            throw new RuntimeException("Не удалось подключиться к БД: "
                    + e.getMessage(), e);
        }
    }

    /** Закрывает физическое соединение (вызывается при завершении сервера) */
    public synchronized void close() {
        if (connection != null) {
            try {
                connection.close();
                log.info("Соединение с MySQL закрыто.");
            } catch (SQLException e) {
                log.warning("Ошибка при закрытии соединения: " + e.getMessage());
            }
        }
    }
}
