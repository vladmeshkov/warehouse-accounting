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
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Контроллер расходной накладной — зеркало IncomeController для отгрузки.
 */
public class OutcomeController {

    @FXML private ComboBox<Warehouse>       warehouseCombo;
    @FXML private ComboBox<Customer>        customerCombo;
    @FXML private TextField                 commentField;
    @FXML private TableView<DocumentItem>   itemsTable;
    @FXML private TableColumn<DocumentItem,String> colArticle, colProduct, colUnit, colQty, colPrice, colTotal;
    @FXML private Label totalLabel, statusLabel;

    private final ObservableList<DocumentItem> items = FXCollections.observableArrayList();
    private List<Product> allProducts = new ArrayList<>();

    @FXML public void initialize() {
        setupColumns(); itemsTable.setItems(items); loadReferenceData();
    }

    private void setupColumns() {
        colArticle.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getProduct() != null ? c.getValue().getProduct().getArticle() : ""));
        colProduct.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getProduct() != null ? c.getValue().getProduct().getName() : ""));
        colUnit.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getProduct() != null ? c.getValue().getProduct().getUnit() : ""));
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
                Platform.runLater(() -> {
                    if (wh.isSuccess()   && wh.getData()   instanceof List<?> l) warehouseCombo.getItems().setAll((List<Warehouse>)l);
                    if (cust.isSuccess() && cust.getData() instanceof List<?> l) customerCombo.getItems().setAll((List<Customer>)l);
                    if (prod.isSuccess() && prod.getData() instanceof List<?> l) allProducts = (List<Product>)l;
                });
            } catch (Exception e) { Platform.runLater(() -> statusLabel.setText("Ошибка: " + e.getMessage())); }
        }).start();
    }

    @FXML public void addItem() {
        showItemDialog().ifPresent(item -> { items.add(item); updateTotal(); });
    }
    @FXML public void removeItem() {
        DocumentItem s = itemsTable.getSelectionModel().getSelectedItem();
        if (s != null) { items.remove(s); updateTotal(); }
    }

    @FXML public void handleSubmit() {
        if (warehouseCombo.getValue() == null) { statusLabel.setText("Выберите склад"); return; }
        if (items.isEmpty()) { statusLabel.setText("Добавьте позиции"); return; }
        Document doc = new Document(DocumentType.OUTCOME, "AUTO", null);
        doc.setWarehouseFrom(warehouseCombo.getValue());
        doc.setCustomer(customerCombo.getValue());
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

    private Optional<DocumentItem> showItemDialog() {
        Dialog<DocumentItem> d = new Dialog<>();
        d.setTitle("Добавить позицию");
        ButtonType add = new ButtonType("Добавить", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(add, ButtonType.CANCEL);
        GridPane g = new GridPane(); g.setHgap(10); g.setVgap(10); g.setPadding(new Insets(16));
        ComboBox<Product> pc = new ComboBox<>(FXCollections.observableArrayList(allProducts));
        pc.setPromptText("Выберите товар"); pc.setPrefWidth(300);
        pc.setConverter(new javafx.util.StringConverter<>() {
            public String toString(Product p) { return p == null ? "" : "["+p.getArticle()+"] "+p.getName(); }
            public Product fromString(String s) { return null; }
        });
        TextField qf = new TextField("1"), pf = new TextField();
        pc.setOnAction(e -> { if (pc.getValue() != null && pc.getValue().getSellingPrice() != null)
            pf.setText(pc.getValue().getSellingPrice().toPlainString()); });
        g.add(new Label("Товар*:"),0,0); g.add(pc,1,0);
        g.add(new Label("Кол-во*:"),0,1); g.add(qf,1,1);
        g.add(new Label("Цена:"),0,2); g.add(pf,1,2);
        d.getDialogPane().setContent(g);
        d.setResultConverter(btn -> {
            if (btn != add || pc.getValue() == null) return null;
            try {
                BigDecimal qty = new BigDecimal(qf.getText().trim());
                BigDecimal price = pf.getText().trim().isEmpty() ? null : new BigDecimal(pf.getText().trim());
                return new DocumentItem(pc.getValue(), qty, price);
            } catch (NumberFormatException ex) { return null; }
        });
        return d.showAndWait();
    }
}
