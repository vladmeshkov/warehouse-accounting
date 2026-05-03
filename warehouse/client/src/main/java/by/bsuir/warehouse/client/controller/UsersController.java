package by.bsuir.warehouse.client.controller;

import by.bsuir.warehouse.client.network.ClientContext;
import by.bsuir.warehouse.common.model.Role;
import by.bsuir.warehouse.common.model.User;
import by.bsuir.warehouse.common.protocol.Action;
import by.bsuir.warehouse.common.protocol.Request;
import by.bsuir.warehouse.common.protocol.Response;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UsersController {

    @FXML private TableView<User> usersTable;
    @FXML private TableColumn<User, Integer> colId;
    @FXML private TableColumn<User, String> colUsername, colFullName, colRole, colPhone, colEmail, colActive;
    @FXML private Label statusLabel;

    private final ObservableList<User> list = FXCollections.observableArrayList();
    private List<Role> allRoles = new ArrayList<>();

    @FXML
    public void initialize() {
        colId      .setCellValueFactory(new PropertyValueFactory<>("id"));
        colUsername.setCellValueFactory(new PropertyValueFactory<>("username"));
        colFullName.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        colRole    .setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getRole() != null ? c.getValue().getRole().getRoleName() : "—"));
        colPhone   .setCellValueFactory(new PropertyValueFactory<>("phone"));
        colEmail   .setCellValueFactory(new PropertyValueFactory<>("email"));
        colActive  .setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isActive() ? "✓ Да" : "✗ Нет"));
        usersTable.setItems(list);
        usersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        new Thread(() -> {
            try {
                Response r = ClientContext.getInstance()
                        .send(new Request.Builder(Action.GET_ALL_ROLES).build());
                Platform.runLater(() -> {
                    if (r.isSuccess() && r.getData() instanceof List<?> l)
                        allRoles = (List<Role>) l;
                });
            } catch (Exception ignored) {}
        }).start();

        loadData();
    }

    @FXML
    public void loadData() {
        new Thread(() -> {
            try {
                Response r = ClientContext.getInstance()
                        .send(new Request.Builder(Action.GET_ALL_USERS).build());
                Platform.runLater(() -> {
                    if (r.isSuccess() && r.getData() instanceof List<?> l) {
                        list.setAll((List<User>) l);
                        statusLabel.setText("Пользователей: " + list.size());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Ошибка: " + e.getMessage()));
            }
        }).start();
    }

    @FXML
    public void handleAdd() {
        showDialog(null).ifPresent(u -> new Thread(() -> {
            try {
                Response r = ClientContext.getInstance()
                        .send(new Request.Builder(Action.CREATE_USER).payload(u).build());
                Platform.runLater(() -> {
                    if (r.isSuccess()) {
                        loadData();
                        statusLabel.setText("Пользователь создан");
                    } else {
                        new Alert(Alert.AlertType.ERROR, r.getMessage()).showAndWait();
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait());
            }
        }).start());
    }

    @FXML
    public void handleEdit() {
        User sel = usersTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        showDialog(sel).ifPresent(u -> {
            u.setId(sel.getId());
            new Thread(() -> {
                try {
                    Response r = ClientContext.getInstance()
                            .send(new Request.Builder(Action.UPDATE_USER).payload(u).build());
                    Platform.runLater(() -> {
                        if (r.isSuccess()) {
                            loadData();
                            statusLabel.setText("Пользователь обновлён");
                        } else {
                            new Alert(Alert.AlertType.ERROR, r.getMessage()).showAndWait();
                        }
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait());
                }
            }).start();
        });
    }

    @FXML
    public void handleBlock() {
        User sel = usersTable.getSelectionModel().getSelectedItem();
        if (sel == null || sel.getId() <= 0) { statusLabel.setText("Выберите пользователя"); return; }
        new Thread(() -> {
            try {
                Response r = ClientContext.getInstance().send(
                        new Request.Builder(Action.DEACTIVATE_USER)
                                .param("id", String.valueOf(sel.getId()))
                                .param("active", "false").build());
                Platform.runLater(() -> {
                    if (r.isSuccess()) { loadData(); statusLabel.setText("Заблокирован"); }
                    else new Alert(Alert.AlertType.ERROR, r.getMessage()).showAndWait();
                });
            } catch (Exception e) {
                Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait());
            }
        }).start();
    }

    @FXML
    public void handleUnblock() {
        User sel = usersTable.getSelectionModel().getSelectedItem();
        if (sel == null || sel.getId() <= 0) { statusLabel.setText("Выберите пользователя"); return; }
        new Thread(() -> {
            try {
                Response r = ClientContext.getInstance().send(
                        new Request.Builder(Action.DEACTIVATE_USER)
                                .param("id", String.valueOf(sel.getId()))
                                .param("active", "true").build());
                Platform.runLater(() -> {
                    if (r.isSuccess()) { loadData(); statusLabel.setText("Разблокирован"); }
                    else new Alert(Alert.AlertType.ERROR, r.getMessage()).showAndWait();
                });
            } catch (Exception e) {
                Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait());
            }
        }).start();
    }

    @FXML
    public void handleDelete() {
        User sel = usersTable.getSelectionModel().getSelectedItem();
        if (sel == null || sel.getId() <= 0) { statusLabel.setText("Выберите пользователя"); return; }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Удалить пользователя «" + sel.getUsername() + "»?",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Подтверждение удаления");
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

        new Thread(() -> {
            try {
                Response r = ClientContext.getInstance().send(
                        new Request.Builder(Action.DELETE_USER)
                                .param("id", String.valueOf(sel.getId())).build());
                Platform.runLater(() -> {
                    if (r.isSuccess()) { loadData(); statusLabel.setText("Удалён"); }
                    else new Alert(Alert.AlertType.ERROR, r.getMessage()).showAndWait();
                });
            } catch (Exception e) {
                Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait());
            }
        }).start();
    }

    private Optional<User> showDialog(User e) {
        Dialog<User> d = new Dialog<>();
        d.setTitle(e == null ? "Добавить пользователя" : "Редактировать");
        ButtonType save = new ButtonType("Сохранить", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        GridPane g = new GridPane(); g.setHgap(10); g.setVgap(10); g.setPadding(new Insets(16));
        TextField fu = new TextField(e != null ? e.getUsername() : ""),
                ff = new TextField(e != null && e.getFullName() != null ? e.getFullName() : ""),
                fp = new TextField(e != null && e.getPhone()    != null ? e.getPhone()    : ""),
                fe = new TextField(e != null && e.getEmail()    != null ? e.getEmail()    : "");
        PasswordField fpw = new PasswordField();
        ComboBox<Role> rc = new ComboBox<>(FXCollections.observableArrayList(allRoles));
        rc.setConverter(new javafx.util.StringConverter<>() {
            public String toString(Role r) { return r == null ? "" : r.getRoleName(); }
            public Role fromString(String s) { return null; }
        });
        if (e != null) rc.setValue(e.getRole());
        g.add(new Label("Логин*:"), 0, 0); g.add(fu, 1, 0);
        g.add(new Label("ФИО:"),    0, 1); g.add(ff, 1, 1);
        g.add(new Label("Роль*:"),  0, 2); g.add(rc, 1, 2);
        g.add(new Label(e == null ? "Пароль*:" : "Новый пароль:"), 0, 3); g.add(fpw, 1, 3);
        g.add(new Label("Телефон:"),  0, 4); g.add(fp, 1, 4);
        g.add(new Label("Email:"),    0, 5); g.add(fe, 1, 5);
        d.getDialogPane().setContent(g);
        d.setResultConverter(btn -> {
            if (btn != save || fu.getText().trim().isEmpty() || rc.getValue() == null) return null;
            if (e == null && fpw.getText().isEmpty()) return null;
            User u = new User();
            u.setUsername(fu.getText().trim()); u.setFullName(ff.getText().trim());
            u.setRole(rc.getValue()); u.setPhone(fp.getText().trim()); u.setEmail(fe.getText().trim());
            u.setActive(true);
            if (!fpw.getText().isEmpty())
                u.setPasswordHash(by.bsuir.warehouse.common.util.PasswordUtil.hash(fpw.getText()));
            else if (e != null) u.setPasswordHash(e.getPasswordHash());
            return u;
        });
        return d.showAndWait();
    }
}