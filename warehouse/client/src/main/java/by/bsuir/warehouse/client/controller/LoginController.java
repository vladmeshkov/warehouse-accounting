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

public class LoginController {

    private static final Logger log = Logger.getLogger(LoginController.class.getName());

    @FXML private TextField     usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label         errorLabel;
    @FXML private Button        loginButton;

    @FXML
    public void initialize() {
        passwordField.setOnAction(e -> handleLogin());
        errorLabel.setText("");
    }

    @FXML
    public void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Введите логин и пароль");
            return;
        }

        loginButton.setDisable(true);
        errorLabel.setText("Подключение...");

        Thread loginThread = new Thread(() -> {
            try {
                ClientContext ctx = ClientContext.getInstance();
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
                        openMainWindow();
                    } else {
                        loginButton.setDisable(false);
                        showError(response.getMessage());
                    }
                });
            } catch (IOException e) {
                Platform.runLater(() -> {
                    loginButton.setDisable(false);
                    showError("Нет соединения с сервером (localhost:8888). Убедитесь, что сервер запущен.");
                });
            }
        });
        loginThread.setDaemon(true);
        loginThread.start();
    }

    @FXML
    public void openRegisterWindow() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/by/bsuir/warehouse/client/fxml/Register.fxml"));
            Stage stage = (Stage) loginButton.getScene().getWindow();

            // Устанавливаем размер специально для регистрации
            Scene scene = new Scene(loader.load(), 480, 620);
            scene.getStylesheets().add(
                    getClass().getResource("/by/bsuir/warehouse/client/css/login-style.css")
                            .toExternalForm());

            stage.setScene(scene);
            stage.setTitle("Регистрация нового пользователя");
            stage.setResizable(false);
        } catch (IOException e) {
            errorLabel.setText("Ошибка открытия формы регистрации");
        }
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