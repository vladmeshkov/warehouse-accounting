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
import java.util.*;

public class OutcomeController {

    @FXML private ComboBox<Warehouse> warehouseCombo;
    @FXML private ComboBox<Customer>  customerCombo;
    @FXML private TextField           commentField;
    @FXML private TableView<DocumentItem> itemsTable;
    @FXML private TableColumn<DocumentItem, String> colArticle, colProduct, colUnit, colStock, colQty, colPrice, colTotal;
    @FXML private Label totalLabel, statusLabel;

    private final ObservableList<DocumentItem> items = FXCollections.observableArrayList();
    private List<Product> allProducts = new ArrayList<>();
    // Хранилище остатков: склад → (товар → количество)
    private Map<Integer, Map<Integer, Integer>> stockMap = new HashMap<>();

    @FXML
    public void initialize() {
        setupColumns();
        itemsTable.setItems(items);
        itemsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        loadReferenceData();
    }

    private void setupColumns() {
        colArticle.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getProduct() != null ? c.getValue().getProduct().getArticle() : ""));
        colProduct.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getProduct() != null ? c.getValue().getProduct().getName() : ""));
        colUnit.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getProduct() != null ? c.getValue().getProduct().getUnit() : ""));
        colStock.setCellValueFactory(c -> {
            DocumentItem item = c.getValue();
            if (warehouseCombo.getValue() != null && item.getProduct() != null) {
                int whId = warehouseCombo.getValue().getId();
                int prodId = item.getProduct().getId();
                Map<Integer, Integer> prodMap = stockMap.get(whId);
                if (prodMap != null && prodMap.containsKey(prodId)) {
                    return new SimpleStringProperty(String.valueOf(prodMap.get(prodId)));
                }
            }
            return new SimpleStringProperty("—");
        });
        colQty.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getQuantity() != null ? c.getValue().getQuantity().toPlainString() : ""));
        colPrice.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getPrice() != null ? c.getValue().getPrice().toPlainString() : "—"));
        colTotal.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTotal().toPlainString()));
    }

    private void loadReferenceData() {
        new Thread(() -> {
            try {
                ClientContext ctx = ClientContext.getInstance();
                Response wh   = ctx.send(new Request.Builder(Action.GET_ALL_WAREHOUSES).build());
                Response cust = ctx.send(new Request.Builder(Action.GET_ALL_CUSTOMERS).build());
                Response prod = ctx.send(new Request.Builder(Action.GET_ALL_PRODUCTS).build());
                Response stockResp = ctx.send(new Request.Builder(Action.GET_STOCK_ALL).build());

                Platform.runLater(() -> {
                    if (wh.isSuccess()   && wh.getData()   instanceof List<?> l) warehouseCombo.getItems().setAll((List<Warehouse>)l);
                    if (cust.isSuccess() && cust.getData() instanceof List<?> l) customerCombo.getItems().setAll((List<Customer>)l);
                    if (prod.isSuccess() && prod.getData() instanceof List<?> l) allProducts = (List<Product>)l;

                    if (stockResp.isSuccess() && stockResp.getData() instanceof List<?> raw) {
                        List<Stock> stocks = (List<Stock>) raw;
                        for (Stock s : stocks) {
                            stockMap
                                    .computeIfAbsent(s.getWarehouseId(), k -> new HashMap<>())
                                    .put(s.getProductId(), s.getQuantity());
                        }
                    }
                });
            } catch (Exception e) { Platform.runLater(() -> statusLabel.setText("Ошибка: " + e.getMessage())); }
        }).start();
    }

    @FXML
    public void addItem() {
        Warehouse wh = warehouseCombo.getValue();
        if (wh == null) { statusLabel.setText("Сначала выберите склад отгрузки"); return; }
        if (allProducts.isEmpty()) { statusLabel.setText("Справочник товаров не загружен"); return; }

        showItemDialog(wh).ifPresent(item -> { items.add(item); updateTotal(); });
    }

    @FXML
    public void removeItem() {
        DocumentItem s = itemsTable.getSelectionModel().getSelectedItem();
        if (s != null) { items.remove(s); updateTotal(); }
    }

    @FXML
    public void handleSubmit() {
        Warehouse wh = warehouseCombo.getValue();
        if (wh == null) {
            showAlert("Не выбран склад", "Пожалуйста, выберите склад отгрузки.");
            return;
        }
        Customer customer = customerCombo.getValue();
        if (customer == null) {
            showAlert("Не выбран покупатель", "Пожалуйста, выберите покупателя для оформления расходной накладной.");
            return;
        }
        if (items.isEmpty()) {
            showAlert("Пустая накладная", "Добавьте хотя бы одну позицию.");
            return;
        }

        // Проверка остатков перед отправкой
        Map<Integer, Integer> prodMap = stockMap.get(wh.getId());
        for (DocumentItem item : items) {
            int prodId = item.getProduct().getId();
            int needed = item.getQuantity().intValue();
            int available = (prodMap != null && prodMap.containsKey(prodId)) ? prodMap.get(prodId) : 0;
            if (needed > available) {
                showAlert("Недостаточно товара",
                        "Товар «" + item.getProduct().getName() + "» – на складе доступно: " + available);
                return;
            }
        }

        Document doc = new Document(DocumentType.OUTCOME, "AUTO", null);
        doc.setWarehouseFrom(wh);
        doc.setCustomer(customer);
        doc.setComment(commentField.getText());
        doc.setItems(new ArrayList<>(items));
        statusLabel.setText("Оформление...");
        new Thread(() -> {
            try {
                Response r = ClientContext.getInstance()
                        .send(new Request.Builder(Action.PROCESS_OUTCOME).payload(doc).build());
                Platform.runLater(() -> {
                    if (r.isSuccess()) {
                        new Alert(Alert.AlertType.INFORMATION, r.getMessage(), ButtonType.OK).showAndWait();
                        items.clear(); updateTotal(); warehouseCombo.setValue(null);
                        customerCombo.setValue(null); commentField.clear(); statusLabel.setText("");
                    } else statusLabel.setText("Ошибка: " + r.getMessage());
                });
            } catch (Exception e) { Platform.runLater(() -> statusLabel.setText("Ошибка: " + e.getMessage())); }
        }).start();
    }

    private void updateTotal() {
        BigDecimal t = items.stream().map(DocumentItem::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        totalLabel.setText("Итого: " + t.setScale(2, java.math.RoundingMode.HALF_UP) + " руб.");
    }

    private Optional<DocumentItem> showItemDialog(Warehouse warehouse) {
        Dialog<DocumentItem> d = new Dialog<>();
        d.setTitle("Добавить позицию");
        d.setHeaderText("Выберите товар и укажите количество");

        ButtonType addBtn = new ButtonType("Добавить", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(addBtn, ButtonType.CANCEL);

        GridPane g = new GridPane(); g.setHgap(12); g.setVgap(12); g.setPadding(new Insets(20));

        ComboBox<Product> pc = new ComboBox<>(FXCollections.observableArrayList(allProducts));
        pc.setPromptText("Выберите товар"); pc.setPrefWidth(320);
        pc.setConverter(new javafx.util.StringConverter<>() {
            public String toString(Product p) { return p == null ? "" : "["+p.getArticle()+"] "+p.getName(); }
            public Product fromString(String s) { return null; }
        });

        TextField qf = new TextField("1"), pf = new TextField();
        Label stockInfoLabel = new Label("Текущий остаток: —");
        stockInfoLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 12px;");
        Label minPriceLabel = new Label("Мин. цена продажи: —");
        minPriceLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 12px;");

        pc.setOnAction(e -> {
            Product p = pc.getValue();
            if (p != null) {
                // Остаток
                Map<Integer, Integer> prodMap = stockMap.get(warehouse.getId());
                int stockQty = (prodMap != null && prodMap.containsKey(p.getId())) ? prodMap.get(p.getId()) : 0;
                stockInfoLabel.setText("Текущий остаток: " + stockQty + " " + (p.getUnit() != null ? p.getUnit() : "шт."));

                // Цена
                if (p.getSellingPrice() != null) {
                    pf.setText(p.getSellingPrice().toPlainString());
                    minPriceLabel.setText("Мин. цена продажи: " + p.getSellingPrice() + " руб.");
                } else {
                    pf.setText("");
                    minPriceLabel.setText("Мин. цена продажи: не задана");
                }
            } else {
                stockInfoLabel.setText("Текущий остаток: —");
                minPriceLabel.setText("Мин. цена продажи: —");
            }
        });

        g.add(new Label("Товар*:"),     0, 0); g.add(pc, 1, 0);
        g.add(new Label("Количество*:"),0, 1); g.add(qf, 1, 1);
        g.add(new Label("Цена продажи:"),0,2); g.add(pf, 1, 2);
        g.add(stockInfoLabel, 0, 3, 2, 1);
        g.add(minPriceLabel, 0, 4, 2, 1);

        // Стили лейблов
        g.getChildren().stream()
                .filter(node -> node instanceof Label)
                .map(node -> (Label) node)
                .forEach(label -> label.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;"));

        d.getDialogPane().setContent(g);

        // Кнопка "Добавить" неактивна до выбора товара
        Button addButton = (Button) d.getDialogPane().lookupButton(addBtn);
        addButton.setDisable(true);
        addButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold;");
        pc.valueProperty().addListener((obs, old, val) -> addButton.setDisable(val == null));

        d.setResultConverter(btn -> {
            if (btn != addBtn || pc.getValue() == null) return null;
            Product p = pc.getValue();
            Map<Integer, Integer> prodMap = stockMap.get(warehouse.getId());
            int available = (prodMap != null && prodMap.containsKey(p.getId())) ? prodMap.get(p.getId()) : 0;

            try {
                BigDecimal qty = new BigDecimal(qf.getText().trim());
                if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                    showAlert("Ошибка количества", "Количество должно быть больше 0");
                    return null;
                }
                if (qty.intValue() > available) {
                    showAlert("Недостаточно товара",
                            "На складе доступно: " + available + " " + (p.getUnit() != null ? p.getUnit() : "шт."));
                    return null;
                }

                String priceText = pf.getText().trim();
                BigDecimal price = priceText.isEmpty() ? null : new BigDecimal(priceText);
                if (price != null && p.getSellingPrice() != null &&
                        price.compareTo(p.getSellingPrice()) < 0) {
                    showAlert("Ошибка цены",
                            "Цена продажи ниже минимальной цены товара (" + p.getSellingPrice() + ")");
                    return null;
                }

                return new DocumentItem(p, qty, price);
            } catch (NumberFormatException ex) {
                showAlert("Ошибка формата", "Некорректное число в поле количества или цены");
                return null;
            }
        });
        return d.showAndWait();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}