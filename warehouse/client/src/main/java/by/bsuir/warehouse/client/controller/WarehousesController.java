package by.bsuir.warehouse.client.controller;

import by.bsuir.warehouse.client.network.ClientContext;
import by.bsuir.warehouse.common.model.Role;
import by.bsuir.warehouse.common.model.Warehouse;
import by.bsuir.warehouse.common.protocol.Action;
import by.bsuir.warehouse.common.protocol.Request;
import by.bsuir.warehouse.common.protocol.Response;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class WarehousesController {

    @FXML private TableView<Warehouse>               warehousesTable;
    @FXML private TableColumn<Warehouse, Integer>    colId;
    @FXML private TableColumn<Warehouse, String>     colName;
    @FXML private TableColumn<Warehouse, String>     colAddress;
    @FXML private Label                              statusLabel;

    @FXML private Button addBtn, editBtn, deleteBtn;

    private final ObservableList<Warehouse> warehouses = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colId     .setCellValueFactory(new PropertyValueFactory<>("id"));
        colName   .setCellValueFactory(new PropertyValueFactory<>("name"));
        colAddress.setCellValueFactory(new PropertyValueFactory<>("address"));
        warehousesTable.setItems(warehouses);
        warehousesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        boolean isAdmin = ClientContext.getInstance().getCurrentUser() != null
                && Role.ADMIN.equals(ClientContext.getInstance().getCurrentUser().getRole().getRoleName());
        addBtn.setVisible(isAdmin);
        addBtn.setManaged(isAdmin);
        editBtn.setVisible(isAdmin);
        editBtn.setManaged(isAdmin);
        deleteBtn.setVisible(isAdmin);
        deleteBtn.setManaged(isAdmin);

        loadData();
    }

    @FXML
    public void loadData() {
        Thread t = new Thread(() -> {
            try {
                Response resp = ClientContext.getInstance()
                        .send(new Request.Builder(Action.GET_ALL_WAREHOUSES).build());
                Platform.runLater(() -> {
                    if (resp.isSuccess() && resp.getData() instanceof List<?> raw) {
                        warehouses.setAll((List<Warehouse>) raw);
                        statusLabel.setText("Складов: " + warehouses.size());
                    }
                });
            } catch (IOException e) {
                Platform.runLater(() -> statusLabel.setText("Ошибка: " + e.getMessage()));
            }
        });
        t.setDaemon(true);
        t.start();
    }

    @FXML
    public void handleAdd() {
        showDialog(null).ifPresent(w -> new Thread(() -> {
            try {
                Response r = ClientContext.getInstance()
                        .send(new Request.Builder(Action.CREATE_WAREHOUSE).payload(w).build());
                Platform.runLater(() -> { if (r.isSuccess()) loadData(); else showError(r.getMessage()); });
            } catch (IOException e) {
                Platform.runLater(() -> showError("Ошибка соединения: " + e.getMessage()));
            }
        }).start());
    }

    @FXML
    public void handleEdit() {
        Warehouse sel = warehousesTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        showDialog(sel).ifPresent(updated -> {
            updated.setId(sel.getId());
            new Thread(() -> {
                try {
                    Response r = ClientContext.getInstance()
                            .send(new Request.Builder(Action.UPDATE_WAREHOUSE).payload(updated).build());
                    Platform.runLater(() -> { if (r.isSuccess()) loadData(); else showError(r.getMessage()); });
                } catch (IOException e) {
                    Platform.runLater(() -> showError("Ошибка соединения: " + e.getMessage()));
                }
            }).start();
        });
    }

    @FXML
    public void handleDelete() {
        Warehouse sel = warehousesTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Удалить склад «" + sel.getName() + "»?", ButtonType.YES, ButtonType.NO);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;
        new Thread(() -> {
            try {
                Response r = ClientContext.getInstance()
                        .send(new Request.Builder(Action.DELETE_WAREHOUSE).param("id", sel.getId()).build());
                Platform.runLater(() -> { if (r.isSuccess()) loadData(); else showError(r.getMessage()); });
            } catch (IOException e) {
                Platform.runLater(() -> showError("Ошибка соединения: " + e.getMessage()));
            }
        }).start();
    }

    private Optional<Warehouse> showDialog(Warehouse existing) {
        Dialog<Warehouse> d = new Dialog<>();
        d.setTitle(existing == null ? "Добавить склад" : "Редактировать склад");
        ButtonType save = new ButtonType("Сохранить", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(16));
        TextField fName    = new TextField(existing != null ? existing.getName()    : "");
        TextField fAddress = new TextField(existing != null ? existing.getAddress() : "");
        grid.add(new Label("Название*:"), 0, 0); grid.add(fName,    1, 0);
        grid.add(new Label("Адрес:"),     0, 1); grid.add(fAddress, 1, 1);
        d.getDialogPane().setContent(grid);
        d.setResultConverter(btn -> {
            if (btn != save) return null;
            if (fName.getText().trim().isEmpty()) return null;
            Warehouse w = new Warehouse();
            w.setName(fName.getText().trim());
            w.setAddress(fAddress.getText().trim());
            return w;
        });
        return d.showAndWait();
    }

    private void showError(String m) {
        new Alert(Alert.AlertType.ERROR, m).showAndWait();
    }
}