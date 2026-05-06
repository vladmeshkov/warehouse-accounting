package by.bsuir.warehouse.client.controller;

import by.bsuir.warehouse.client.network.ClientContext;
import by.bsuir.warehouse.common.model.User;
import by.bsuir.warehouse.common.model.UserProfile;
import by.bsuir.warehouse.common.protocol.Action;
import by.bsuir.warehouse.common.protocol.Request;
import by.bsuir.warehouse.common.protocol.Response;
import by.bsuir.warehouse.common.util.PasswordUtil;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

public class ProfileController {

    @FXML private Label fullNameLabel, roleLabel, usernameLabel, avatarLabel;
    @FXML private TextField fullNameField, phoneField, emailField;
    @FXML private Label statIncome, statOutcome, statTransfer, statInventory, statTotal;
    @FXML private Button editButton, saveButton, changePasswordButton;
    @FXML private Label messageLabel;

    private UserProfile currentProfile;

    @FXML
    public void initialize() {
        loadProfile();
        setEditMode(false);
    }

    private void loadProfile() {
        new Thread(() -> {
            try {
                Response r = ClientContext.getInstance()
                        .send(new Request.Builder(Action.GET_MY_PROFILE).build());
                Platform.runLater(() -> {
                    if (r.isSuccess() && r.getData() instanceof UserProfile profile) {
                        currentProfile = profile;
                        displayProfile(profile);
                        displayStats(profile.getStats());
                    } else {
                        messageLabel.setText("Ошибка загрузки профиля");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> messageLabel.setText("Ошибка соединения"));
            }
        }).start();
    }

    private void displayProfile(UserProfile profile) {
        User u = profile.getUser();
        fullNameLabel.setText(u.getFullName() != null ? u.getFullName() : "—");
        roleLabel.setText(u.getRole() != null ? u.getRole().getRoleName() : "—");
        usernameLabel.setText(u.getUsername());
        fullNameField.setText(u.getFullName() != null ? u.getFullName() : "");
        phoneField.setText(u.getPhone() != null ? u.getPhone() : "");
        emailField.setText(u.getEmail() != null ? u.getEmail() : "");

        String initials = (u.getFullName() != null && !u.getFullName().isEmpty())
                ? u.getFullName().substring(0, 1).toUpperCase()
                : u.getUsername().substring(0, 1).toUpperCase();
        avatarLabel.setText(initials);
    }

    private void displayStats(UserProfile.UserStats stats) {
        statIncome.setText(String.valueOf(stats.getTotalIncome()));
        statOutcome.setText(String.valueOf(stats.getTotalOutcome()));
        statTransfer.setText(String.valueOf(stats.getTotalTransfer()));
        statInventory.setText(String.valueOf(stats.getTotalInventory()));
        statTotal.setText(String.valueOf(stats.getTotal()));
    }

    @FXML
    public void handleEdit() {
        setEditMode(true);
    }

    @FXML
    public void handleSave() {
        User updated = new User();
        updated.setId(currentProfile.getUser().getId());
        updated.setFullName(fullNameField.getText().trim());
        updated.setPhone(phoneField.getText().trim());
        updated.setEmail(emailField.getText().trim());

        new Thread(() -> {
            try {
                Response r = ClientContext.getInstance()
                        .send(new Request.Builder(Action.UPDATE_MY_PROFILE)
                                .payload(updated).build());
                Platform.runLater(() -> {
                    if (r.isSuccess()) {
                        messageLabel.setText("Профиль обновлён");
                        loadProfile();
                        setEditMode(false);
                    } else {
                        messageLabel.setText("Ошибка: " + r.getMessage());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> messageLabel.setText("Ошибка соединения"));
            }
        }).start();
    }

    @FXML
    public void handleChangePassword() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Смена пароля");
        dialog.setHeaderText("Введите старый и новый пароль");

        ButtonType okButton = new ButtonType("Сменить", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okButton, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        PasswordField oldPass = new PasswordField();
        PasswordField newPass = new PasswordField();
        PasswordField confirmPass = new PasswordField();
        grid.add(new Label("Старый пароль:"), 0, 0);
        grid.add(oldPass, 1, 0);
        grid.add(new Label("Новый пароль:"), 0, 1);
        grid.add(newPass, 1, 1);
        grid.add(new Label("Подтверждение:"), 0, 2);
        grid.add(confirmPass, 1, 2);
        dialog.getDialogPane().setContent(grid);

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(okButton);
        okBtn.setDisable(true);
        newPass.textProperty().addListener((obs, o, n) ->
                okBtn.setDisable(oldPass.getText().isEmpty() || newPass.getText().isEmpty() || confirmPass.getText().isEmpty())
        );

        dialog.setResultConverter(btn -> {
            if (btn == okButton) {
                String oldPwd = oldPass.getText();
                String newPwd = newPass.getText();
                String confPwd = confirmPass.getText();

                // Новый пароль не должен совпадать со старым → Alert
                if (newPwd.equals(oldPwd)) {
                    Alert alert = new Alert(Alert.AlertType.WARNING,
                            "Новый пароль не должен совпадать со старым", ButtonType.OK);
                    alert.setHeaderText(null);
                    alert.showAndWait();
                    return null;
                }

                if (!newPwd.equals(confPwd)) {
                    messageLabel.setText("Новые пароли не совпадают");
                    return null;
                }
                String validationError = PasswordUtil.validatePassword(newPwd);
                if (validationError != null) {
                    messageLabel.setText(validationError);
                    return null;
                }

                try {
                    Response verifyResp = ClientContext.getInstance()
                            .send(new Request.Builder(Action.VERIFY_PASSWORD)
                                    .param("password", oldPwd).build());
                    if (!verifyResp.isSuccess()) {
                        messageLabel.setText("Неверный старый пароль");
                        return null;
                    }
                } catch (Exception e) {
                    messageLabel.setText("Ошибка проверки пароля");
                    return null;
                }

                return newPwd;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(newPassword -> {
            User updated = new User();
            updated.setId(currentProfile.getUser().getId());
            updated.setPasswordHash(PasswordUtil.hash(newPassword));

            new Thread(() -> {
                try {
                    Response r = ClientContext.getInstance()
                            .send(new Request.Builder(Action.UPDATE_MY_PROFILE)
                                    .payload(updated).build());
                    Platform.runLater(() -> {
                        if (r.isSuccess()) {
                            messageLabel.setText("Пароль изменён");
                        } else {
                            messageLabel.setText("Ошибка: " + r.getMessage());
                        }
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> messageLabel.setText("Ошибка соединения"));
                }
            }).start();
        });
    }

    private void setEditMode(boolean edit) {
        fullNameField.setEditable(edit);
        phoneField.setEditable(edit);
        emailField.setEditable(edit);
        editButton.setVisible(!edit);
        saveButton.setVisible(edit);
    }
}