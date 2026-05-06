package by.bsuir.warehouse.server.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class ServerConfig {

    private static volatile ServerConfig instance;
    private final Properties props = new Properties();

    private ServerConfig() {
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("config.properties")) {
            if (is == null) {
                throw new RuntimeException("config.properties не найден в classpath");
            }
            props.load(is);
        } catch (IOException e) {
            throw new RuntimeException("Ошибка загрузки конфигурации", e);
        }
    }

    public static ServerConfig getInstance() {
        if (instance == null) {
            synchronized (ServerConfig.class) {
                if (instance == null) {
                    instance = new ServerConfig();
                }
            }
        }
        return instance;
    }

    public int getServerPort() {
        return Integer.parseInt(props.getProperty("server.port", "8888"));
    }

    public int getThreadPoolSize() {
        return Integer.parseInt(props.getProperty("server.thread.pool.size", "20"));
    }

    public String getDbUrl()      { return props.getProperty("db.url"); }
    public String getDbUsername() { return props.getProperty("db.username"); }
    public String getDbPassword() { return props.getProperty("db.password"); }

    // Новый метод — путь к mysqldump
    public String getMysqldumpPath() {
        return props.getProperty("db.mysqldump.path", "mysqldump");
    }

    public double getForecastAlpha() {
        return Double.parseDouble(props.getProperty("forecast.alpha", "0.3"));
    }

    public int getForecastMinHistoryDays() {
        return Integer.parseInt(props.getProperty("forecast.min.history.days", "7"));
    }

    public int getForecastHistoryWindowDays() {
        return Integer.parseInt(props.getProperty("forecast.history.window.days", "30"));
    }

    public int getForecastCriticalDays() {
        return Integer.parseInt(props.getProperty("forecast.critical.days", "7"));
    }

    public int getForecastScheduleHour() {
        return Integer.parseInt(props.getProperty("forecast.schedule.hour", "2"));
    }

    public int getSessionTimeoutMinutes() {
        return Integer.parseInt(props.getProperty("session.timeout.minutes", "60"));
    }
}