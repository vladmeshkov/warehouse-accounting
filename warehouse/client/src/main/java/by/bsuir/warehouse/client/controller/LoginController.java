package by.bsuir.warehouse.client.controller;

import by.bsuir.warehouse.client.network.ClientContext;
import by.bsuir.warehouse.common.model.User;
import by.bsuir.warehouse.common.protocol.Action;
import by.bsuir.warehouse.common.protocol.Request;
import by.bsuir.warehouse.common.protocol.Response;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Контроллер экрана авторизации.
 *
 * При успешном входе:
 *   1. Сохраняет User в ClientContext
 *   2. Открывает главное окно Main.fxml
 *   3. Закрывает окно входа
 */
public class LoginController {

    private static final Logger log = Logger.getLogger(LoginController.class.getName());

    @FXML private TextField     usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField     hostField;
    @FXML private TextField     portField;
    @FXML private Label         errorLabel;
    @FXML private Button        loginButton;

    @FXML
    public void initialize() {
        ClientContext ctx = ClientContext.getInstance();
        hostField.setText(ctx.getServerHost());
        portField.setText(String.valueOf(ctx.getServerPort()));

        // Enter в поле пароля — попытка входа
        passwordField.setOnAction(e -> handleLogin());

        errorLabel.setText("");
    }

    @FXML
    public void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String host     = hostField.getText().trim();
        String portStr  = portField.getText().trim();

        // Базовая валидация на клиенте
        if (username.isEmpty() || password.isEmpty()) {
            showError("Введите логин и пароль");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            showError("Неверный порт: " + portStr);
            return;
        }

        // Блокируем кнопку на время запроса
        loginButton.setDisable(true);
        errorLabel.setText("Подключение...");

        // Выполняем в фоновом потоке — не блокируем UI
        Thread loginThread = new Thread(() -> {
            try {
                ClientContext ctx = ClientContext.getInstance();
                ctx.saveConnectionSettings(host, port);
                ctx.connect();

                Request request = new Request.Builder(Action.LOGIN)
                        .param("username", username)
                        .param("password", password)
                        .build();

                Response response = ctx.send(request);

                Platform.runLater(() -> {
                    if (response.isSuccess()) {
                        User user = (User) response.getData();
                        ctx.setCurrentUser(user);
                        log.info("Вход выполнен: " + user.getUsername()
                                 + " [" + user.getRole().getRoleName() + "]");
                        openMainWindow();
                    } else {
                        loginButton.setDisable(false);
                        showError(response.getMessage());
                    }
                });

            } catch (IOException e) {
                Platform.runLater(() -> {
                    loginButton.setDisable(false);
                    showError("Нет соединения с сервером: " + host + ":" + port);
                });
            }
        });
        loginThread.setDaemon(true);
        loginThread.start();
    }

    private void openMainWindow() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/by/bsuir/warehouse/client/fxml/Main.fxml"));
            Stage mainStage = new Stage();
            Scene scene = new Scene(loader.load(), 1100, 700);
            scene.getStylesheets().add(
                    getClass().getResource("/by/bsuir/warehouse/client/css/style.css")
                              .toExternalForm());

            User user = ClientContext.getInstance().getCurrentUser();
            mainStage.setTitle("Складской учёт  —  "
                    + user.getFullName() + "  [" + user.getRole().getRoleName() + "]");
            mainStage.setScene(scene);
            mainStage.setMinWidth(900);
            mainStage.setMinHeight(600);
            mainStage.show();

            // Закрываем окно входа
            Stage loginStage = (Stage) loginButton.getScene().getWindow();
            loginStage.close();

        } catch (IOException e) {
            showError("Ошибка открытия главного окна: " + e.getMessage());
            loginButton.setDisable(false);
        }
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
    }
}
