package by.bsuir.warehouse.server;

import by.bsuir.warehouse.server.config.DBConnection;
import by.bsuir.warehouse.server.config.ServerConfig;
import by.bsuir.warehouse.server.network.ClientHandler;
import by.bsuir.warehouse.server.network.RequestDispatcher;
import by.bsuir.warehouse.server.service.AuthService;
import by.bsuir.warehouse.server.service.ForecastService;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.*;

/**
 * Точка входа серверного приложения.
 *
 * Запуск:
 *   java -jar warehouse-server.jar
 *   (параметры берутся из config.properties в classpath)
 *
 * Архитектура:
 *   ServerSocket слушает порт → при подключении клиента создаёт ClientHandler
 *   и передаёт его в пул потоков ExecutorService (фиксированный размер).
 *   Каждый ClientHandler обслуживает одного клиента на всё время сессии.
 */
public class ServerApp {

    private static final Logger log = Logger.getLogger(ServerApp.class.getName());

    public static void main(String[] args) {
        configureLogging();
        ServerConfig config = ServerConfig.getInstance();

        log.info("╔══════════════════════════════════════════════╗");
        log.info("║  Warehouse Accounting Server  v1.0.0         ║");
        log.info("║  БГУИР, кафедра ЭИ, 2026                    ║");
        log.info("╚══════════════════════════════════════════════╝");

        // Проверяем подключение к БД при старте
        log.info("Проверка подключения к MySQL...");
        DBConnection.getInstance().getConnection();
        log.info("Подключение к MySQL успешно.");

        // Инициализируем сервисы
        AuthService authService = new AuthService();
        ForecastService forecastService = new ForecastService();
        RequestDispatcher dispatcher = new RequestDispatcher(authService, forecastService);

        // Запускаем планировщик прогнозирования
        forecastService.startScheduler();

        // Пул потоков для клиентских подключений
        int poolSize = config.getThreadPoolSize();
        ExecutorService threadPool = Executors.newFixedThreadPool(poolSize);
        log.info("Пул потоков: " + poolSize + " потоков");

        int port = config.getServerPort();

        // Хук завершения — корректно останавливаем при Ctrl+C
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Остановка сервера...");
            forecastService.stopScheduler();
            threadPool.shutdown();
            try {
                if (!threadPool.awaitTermination(10, TimeUnit.SECONDS))
                    threadPool.shutdownNow();
            } catch (InterruptedException e) {
                threadPool.shutdownNow();
            }
            DBConnection.getInstance().close();
            log.info("Сервер остановлен.");
        }));

        // Основной цикл приёма подключений
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            log.info("Сервер запущен и слушает порт " + port);

            while (!serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    clientSocket.setSoTimeout(300_000); // 5 минут таймаут чтения
                    threadPool.execute(new ClientHandler(clientSocket, dispatcher));
                } catch (IOException e) {
                    if (!serverSocket.isClosed()) {
                        log.warning("Ошибка принятия подключения: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            log.severe("Не удалось запустить сервер на порту " + port + ": " + e.getMessage());
            System.exit(1);
        }
    }

    private static void configureLogging() {
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.INFO);
        for (Handler h : rootLogger.getHandlers()) {
            h.setFormatter(new SimpleFormatter() {
                @Override
                public String format(LogRecord r) {
                    return String.format("[%1$tH:%1$tM:%1$tS] [%2$-7s] %3$s%n",
                            r.getMillis(),
                            r.getLevel().getName(),
                            r.getMessage());
                }
            });
        }
    }
}
