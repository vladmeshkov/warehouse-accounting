package by.bsuir.warehouse.client.controller;

import by.bsuir.warehouse.client.network.ClientContext;
import by.bsuir.warehouse.common.model.User;
import by.bsuir.warehouse.common.protocol.Action;
import by.bsuir.warehouse.common.protocol.Request;
import by.bsuir.warehouse.common.protocol.Response;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import java.util.List;
import java.util.Optional;

public class RegistrationRequestsController {

    @FXML private TableView<User> table;
    @FXML private TableColumn<User, Integer> colId;
    @FXML private TableColumn<User, String> colUsername, colFullName, colRole;
    @FXML private Label statusLabel;

    private final ObservableList<User> requests = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colId      .setCellValueFactory(new PropertyValueFactory<>("id"));
        colUsername.setCellValueFactory(new PropertyValueFactory<>("username"));
        colFullName.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        colRole    .setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getRole() != null ? c.getValue().getRole().getRoleName() : "—"));
        table.setItems(requests);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        loadRequests();
    }

    @FXML
    public void loadRequests() {
        new Thread(() -> {
            try {
                Response r = ClientContext.getInstance()
                        .send(new Request.Builder(Action.GET_PENDING_USERS).build());
                Platform.runLater(() -> {
                    if (r.isSuccess() && r.getData() instanceof List<?> l) {
                        requests.setAll((List<User>) l);
                        statusLabel.setText("Заявок: " + requests.size());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Ошибка: " + e.getMessage()));
            }
        }).start();
    }

    @FXML
    public void approve() {
        User sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) { statusLabel.setText("Выберите заявку"); return; }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Одобрить заявку «" + sel.getUsername() + "»?", ButtonType.YES, ButtonType.NO);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

        new Thread(() -> {
            try {
                Response r = ClientContext.getInstance()
                        .send(new Request.Builder(Action.APPROVE_USER)
                                .param("id", String.valueOf(sel.getId())).build());
                Platform.runLater(() -> {
                    if (r.isSuccess()) { loadRequests(); statusLabel.setText("Одобрено"); }
                    else new Alert(Alert.AlertType.ERROR, r.getMessage()).showAndWait();
                });
            } catch (Exception e) {
                Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait());
            }
        }).start();
    }

    @FXML
    public void reject() {
        User sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) { statusLabel.setText("Выберите заявку"); return; }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Отклонить заявку «" + sel.getUsername() + "»?", ButtonType.YES, ButtonType.NO);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

        new Thread(() -> {
            try {
                Response r = ClientContext.getInstance()
                        .send(new Request.Builder(Action.REJECT_USER)
                                .param("id", String.valueOf(sel.getId())).build());
                Platform.runLater(() -> {
                    if (r.isSuccess()) { loadRequests(); statusLabel.setText("Отклонено"); }
                    else new Alert(Alert.AlertType.ERROR, r.getMessage()).showAndWait();
                });
            } catch (Exception e) {
                Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait());
            }
        }).start();
    }
}