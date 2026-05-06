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
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class IncomeController {

    @FXML private ComboBox<Warehouse> warehouseCombo;
    @FXML private ComboBox<Supplier>  supplierCombo;
    @FXML private TextField           commentField;
    @FXML private TableView<DocumentItem> itemsTable;
    @FXML private TableColumn<DocumentItem, String> colArticle, colProduct, colUnit, colQty, colPrice, colTotal;
    @FXML private Label totalLabel, statusLabel;

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
        new Thread(() -> {
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
        }).start();
    }

    @FXML
    public void addItem() {
        if (allProducts.isEmpty()) { statusLabel.setText("Справочник товаров не загружен. Подождите..."); return; }
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
        if (wh == null) {
            showAlert("Не выбран склад", "Пожалуйста, выберите склад назначения.");
            return;
        }
        Supplier supplier = supplierCombo.getValue();
        if (supplier == null) {
            showAlert("Не выбран поставщик", "Пожалуйста, выберите поставщика для оформления приходной накладной.");
            return;
        }
        if (items.isEmpty()) {
            showAlert("Пустая накладная", "Добавьте хотя бы одну позицию.");
            return;
        }

        Document doc = new Document(DocumentType.INCOME, "AUTO", null);
        doc.setWarehouseTo(wh);
        doc.setSupplier(supplier);
        doc.setComment(commentField.getText().trim());
        doc.setItems(new ArrayList<>(items));

        statusLabel.setText("Оформление...");
        new Thread(() -> {
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
        }).start();
    }

    private void updateTotal() {
        BigDecimal total = items.stream()
                .map(DocumentItem::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        totalLabel.setText("Итого: " + total.setScale(2, java.math.RoundingMode.HALF_UP) + " руб.");
    }

    /**
     * Улучшенный диалог добавления позиции с валидацией цены и красивым оформлением.
     */
    private Optional<DocumentItem> showItemDialog(DocumentItem existing) {
        Dialog<DocumentItem> dialog = new Dialog<>();
        dialog.setTitle("Добавить позицию");
        dialog.setHeaderText("Выберите товар и укажите количество");

        ButtonType addBtn = new ButtonType("Добавить", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));

        ComboBox<Product> productCombo = new ComboBox<>();
        productCombo.getItems().setAll(allProducts);
        productCombo.setPromptText("Выберите товар");
        productCombo.setPrefWidth(320);
        productCombo.setConverter(new javafx.util.StringConverter<>() {
            public String toString(Product p) { return p == null ? "" : "[" + p.getArticle() + "] " + p.getName(); }
            public Product fromString(String s) { return null; }
        });

        TextField qtyField   = new TextField("1");
        qtyField.setPrefWidth(120);
        TextField priceField = new TextField("");
        priceField.setPrefWidth(120);
        Label maxPriceLabel = new Label("Макс. цена закупки: —");
        maxPriceLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 12px;");

        productCombo.setOnAction(e -> {
            Product p = productCombo.getValue();
            if (p != null) {
                if (p.getPurchasePrice() != null) {
                    priceField.setText(p.getPurchasePrice().toPlainString());
                    maxPriceLabel.setText("Макс. цена закупки: " + p.getPurchasePrice() + " руб.");
                } else {
                    priceField.setText("");
                    maxPriceLabel.setText("Макс. цена закупки: не задана");
                }
            }
        });

        grid.add(new Label("Товар*:"),       0, 0);
        grid.add(productCombo, 1, 0);
        grid.add(new Label("Количество*:"),  0, 1);
        grid.add(qtyField,     1, 1);
        grid.add(new Label("Цена закупки:"), 0, 2);
        grid.add(priceField,   1, 2);
        grid.add(maxPriceLabel, 0, 3, 2, 1);

        // Стилизация лейблов
        grid.getChildren().stream()
                .filter(node -> node instanceof Label)
                .map(node -> (Label) node)
                .forEach(label -> label.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;"));

        dialog.getDialogPane().setContent(grid);
        Platform.runLater(productCombo::requestFocus);

        // Делаем кнопку "Добавить" недоступной, пока не выбран товар
        Button addButton = (Button) dialog.getDialogPane().lookupButton(addBtn);
        addButton.setDisable(true);
        addButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold;");

        productCombo.valueProperty().addListener((obs, old, val) -> {
            addButton.setDisable(val == null);
        });

        dialog.setResultConverter(btn -> {
            if (btn != addBtn) return null;
            Product p = productCombo.getValue();
            try {
                BigDecimal qty = new BigDecimal(qtyField.getText().trim());
                if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                    showAlert("Ошибка количества", "Количество должно быть больше 0");
                    return null;
                }
                String priceText = priceField.getText().trim();
                BigDecimal price = priceText.isEmpty() ? null : new BigDecimal(priceText);
                if (price != null && p.getPurchasePrice() != null &&
                        price.compareTo(p.getPurchasePrice()) > 0) {
                    showAlert("Ошибка цены",
                            "Цена закупки превышает максимальную закупочную цену товара (" + p.getPurchasePrice() + ")");
                    return null;
                }
                return new DocumentItem(p, qty, price);
            } catch (NumberFormatException e) {
                showAlert("Ошибка формата", "Некорректное число в поле количества или цены");
                return null;
            }
        });

        return dialog.showAndWait();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void showInfo(String msg) {
        new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK).showAndWait();
    }
}