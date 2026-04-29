package by.bsuir.warehouse.client.controller;

import by.bsuir.warehouse.client.network.ClientContext;
import by.bsuir.warehouse.common.model.Product;
import by.bsuir.warehouse.common.protocol.Action;
import by.bsuir.warehouse.common.protocol.Request;
import by.bsuir.warehouse.common.protocol.Response;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public class ProductsController {

    @FXML private TableView<Product>                    productsTable;
    @FXML private TableColumn<Product, Integer>         colId;
    @FXML private TableColumn<Product, String>          colArticle;
    @FXML private TableColumn<Product, String>          colName;
    @FXML private TableColumn<Product, String>          colCategory;
    @FXML private TableColumn<Product, String>          colUnit;
    @FXML private TableColumn<Product, BigDecimal>      colPurchase;
    @FXML private TableColumn<Product, BigDecimal>      colSelling;
    @FXML private TableColumn<Product, Integer>         colMinStock;
    @FXML private TextField                             searchField;
    @FXML private Label                                 statusLabel;

    private final ObservableList<Product> allProducts = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupColumns();
        setupSearch();
        loadData();
    }

    private void setupColumns() {
        colId      .setCellValueFactory(new PropertyValueFactory<>("id"));
        colArticle .setCellValueFactory(new PropertyValueFactory<>("article"));
        colName    .setCellValueFactory(new PropertyValueFactory<>("name"));
        colCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        colUnit    .setCellValueFactory(new PropertyValueFactory<>("unit"));
        colPurchase.setCellValueFactory(new PropertyValueFactory<>("purchasePrice"));
        colSelling .setCellValueFactory(new PropertyValueFactory<>("sellingPrice"));
        colMinStock.setCellValueFactory(new PropertyValueFactory<>("minStockLevel"));
    }

    private void setupSearch() {
        FilteredList<Product> filtered = new FilteredList<>(allProducts, p -> true);
        searchField.textProperty().addListener((obs, old, text) -> {
            filtered.setPredicate(p -> {
                if (text == null || text.isEmpty()) return true;
                String lower = text.toLowerCase();
                return p.getName().toLowerCase().contains(lower)
                        || p.getArticle().toLowerCase().contains(lower)
                        || (p.getCategory() != null && p.getCategory().toLowerCase().contains(lower));
            });
        });
        productsTable.setItems(filtered);
    }

    @FXML
    public void loadData() {
        statusLabel.setText("Загрузка...");
        Thread t = new Thread(() -> {
            try {
                Response resp = ClientContext.getInstance()
                        .send(new Request.Builder(Action.GET_ALL_PRODUCTS).build());
                Platform.runLater(() -> {
                    if (resp.isSuccess() && resp.getData() instanceof List<?> raw) {
                        allProducts.setAll((List<Product>) raw);
                        statusLabel.setText("Загружено: " + allProducts.size() + " позиций");
                    } else {
                        statusLabel.setText("Ошибка: " + resp.getMessage());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Ошибка соединения: " + e.getMessage()));
            }
        });
        t.setDaemon(true);
        t.start();
    }

    @FXML
    public void handleAdd() {
        showProductDialog(null).ifPresent(product -> {
            runAsync(() -> {
                try {
                    Response resp = ClientContext.getInstance()
                            .send(new Request.Builder(Action.CREATE_PRODUCT)
                                    .payload(product).build());
                    Platform.runLater(() -> {
                        if (resp.isSuccess()) { loadData(); showInfo("Товар добавлен"); }
                        else showError(resp.getMessage());
                    });
                } catch (java.io.IOException e) {
                    Platform.runLater(() -> showError("Ошибка соединения: " + e.getMessage()));
                }
            });
        });
    }

    @FXML
    public void handleEdit() {
        Product selected = productsTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showInfo("Выберите товар для редактирования"); return; }

        showProductDialog(selected).ifPresent(updated -> {
            updated.setId(selected.getId());
            runAsync(() -> {
                try {
                    Response resp = ClientContext.getInstance()
                            .send(new Request.Builder(Action.UPDATE_PRODUCT)
                                    .payload(updated).build());
                    Platform.runLater(() -> {
                        if (resp.isSuccess()) { loadData(); showInfo("Товар обновлён"); }
                        else showError(resp.getMessage());
                    });
                } catch (java.io.IOException e) {
                    Platform.runLater(() -> showError("Ошибка соединения: " + e.getMessage()));
                }
            });
        });
    }

    @FXML
    public void handleDelete() {
        Product selected = productsTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showInfo("Выберите товар для удаления"); return; }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Удалить товар «" + selected.getName() + "»?",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Подтверждение удаления");
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

        runAsync(() -> {
            try {
                Response resp = ClientContext.getInstance()
                        .send(new Request.Builder(Action.DELETE_PRODUCT)
                                .param("id", selected.getId()).build());
                Platform.runLater(() -> {
                    if (resp.isSuccess()) { loadData(); showInfo("Товар удалён"); }
                    else showError(resp.getMessage());
                });
            } catch (java.io.IOException e) {
                Platform.runLater(() -> showError("Ошибка соединения: " + e.getMessage()));
            }
        });
    }

    // ── Диалог добавления/редактирования ─────────────────────────────────

    private Optional<Product> showProductDialog(Product existing) {
        Dialog<Product> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Добавить товар" : "Редактировать товар");
        dialog.setHeaderText(existing == null ? "Новая позиция номенклатуры" : "Изменение товара");

        ButtonType saveBtn = new ButtonType("Сохранить", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField fArticle   = new TextField(existing != null ? existing.getArticle()   : "");
        TextField fName      = new TextField(existing != null ? existing.getName()      : "");
        TextField fCategory  = new TextField(existing != null ? existing.getCategory()  : "");
        TextField fUnit      = new TextField(existing != null ? existing.getUnit()      : "шт.");
        TextField fPurchase  = new TextField(existing != null && existing.getPurchasePrice() != null
                ? existing.getPurchasePrice().toPlainString() : "");
        TextField fSelling   = new TextField(existing != null && existing.getSellingPrice() != null
                ? existing.getSellingPrice().toPlainString() : "");
        TextField fMinStock  = new TextField(existing != null
                ? String.valueOf(existing.getMinStockLevel()) : "0");
        TextArea  fDesc      = new TextArea(existing != null ? existing.getDescription() : "");
        fDesc.setPrefRowCount(2);

        grid.add(new Label("Артикул*:"),     0, 0); grid.add(fArticle,  1, 0);
        grid.add(new Label("Наименование*:"),0, 1); grid.add(fName,     1, 1);
        grid.add(new Label("Категория:"),    0, 2); grid.add(fCategory, 1, 2);
        grid.add(new Label("Ед. измерения*:"),0,3); grid.add(fUnit,     1, 3);
        grid.add(new Label("Цена закупки:"), 0, 4); grid.add(fPurchase, 1, 4);
        grid.add(new Label("Цена продажи:"), 0, 5); grid.add(fSelling,  1, 5);
        grid.add(new Label("Мин. остаток*:"),0, 6); grid.add(fMinStock, 1, 6);
        grid.add(new Label("Описание:"),     0, 7); grid.add(fDesc,     1, 7);

        dialog.getDialogPane().setContent(grid);
        Platform.runLater(fArticle::requestFocus);

        Label errLabel = new Label("");
        errLabel.setStyle("-fx-text-fill: red;");
        grid.add(errLabel, 0, 8, 2, 1);

        // Валидация перед сохранением
        javafx.scene.Node saveButton = dialog.getDialogPane().lookupButton(saveBtn);
        dialog.setResultConverter(btn -> {
            if (btn != saveBtn) return null;
            // Проверка обязательных полей
            if (fArticle.getText().trim().isEmpty() || fName.getText().trim().isEmpty()
                    || fUnit.getText().trim().isEmpty()) {
                errLabel.setText("Заполните обязательные поля (*)");
                return null;
            }
            Product p = new Product();
            p.setArticle(fArticle.getText().trim());
            p.setName(fName.getText().trim());
            p.setCategory(fCategory.getText().trim());
            p.setUnit(fUnit.getText().trim());
            try {
                if (!fPurchase.getText().isEmpty())
                    p.setPurchasePrice(new BigDecimal(fPurchase.getText().trim()));
                if (!fSelling.getText().isEmpty())
                    p.setSellingPrice(new BigDecimal(fSelling.getText().trim()));
                p.setMinStockLevel(Integer.parseInt(fMinStock.getText().trim()));
            } catch (NumberFormatException e) {
                errLabel.setText("Неверный формат числа");
                return null;
            }
            p.setDescription(fDesc.getText().trim());
            return p;
        });

        return dialog.showAndWait();
    }

    private void runAsync(Runnable task) {
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private void showInfo(String msg)  {
        new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK).showAndWait();
    }
    private void showError(String msg) {
        new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
    }
}