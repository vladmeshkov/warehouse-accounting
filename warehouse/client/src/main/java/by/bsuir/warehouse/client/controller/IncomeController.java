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

public class IncomeController {

    @FXML private ComboBox<Warehouse>                     warehouseCombo;
    @FXML private ComboBox<Supplier>                      supplierCombo;
    @FXML private TextField                               commentField;
    @FXML private TableView<DocumentItem>                 itemsTable;
    @FXML private TableColumn<DocumentItem, String>       colArticle;
    @FXML private TableColumn<DocumentItem, String>       colProduct;
    @FXML private TableColumn<DocumentItem, String>       colUnit;
    @FXML private TableColumn<DocumentItem, String>       colQty;
    @FXML private TableColumn<DocumentItem, String>       colPrice;
    @FXML private TableColumn<DocumentItem, String>       colTotal;
    @FXML private Label                                   totalLabel;
    @FXML private Label                                   statusLabel;

    private final ObservableList<DocumentItem> items = FXCollections.observableArrayList();
    private List<Product> allProducts = new ArrayList<>();

    @FXML
    public void initialize() {
        setupColumns();
        itemsTable.setItems(items);
        itemsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        loadReferenceData();
    }

    private void setupColumns() {
        colArticle.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getProduct() != null
                        ? c.getValue().getProduct().getArticle() : ""));
        colProduct.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getProduct() != null
                        ? c.getValue().getProduct().getName() : ""));
        colUnit.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getProduct() != null
                        ? c.getValue().getProduct().getUnit() : ""));
        colQty.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getQuantity() != null
                        ? c.getValue().getQuantity().toPlainString() : "0"));
        colPrice.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getPrice() != null
                        ? c.getValue().getPrice().toPlainString() : "—"));
        colTotal.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getTotal().toPlainString()));
    }

    private void loadReferenceData() {
        Thread t = new Thread(() -> {
            try {
                ClientContext ctx = ClientContext.getInstance();
                Response wh  = ctx.send(new Request.Builder(Action.GET_ALL_WAREHOUSES).build());
                Response sup = ctx.send(new Request.Builder(Action.GET_ALL_SUPPLIERS).build());
                Response prod= ctx.send(new Request.Builder(Action.GET_ALL_PRODUCTS).build());

                Platform.runLater(() -> {
                    if (wh.isSuccess() && wh.getData() instanceof List<?> l)
                        warehouseCombo.getItems().setAll((List<Warehouse>) l);
                    if (sup.isSuccess() && sup.getData() instanceof List<?> l)
                        supplierCombo.getItems().setAll((List<Supplier>) l);
                    if (prod.isSuccess() && prod.getData() instanceof List<?> l)
                        allProducts = (List<Product>) l;
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Ошибка загрузки справочников: " + e.getMessage()));
            }
        });
        t.setDaemon(true);
        t.start();
    }

    @FXML
    public void addItem() {
        if (allProducts.isEmpty()) {
            statusLabel.setText("Справочник товаров не загружен. Подождите...");
            return;
        }
        showItemDialog(null).ifPresent(item -> {
            items.add(item);
            updateTotal();
        });
    }

    @FXML
    public void removeItem() {
        DocumentItem sel = itemsTable.getSelectionModel().getSelectedItem();
        if (sel != null) { items.remove(sel); updateTotal(); }
    }

    @FXML
    public void handleSubmit() {
        Warehouse wh = warehouseCombo.getValue();
        if (wh == null) { statusLabel.setText("Выберите склад"); return; }
        if (items.isEmpty()) { statusLabel.setText("Добавьте хотя бы одну позицию"); return; }

        Document doc = new Document(DocumentType.INCOME, "AUTO", null);
        doc.setWarehouseTo(wh);
        doc.setSupplier(supplierCombo.getValue());
        doc.setComment(commentField.getText().trim());
        doc.setItems(new ArrayList<>(items));

        statusLabel.setText("Оформление...");
        Thread t = new Thread(() -> {
            try {
                Response resp = ClientContext.getInstance()
                        .send(new Request.Builder(Action.PROCESS_INCOME).payload(doc).build());
                Platform.runLater(() -> {
                    if (resp.isSuccess()) {
                        showInfo(resp.getMessage());
                        items.clear();
                        updateTotal();
                        warehouseCombo.setValue(null);
                        supplierCombo.setValue(null);
                        commentField.clear();
                        statusLabel.setText("");
                    } else {
                        statusLabel.setText("Ошибка: " + resp.getMessage());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Ошибка: " + e.getMessage()));
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void updateTotal() {
        BigDecimal total = items.stream()
                .map(DocumentItem::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        totalLabel.setText("Итого: " + total.setScale(2, java.math.RoundingMode.HALF_UP) + " руб.");
    }

    private Optional<DocumentItem> showItemDialog(DocumentItem existing) {
        Dialog<DocumentItem> dialog = new Dialog<>();
        dialog.setTitle("Добавить позицию");
        dialog.setHeaderText("Выберите товар и укажите количество");

        ButtonType addBtn = new ButtonType("Добавить", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(16));

        ComboBox<Product> productCombo = new ComboBox<>();
        productCombo.getItems().setAll(allProducts);
        productCombo.setPromptText("Выберите товар");
        productCombo.setPrefWidth(300);
        productCombo.setConverter(new javafx.util.StringConverter<>() {
            public String toString(Product p) { return p == null ? "" : "[" + p.getArticle() + "] " + p.getName(); }
            public Product fromString(String s) { return null; }
        });

        TextField qtyField   = new TextField("1");
        TextField priceField = new TextField("");

        // Автозаполнение цены из карточки товара
        productCombo.setOnAction(e -> {
            Product p = productCombo.getValue();
            if (p != null && p.getPurchasePrice() != null)
                priceField.setText(p.getPurchasePrice().toPlainString());
        });

        grid.add(new Label("Товар*:"),       0, 0); grid.add(productCombo, 1, 0);
        grid.add(new Label("Количество*:"),  0, 1); grid.add(qtyField,     1, 1);
        grid.add(new Label("Цена за ед.:"),  0, 2); grid.add(priceField,   1, 2);

        Label err = new Label("");
        err.setStyle("-fx-text-fill:red;");
        grid.add(err, 0, 3, 2, 1);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn != addBtn) return null;
            Product p = productCombo.getValue();
            if (p == null) { err.setText("Выберите товар"); return null; }
            try {
                BigDecimal qty = new BigDecimal(qtyField.getText().trim());
                if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                    err.setText("Количество должно быть больше 0"); return null;
                }
                BigDecimal price = priceField.getText().trim().isEmpty()
                        ? null : new BigDecimal(priceField.getText().trim());
                DocumentItem item = new DocumentItem(p, qty, price);
                return item;
            } catch (NumberFormatException e) {
                err.setText("Неверный формат числа"); return null;
            }
        });
        return dialog.showAndWait();
    }

    private void showInfo(String msg) {
        new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK).showAndWait();
    }
}
