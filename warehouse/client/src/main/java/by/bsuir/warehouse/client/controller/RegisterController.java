package by.bsuir.warehouse.client.controller;

import by.bsuir.warehouse.client.network.ClientContext;
import by.bsuir.warehouse.common.model.Role;
import by.bsuir.warehouse.common.model.User;
import by.bsuir.warehouse.common.util.PasswordUtil;
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
import java.util.List;
import java.util.stream.Collectors;

public class RegisterController {

    @FXML private TextField usernameField, fullNameField, phoneField, emailField;
    @FXML private PasswordField passwordField, confirmPasswordField;
    @FXML private ComboBox<Role> roleCombo;
    @FXML private Label errorLabel;
    @FXML private Button registerButton;

    @FXML
    public void initialize() {
        new Thread(() -> {
            try {
                ClientContext ctx = ClientContext.getInstance();
                if (!ctx.isConnected()) ctx.connect();
                Response resp = ctx.send(new Request.Builder(Action.GET_ALL_ROLES).build());
                Platform.runLater(() -> {
                    if (resp.isSuccess() && resp.getData() instanceof List<?> list) {
                        List<Role> filteredRoles = ((List<Role>) list).stream()
                                .filter(r -> !Role.ADMIN.equals(r.getRoleName()))
                                .collect(Collectors.toList());
                        roleCombo.getItems().setAll(filteredRoles);
                        roleCombo.setConverter(new javafx.util.StringConverter<>() {
                            public String toString(Role r) { return r != null ? r.getRoleName() : ""; }
                            public Role fromString(String s) { return null; }
                        });
                    } else {
                        errorLabel.setText("Не удалось загрузить роли: " + resp.getMessage());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> errorLabel.setText(
                        "Ошибка соединения с сервером. Убедитесь, что сервер запущен."));
            }
        }).start();
    }

    @FXML
    public void handleRegister() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String confirm = confirmPasswordField.getText();
        Role selectedRole = roleCombo.getValue();
        String fullName = fullNameField.getText().trim();
        String phone = phoneField.getText().trim();
        String email = emailField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            errorLabel.setText("Логин и пароль обязательны");
            return;
        }
        if (!password.equals(confirm)) {
            errorLabel.setText("Пароли не совпадают");
            return;
        }
        if (selectedRole == null) {
            errorLabel.setText("Выберите роль");
            return;
        }
        if (fullName.isEmpty()) {
            showAlert("Ошибка", "ФИО обязательно для заполнения.");
            return;
        }
        if (!fullName.matches("^[А-ЯЁ][а-яё]+(-[А-ЯЁ][а-яё]+)? [А-ЯЁ][а-яё]+( [А-ЯЁ][а-яё]+)?$")) {
            showAlert("Ошибка", "ФИО должно содержать фамилию и имя (русские буквы, допускается двойная фамилия через дефис). Можно указать отчество.");
            return;
        }
        if (phone.isEmpty()) {
            showAlert("Ошибка", "Номер телефона обязателен.");
            return;
        }
        if (!phone.matches("^\\+375\\d{9}$")) {
            showAlert("Ошибка", "Номер телефона должен быть в формате +375XXXXXXXXX (12 цифр).");
            return;
        }
        if (!email.isEmpty() && !email.matches("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$")) {
            showAlert("Ошибка", "Некорректный формат email.");
            return;
        }

        String passwordError = PasswordUtil.validatePassword(password);
        if (passwordError != null) {
            Alert alert = new Alert(Alert.AlertType.WARNING, passwordError, ButtonType.OK);
            alert.setHeaderText("Слабый пароль");
            alert.showAndWait();
            return;
        }

        User newUser = new User();
        newUser.setUsername(username);
        newUser.setPasswordHash(PasswordUtil.hash(password));
        newUser.setRole(selectedRole);
        newUser.setFullName(fullName);
        newUser.setPhone(phone);
        newUser.setEmail(email);

        registerButton.setDisable(true);
        errorLabel.setText("");

        new Thread(() -> {
            try {
                ClientContext ctx = ClientContext.getInstance();
                if (!ctx.isConnected()) ctx.connect();
                Response resp = ctx.send(new Request.Builder(Action.REGISTER)
                        .payload(newUser).build());
                Platform.runLater(() -> {
                    registerButton.setDisable(false);
                    if (resp.isSuccess()) {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION,
                                resp.getMessage(), ButtonType.OK);
                        alert.showAndWait();
                        openLogin();
                    } else {
                        errorLabel.setText(resp.getMessage());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    registerButton.setDisable(false);
                    errorLabel.setText("Ошибка соединения: " + e.getMessage());
                });
            }
        }).start();
    }

    @FXML
    public void openLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/by/bsuir/warehouse/client/fxml/Login.fxml"));
            Stage stage = (Stage) registerButton.getScene().getWindow();

            Scene scene = new Scene(loader.load(), 420, 520);
            scene.getStylesheets().add(
                    getClass().getResource("/by/bsuir/warehouse/client/css/login-style.css")
                            .toExternalForm());

            stage.setScene(scene);
            stage.setTitle("Складской учёт — Вход");
            stage.setResizable(false);
        } catch (IOException e) {
            errorLabel.setText("Ошибка перехода на вход");
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}