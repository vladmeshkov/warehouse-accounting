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

public class TransferController {

    @FXML private ComboBox<Warehouse> fromWarehouseCombo, toWarehouseCombo;
    @FXML private TableView<DocumentItem> itemsTable;
    @FXML private TableColumn<DocumentItem, String> colArticle, colProduct, colUnit, colStock, colQty;
    @FXML private Label statusLabel;

    private final ObservableList<DocumentItem> items = FXCollections.observableArrayList();
    private List<Product> allProducts = new ArrayList<>();
    private Map<Integer, Map<Integer, Integer>> stockMap = new HashMap<>();

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
        colStock.setCellValueFactory(c -> {
            DocumentItem item = c.getValue();
            if (fromWarehouseCombo.getValue() != null && item.getProduct() != null) {
                int whId = fromWarehouseCombo.getValue().getId();
                int prodId = item.getProduct().getId();
                Map<Integer, Integer> prodMap = stockMap.get(whId);
                if (prodMap != null && prodMap.containsKey(prodId)) {
                    return new SimpleStringProperty(String.valueOf(prodMap.get(prodId)));
                }
            }
            return new SimpleStringProperty("—");
        });

        itemsTable.setItems(items);
        itemsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        new Thread(() -> {
            try {
                ClientContext ctx = ClientContext.getInstance();
                Response whResp  = ctx.send(new Request.Builder(Action.GET_ALL_WAREHOUSES).build());
                Response prodResp = ctx.send(new Request.Builder(Action.GET_ALL_PRODUCTS).build());
                Response stockResp = ctx.send(new Request.Builder(Action.GET_STOCK_ALL).build());

                Platform.runLater(() -> {
                    if (whResp.isSuccess() && whResp.getData() instanceof List<?> l) {
                        fromWarehouseCombo.getItems().setAll((List<Warehouse>) l);
                        toWarehouseCombo.getItems().setAll((List<Warehouse>) l);
                    }
                    if (prodResp.isSuccess() && prodResp.getData() instanceof List<?> l)
                        allProducts = (List<Product>) l;

                    if (stockResp.isSuccess() && stockResp.getData() instanceof List<?> raw) {
                        List<Stock> stocks = (List<Stock>) raw;
                        for (Stock s : stocks) {
                            stockMap
                                    .computeIfAbsent(s.getWarehouseId(), k -> new HashMap<>())
                                    .put(s.getProductId(), s.getQuantity());
                        }
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Ошибка загрузки данных: " + e.getMessage()));
            }
        }).start();
    }

    @FXML
    public void addItem() {
        if (allProducts.isEmpty()) { statusLabel.setText("Нет товаров"); return; }
        Warehouse fromWh = fromWarehouseCombo.getValue();
        if (fromWh == null) { statusLabel.setText("Сначала выберите склад отправки"); return; }

        Dialog<DocumentItem> d = new Dialog<>();
        d.setTitle("Добавить позицию");
        d.setHeaderText("Выберите товар и укажите количество для перемещения");

        ButtonType addBtn = new ButtonType("Добавить", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(addBtn, ButtonType.CANCEL);

        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(12);
        g.setPadding(new Insets(20));

        ComboBox<Product> pc = new ComboBox<>(FXCollections.observableArrayList(allProducts));
        pc.setPromptText("Выберите товар");
        pc.setPrefWidth(320);
        pc.setConverter(new javafx.util.StringConverter<>() {
            public String toString(Product p) { return p == null ? "" : "[" + p.getArticle() + "] " + p.getName(); }
            public Product fromString(String s) { return null; }
        });

        TextField qf = new TextField("1");
        qf.setPrefWidth(120);
        Label stockInfoLabel = new Label("Текущий остаток: —");
        stockInfoLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 12px;");

        pc.setOnAction(e -> {
            Product sel = pc.getValue();
            if (sel != null) {
                int whId = fromWh.getId();
                Map<Integer, Integer> prodMap = stockMap.get(whId);
                int qty = (prodMap != null && prodMap.containsKey(sel.getId())) ? prodMap.get(sel.getId()) : 0;
                stockInfoLabel.setText("Текущий остаток: " + qty + " " + (sel.getUnit() != null ? sel.getUnit() : "шт."));
            } else {
                stockInfoLabel.setText("Текущий остаток: —");
            }
        });

        g.add(new Label("Товар*:"),     0, 0);
        g.add(pc, 1, 0);
        g.add(new Label("Количество*:"),0, 1);
        g.add(qf, 1, 1);
        g.add(stockInfoLabel, 0, 2, 2, 1);

        // Стили лейблов
        g.getChildren().stream()
                .filter(node -> node instanceof Label)
                .map(node -> (Label) node)
                .forEach(label -> label.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;"));

        d.getDialogPane().setContent(g);
        Platform.runLater(pc::requestFocus);

        // Кнопка "Добавить" неактивна, пока не выбран товар
        Button addButton = (Button) d.getDialogPane().lookupButton(addBtn);
        addButton.setDisable(true);
        addButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold;");
        pc.valueProperty().addListener((obs, old, val) -> addButton.setDisable(val == null));

        d.setResultConverter(btn -> {
            if (btn != addBtn || pc.getValue() == null) return null;
            Product sel = pc.getValue();
            int whId = fromWh.getId();
            Map<Integer, Integer> prodMap = stockMap.get(whId);
            int available = (prodMap != null && prodMap.containsKey(sel.getId())) ? prodMap.get(sel.getId()) : 0;

            try {
                BigDecimal qty = new BigDecimal(qf.getText().trim());
                if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                    showAlert("Ошибка количества", "Количество должно быть больше 0");
                    return null;
                }
                if (qty.intValue() > available) {
                    showAlert("Недостаточно товара",
                            "На складе доступно: " + available + " " + (sel.getUnit() != null ? sel.getUnit() : "шт."));
                    return null;
                }
                return new DocumentItem(sel, qty, null);
            } catch (NumberFormatException e) {
                showAlert("Ошибка формата", "Некорректное число в поле количества");
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
        Warehouse from = fromWarehouseCombo.getValue();
        Warehouse to = toWarehouseCombo.getValue();
        if (from == null || to == null) {
            showAlert("Не выбраны склады", "Укажите склад отправки и склад получения.");
            return;
        }
        if (items.isEmpty()) {
            showAlert("Пустая накладная", "Добавьте хотя бы одну позицию.");
            return;
        }

        // Финальная проверка остатков
        Map<Integer, Integer> prodMap = stockMap.get(from.getId());
        for (DocumentItem item : items) {
            int prodId = item.getProduct().getId();
            int needed = item.getQuantity().intValue();
            int available = (prodMap != null && prodMap.containsKey(prodId)) ? prodMap.get(prodId) : 0;
            if (needed > available) {
                showAlert("Недостаточно товара",
                        "Товар «" + item.getProduct().getName() + "» – доступно: " + available);
                return;
            }
        }

        Document doc = new Document(DocumentType.TRANSFER, "AUTO", null);
        doc.setWarehouseFrom(from);
        doc.setWarehouseTo(to);
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

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}