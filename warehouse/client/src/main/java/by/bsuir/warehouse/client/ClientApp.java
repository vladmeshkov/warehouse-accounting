package by.bsuir.warehouse.client;

import by.bsuir.warehouse.client.network.ClientContext;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.util.logging.*;

/**
 * Точка входа клиентского JavaFX-приложения.
 *
 * Запуск:
 *   mvn -pl client javafx:run
 *   или: java -jar warehouse-client.jar
 *
 * Первый экран — форма авторизации (Login.fxml).
 * После успешного входа открывается главное окно (Main.fxml).
 */
public class ClientApp extends Application {

    private static final Logger log = Logger.getLogger(ClientApp.class.getName());

    @Override
    public void start(Stage primaryStage) throws Exception {
        configureLogging();

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/by/bsuir/warehouse/client/fxml/Login.fxml"));
        Scene scene = new Scene(loader.load(), 420, 320);
        scene.getStylesheets().add(
                getClass().getResource("/by/bsuir/warehouse/client/css/style.css")
                          .toExternalForm());

        primaryStage.setTitle("Складской учёт — Вход");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();

        // Корректное завершение при закрытии окна
        primaryStage.setOnCloseRequest(e -> {
            ClientContext.getInstance().disconnect();
            log.info("Приложение закрыто пользователем.");
        });
    }

    @Override
    public void stop() {
        ClientContext.getInstance().disconnect();
    }

    public static void main(String[] args) {
        launch(args);
    }

    private static void configureLogging() {
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.INFO);
        for (Handler h : rootLogger.getHandlers()) {
            h.setFormatter(new SimpleFormatter() {
                @Override
                public String format(LogRecord r) {
                    return String.format("[%1$tH:%1$tM:%1$tS] [%2$-7s] %3$s%n",
                            r.getMillis(), r.getLevel().getName(), r.getMessage());
                }
            });
        }
    }
}
