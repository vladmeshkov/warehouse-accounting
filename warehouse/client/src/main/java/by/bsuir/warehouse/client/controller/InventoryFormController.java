package by.bsuir.warehouse.client.controller;

import by.bsuir.warehouse.client.network.ClientContext;
import by.bsuir.warehouse.common.model.*;
import by.bsuir.warehouse.common.protocol.Action;
import by.bsuir.warehouse.common.protocol.Request;
import by.bsuir.warehouse.common.protocol.Response;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;

import java.util.*;

public class InventoryFormController {

    public static class InventoryRow {
        public int productId;
        public String article, name, unit;
        public int accounting;
        public int actual;
        public int diff;

        public InventoryRow(Stock s) {
            productId  = s.getProductId();
            article    = s.getProductArticle();
            name       = s.getProductName();
            unit       = s.getProductUnit();
            accounting = s.getQuantity();
            actual     = s.getQuantity();
            diff       = 0;
        }
    }

    @FXML private ComboBox<Warehouse> warehouseCombo;
    @FXML private TableView<InventoryRow> inventoryTable;
    @FXML private TableColumn<InventoryRow, String> colArticle, colProduct, colUnit, colDiff;
    @FXML private TableColumn<InventoryRow, Integer> colAccounting, colActual;
    @FXML private Label statusLabel;

    private final ObservableList<InventoryRow> rows = FXCollections.observableArrayList();
    private final Map<Integer, TextField> actualFields = new HashMap<>();   // productId → поле ввода

    @FXML
    public void initialize() {
        colArticle   .setCellValueFactory(c -> new SimpleStringProperty(c.getValue().article));
        colProduct   .setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name));
        colUnit      .setCellValueFactory(c -> new SimpleStringProperty(c.getValue().unit));
        colAccounting.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().accounting).asObject());
        colDiff      .setCellValueFactory(c -> {
            int d = c.getValue().diff;
            return new SimpleStringProperty(d == 0 ? "—" : d > 0 ? "+" + d : String.valueOf(d));
        });

        colActual.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().actual).asObject());
        colActual.setCellFactory(col -> new TableCell<>() {
            private final TextField tf = new TextField();
            {
                tf.setOnAction(e -> commitEdit(parse(tf.getText())));
                tf.focusedProperty().addListener((o, ov, nv) -> { if (!nv) commitEdit(parse(tf.getText())); });
            }
            @Override protected void updateItem(Integer v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    tf.setText(v != null ? v.toString() : "0");
                    setGraphic(tf);
                    // Сохраняем поле для подсветки
                    InventoryRow row = getTableView().getItems().get(getIndex());
                    actualFields.put(row.productId, tf);
                }
            }
            @Override public void commitEdit(Integer v) {
                if (v != null && v < 0) {
                    // Пытались ввести отрицательное – блокируем
                    Alert alert = new Alert(Alert.AlertType.WARNING,
                            "Остаток не может быть отрицательным. Введите 0 или положительное число.",
                            ButtonType.OK);
                    alert.setHeaderText("Некорректное значение");
                    alert.showAndWait();
                    // Возвращаем прежнее значение
                    InventoryRow row = getTableView().getItems().get(getIndex());
                    tf.setText(String.valueOf(row.actual));
                    return;
                }
                super.commitEdit(v);
                InventoryRow row = getTableView().getItems().get(getIndex());
                row.actual = v;
                row.diff = v - row.accounting;
                inventoryTable.refresh();
            }
            private int parse(String s) {
                try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 0; }
            }
        });

        colActual.setEditable(true);
        inventoryTable.setEditable(true);
        inventoryTable.setItems(rows);

        new Thread(() -> {
            try {
                Response r = ClientContext.getInstance().send(
                        new Request.Builder(Action.GET_ALL_WAREHOUSES).build());
                Platform.runLater(() -> {
                    if (r.isSuccess() && r.getData() instanceof List<?> l)
                        warehouseCombo.getItems().setAll((List<Warehouse>) l);
                });
            } catch (Exception ignored) {}
        }).start();
    }

    @FXML
    public void loadStockForWarehouse() {
        Warehouse wh = warehouseCombo.getValue();
        if (wh == null) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION,
                    "Выберите склад для проведения инвентаризации", ButtonType.OK);
            alert.setHeaderText(null);
            alert.showAndWait();
            return;
        }
        new Thread(() -> {
            try {
                Response r = ClientContext.getInstance().send(
                        new Request.Builder(Action.GET_STOCK_BY_WAREHOUSE)
                                .param("warehouseId", wh.getId()).build());
                Platform.runLater(() -> {
                    rows.clear();
                    actualFields.clear();
                    if (r.isSuccess() && r.getData() instanceof List<?> l)
                        for (Object o : l) rows.add(new InventoryRow((Stock) o));
                    statusLabel.setText("Загружено: " + rows.size() + " позиций");
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Ошибка: " + e.getMessage()));
            }
        }).start();
    }

    @FXML
    public void handleSubmit() {
        if (warehouseCombo.getValue() == null) { statusLabel.setText("Выберите склад"); return; }
        if (rows.isEmpty()) { statusLabel.setText("Нет позиций"); return; }

        // Проверка на отрицательные значения и подсветка
        boolean hasNegative = false;
        for (InventoryRow row : rows) {
            if (row.actual < 0) {
                hasNegative = true;
                TextField tf = actualFields.get(row.productId);
                if (tf != null) {
                    tf.setStyle("-fx-border-color: red; -fx-border-width: 2px;");
                }
            }
        }

        if (hasNegative) {
            Alert alert = new Alert(Alert.AlertType.WARNING,
                    "Обнаружены отрицательные остатки.\nОни выделены красной рамкой.\n" +
                            "Скопируйте актуальное количество и повторите попытку.",
                    ButtonType.OK);
            alert.setHeaderText("Ошибка в данных инвентаризации");
            alert.showAndWait();
            return;
        }

        Map<Integer, Integer> actual = new HashMap<>();
        for (InventoryRow r : rows) actual.put(r.productId, r.actual);

        InventoryRequest reqPayload = new InventoryRequest(warehouseCombo.getValue(), actual);

        new Thread(() -> {
            try {
                Response r = ClientContext.getInstance().send(
                        new Request.Builder(Action.PROCESS_INVENTORY).payload(reqPayload).build());
                Platform.runLater(() -> {
                    if (r.isSuccess()) {
                        String msg = r.getMessage();
                        if (r.getData() != null) {
                            // Пытаемся извлечь детали
                            msg += "\n" + r.getData().toString();
                        }
                        new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK).showAndWait();
                        statusLabel.setText("");
                    } else {
                        statusLabel.setText("Ошибка: " + r.getMessage());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Ошибка: " + e.getMessage()));
            }
        }).start();
    }
}