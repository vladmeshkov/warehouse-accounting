package by.bsuir.warehouse.client.controller;

import by.bsuir.warehouse.client.network.ClientContext;
import by.bsuir.warehouse.common.model.*;
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
import javafx.scene.layout.GridPane;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TransferController {

    @FXML private ComboBox<Warehouse> fromWarehouseCombo, toWarehouseCombo;
    @FXML private TableView<DocumentItem> itemsTable;
    @FXML private TableColumn<DocumentItem, String> colArticle, colProduct, colUnit, colQty;
    @FXML private Label statusLabel;

    private final ObservableList<DocumentItem> items = FXCollections.observableArrayList();
    private List<Product> allProducts = new ArrayList<>();

    @FXML
    public void initialize() {
        colArticle.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getProduct() != null ? c.getValue().getProduct().getArticle() : ""));
        colProduct.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getProduct() != null ? c.getValue().getProduct().getName() : ""));
        colUnit.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getProduct() != null ? c.getValue().getProduct().getUnit() : ""));
        colQty.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getQuantity() != null ? c.getValue().getQuantity().toPlainString() : ""));
        itemsTable.setItems(items);
        itemsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        new Thread(() -> {
            try {
                Response wh = ClientContext.getInstance()
                        .send(new Request.Builder(Action.GET_ALL_WAREHOUSES).build());
                Response pr = ClientContext.getInstance()
                        .send(new Request.Builder(Action.GET_ALL_PRODUCTS).build());
                Platform.runLater(() -> {
                    if (wh.isSuccess() && wh.getData() instanceof List<?> l) {
                        fromWarehouseCombo.getItems().setAll((List<Warehouse>) l);
                        toWarehouseCombo.getItems().setAll((List<Warehouse>) l);
                    }
                    if (pr.isSuccess() && pr.getData() instanceof List<?> l)
                        allProducts = (List<Product>) l;
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Ошибка: " + e.getMessage()));
            }
        }).start();
    }

    @FXML
    public void addItem() {
        if (allProducts.isEmpty()) return;
        Dialog<DocumentItem> d = new Dialog<>();
        d.setTitle("Добавить позицию");
        ButtonType add = new ButtonType("Добавить", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(add, ButtonType.CANCEL);
        GridPane g = new GridPane();
        g.setHgap(10); g.setVgap(10); g.setPadding(new Insets(16));
        ComboBox<Product> pc = new ComboBox<>(FXCollections.observableArrayList(allProducts));
        pc.setConverter(new javafx.util.StringConverter<>() {
            public String toString(Product p) {
                return p == null ? "" : "[" + p.getArticle() + "] " + p.getName();
            }
            public Product fromString(String s) { return null; }
        });
        TextField qf = new TextField("1");
        g.add(new Label("Товар*:"), 0, 0); g.add(pc, 1, 0);
        g.add(new Label("Кол-во*:"), 0, 1); g.add(qf, 1, 1);
        d.getDialogPane().setContent(g);
        d.setResultConverter(btn -> {
            if (btn != add || pc.getValue() == null) return null;
            try {
                return new DocumentItem(pc.getValue(), new BigDecimal(qf.getText().trim()), null);
            } catch (Exception e) {
                return null;
            }
        });
        d.showAndWait().ifPresent(items::add);
    }

    @FXML
    public void removeItem() {
        DocumentItem s = itemsTable.getSelectionModel().getSelectedItem();
        if (s != null) items.remove(s);
    }

    @FXML
    public void handleSubmit() {
        if (fromWarehouseCombo.getValue() == null || toWarehouseCombo.getValue() == null) {
            statusLabel.setText("Выберите склады");
            return;
        }
        if (items.isEmpty()) {
            statusLabel.setText("Добавьте позиции");
            return;
        }
        Document doc = new Document(DocumentType.TRANSFER, "AUTO", null);
        doc.setWarehouseFrom(fromWarehouseCombo.getValue());
        doc.setWarehouseTo(toWarehouseCombo.getValue());
        doc.setItems(new ArrayList<>(items));
        new Thread(() -> {
            try {
                Response r = ClientContext.getInstance().send(
                        new Request.Builder(Action.PROCESS_TRANSFER).payload(doc).build());
                Platform.runLater(() -> {
                    if (r.isSuccess()) {
                        new Alert(Alert.AlertType.INFORMATION, r.getMessage(), ButtonType.OK).showAndWait();
                        items.clear();
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