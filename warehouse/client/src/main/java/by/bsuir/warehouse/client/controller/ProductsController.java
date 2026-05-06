package by.bsuir.warehouse.client.controller;

import by.bsuir.warehouse.client.network.ClientContext;
import by.bsuir.warehouse.common.model.Product;
import by.bsuir.warehouse.common.model.Role;
import by.bsuir.warehouse.common.model.Stock;
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

    @FXML private TableView<Product> productsTable;
    @FXML private TableColumn<Product, Integer> colId;
    @FXML private TableColumn<Product, String> colArticle, colName, colCategory, colUnit;
    @FXML private TableColumn<Product, BigDecimal> colPurchase, colSelling;
    @FXML private TableColumn<Product, Integer> colMinStock;
    @FXML private TextField searchField;
    @FXML private Label statusLabel;

    @FXML private TextField pricePurchaseFrom, pricePurchaseTo;
    @FXML private TextField priceSellingFrom, priceSellingTo;

    @FXML private Button addBtn, editBtn, deleteBtn;

    private final ObservableList<Product> allProducts = FXCollections.observableArrayList();
    private FilteredList<Product> filteredList;

    @FXML
    public void initialize() {
        setupColumns();
        setupSearch();
        productsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        filteredList = new FilteredList<>(allProducts, p -> true);
        productsTable.setItems(filteredList);

        // Права на изменение: только ADMIN
        boolean isAdmin = ClientContext.getInstance().getCurrentUser() != null
                && Role.ADMIN.equals(ClientContext.getInstance().getCurrentUser().getRole().getRoleName());
        addBtn.setVisible(isAdmin);
        addBtn.setManaged(isAdmin);
        editBtn.setVisible(isAdmin);
        editBtn.setManaged(isAdmin);
        deleteBtn.setVisible(isAdmin);
        deleteBtn.setManaged(isAdmin);

        loadData();

        productsTable.setRowFactory(tv -> {
            TableRow<Product> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showProductDetailsDialog(row.getItem());
                }
            });
            return row;
        });
    }

    private void setupColumns() {
        colId       .setCellValueFactory(new PropertyValueFactory<>("id"));
        colArticle  .setCellValueFactory(new PropertyValueFactory<>("article"));
        colName     .setCellValueFactory(new PropertyValueFactory<>("name"));
        colCategory .setCellValueFactory(new PropertyValueFactory<>("category"));
        colUnit     .setCellValueFactory(new PropertyValueFactory<>("unit"));
        colPurchase .setCellValueFactory(new PropertyValueFactory<>("purchasePrice"));
        colSelling  .setCellValueFactory(new PropertyValueFactory<>("sellingPrice"));
        colMinStock .setCellValueFactory(new PropertyValueFactory<>("minStockLevel"));
    }

    private void setupSearch() {
        searchField.textProperty().addListener((obs, old, text) -> {
            filteredList.setPredicate(p -> {
                if (text == null || text.isEmpty()) return true;
                String lower = text.toLowerCase();
                return p.getName().toLowerCase().contains(lower)
                        || p.getArticle().toLowerCase().contains(lower)
                        || (p.getCategory() != null && p.getCategory().toLowerCase().contains(lower));
            });
        });
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
    public void applyPriceFilter() {
        BigDecimal fromPur   = parseBigDecimal(pricePurchaseFrom.getText());
        BigDecimal toPur     = parseBigDecimal(pricePurchaseTo.getText());
        BigDecimal fromSell  = parseBigDecimal(priceSellingFrom.getText());
        BigDecimal toSell    = parseBigDecimal(priceSellingTo.getText());

        filteredList.setPredicate(p -> {
            boolean textMatch = true;
            String text = searchField.getText();
            if (text != null && !text.isEmpty()) {
                String lower = text.toLowerCase();
                textMatch = p.getName().toLowerCase().contains(lower)
                        || p.getArticle().toLowerCase().contains(lower)
                        || (p.getCategory() != null && p.getCategory().toLowerCase().contains(lower));
            }

            boolean purchaseMatch = true;
            if (fromPur != null && (p.getPurchasePrice() == null || p.getPurchasePrice().compareTo(fromPur) < 0))
                purchaseMatch = false;
            if (toPur != null && (p.getPurchasePrice() == null || p.getPurchasePrice().compareTo(toPur) > 0))
                purchaseMatch = false;

            boolean sellingMatch = true;
            if (fromSell != null && (p.getSellingPrice() == null || p.getSellingPrice().compareTo(fromSell) < 0))
                sellingMatch = false;
            if (toSell != null && (p.getSellingPrice() == null || p.getSellingPrice().compareTo(toSell) > 0))
                sellingMatch = false;

            return textMatch && purchaseMatch && sellingMatch;
        });
        statusLabel.setText("Фильтр применён");
    }

    @FXML
    public void resetPriceFilter() {
        pricePurchaseFrom.clear();
        pricePurchaseTo.clear();
        priceSellingFrom.clear();
        priceSellingTo.clear();
        String currentText = searchField.getText();
        filteredList.setPredicate(p -> {
            if (currentText == null || currentText.isEmpty()) return true;
            String lower = currentText.toLowerCase();
            return p.getName().toLowerCase().contains(lower)
                    || p.getArticle().toLowerCase().contains(lower)
                    || (p.getCategory() != null && p.getCategory().toLowerCase().contains(lower));
        });
        statusLabel.setText("Фильтр сброшен");
    }

    private BigDecimal parseBigDecimal(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
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

        fArticle.setPromptText("AA-000");
        fPurchase.setPromptText("Например, 150.00");
        fSelling.setPromptText("Например, 200.00");

        grid.add(new Label("Артикул*:"),     0, 0); grid.add(fArticle,  1, 0);
        grid.add(new Label("Наименование*:"),0, 1); grid.add(fName,     1, 1);
        grid.add(new Label("Категория:"),    0, 2); grid.add(fCategory, 1, 2);
        grid.add(new Label("Ед. измерения*:"),0,3); grid.add(fUnit,     1, 3);
        grid.add(new Label("Макс. цена закупки*:"), 0, 4); grid.add(fPurchase, 1, 4);
        grid.add(new Label("Мин. цена продажи*:"),  0, 5); grid.add(fSelling,  1, 5);
        grid.add(new Label("Мин. остаток*:"),0, 6); grid.add(fMinStock, 1, 6);
        grid.add(new Label("Описание:"),     0, 7); grid.add(fDesc,     1, 7);

        dialog.getDialogPane().setContent(grid);
        Platform.runLater(fArticle::requestFocus);

        fArticle.textProperty().addListener((o,old,val) -> fArticle.setStyle(""));
        fName.textProperty().addListener((o,old,val) -> fName.setStyle(""));
        fUnit.textProperty().addListener((o,old,val) -> fUnit.setStyle(""));
        fMinStock.textProperty().addListener((o,old,val) -> fMinStock.setStyle(""));
        fPurchase.textProperty().addListener((o,old,val) -> fPurchase.setStyle(""));
        fSelling.textProperty().addListener((o,old,val) -> fSelling.setStyle(""));

        final javafx.scene.Node saveButtonNode = dialog.getDialogPane().lookupButton(saveBtn);
        saveButtonNode.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            fArticle.setStyle("");
            fName.setStyle("");
            fUnit.setStyle("");
            fMinStock.setStyle("");
            fPurchase.setStyle("");
            fSelling.setStyle("");

            StringBuilder errors = new StringBuilder();

            String article = fArticle.getText().trim();
            if (article.isEmpty()) {
                errors.append("• Артикул обязателен\n");
                fArticle.setStyle("-fx-border-color: red; -fx-border-width: 1.5px;");
            } else if (!article.matches("[A-Z]{2}-\\d{3}")) {
                errors.append("• Артикул должен иметь формат AA-000 (2 заглавные буквы, дефис, 3 цифры)\n");
                fArticle.setStyle("-fx-border-color: red; -fx-border-width: 1.5px;");
            }

            if (fName.getText().trim().isEmpty()) {
                errors.append("• Наименование обязательно\n");
                fName.setStyle("-fx-border-color: red; -fx-border-width: 1.5px;");
            }

            if (fUnit.getText().trim().isEmpty()) {
                errors.append("• Единица измерения обязательна\n");
                fUnit.setStyle("-fx-border-color: red; -fx-border-width: 1.5px;");
            }

            try {
                int minStock = Integer.parseInt(fMinStock.getText().trim());
                if (minStock < 0) {
                    errors.append("• Мин. остаток не может быть отрицательным\n");
                    fMinStock.setStyle("-fx-border-color: red; -fx-border-width: 1.5px;");
                }
            } catch (NumberFormatException e) {
                errors.append("• Мин. остаток должен быть целым числом\n");
                fMinStock.setStyle("-fx-border-color: red; -fx-border-width: 1.5px;");
            }

            String purText = fPurchase.getText().trim();
            if (purText.isEmpty()) {
                errors.append("• Макс. цена закупки обязательна\n");
                fPurchase.setStyle("-fx-border-color: red; -fx-border-width: 1.5px;");
            } else {
                try {
                    BigDecimal purPrice = new BigDecimal(purText);
                    if (purPrice.compareTo(BigDecimal.ZERO) < 0) {
                        errors.append("• Макс. цена закупки не может быть отрицательной\n");
                        fPurchase.setStyle("-fx-border-color: red; -fx-border-width: 1.5px;");
                    }
                } catch (NumberFormatException e) {
                    errors.append("• Макс. цена закупки должна быть числом\n");
                    fPurchase.setStyle("-fx-border-color: red; -fx-border-width: 1.5px;");
                }
            }

            String selText = fSelling.getText().trim();
            if (selText.isEmpty()) {
                errors.append("• Мин. цена продажи обязательна\n");
                fSelling.setStyle("-fx-border-color: red; -fx-border-width: 1.5px;");
            } else {
                try {
                    BigDecimal selPrice = new BigDecimal(selText);
                    if (selPrice.compareTo(BigDecimal.ZERO) < 0) {
                        errors.append("• Мин. цена продажи не может быть отрицательной\n");
                        fSelling.setStyle("-fx-border-color: red; -fx-border-width: 1.5px;");
                    }
                } catch (NumberFormatException e) {
                    errors.append("• Мин. цена продажи должна быть числом\n");
                    fSelling.setStyle("-fx-border-color: red; -fx-border-width: 1.5px;");
                }
            }

            if (errors.length() == 0) {
                BigDecimal purPrice = new BigDecimal(purText);
                BigDecimal selPrice = new BigDecimal(selText);
                if (purPrice.compareTo(BigDecimal.ZERO) >= 0 && selPrice.compareTo(BigDecimal.ZERO) >= 0) {
                    if (purPrice.compareTo(selPrice) > 0) {
                        errors.append("• Макс. цена закупки не может быть выше мин. цены продажи\n");
                        fPurchase.setStyle("-fx-border-color: red; -fx-border-width: 1.5px;");
                        fSelling.setStyle("-fx-border-color: red; -fx-border-width: 1.5px;");
                    }
                }
            }

            if (errors.length() > 0) {
                event.consume();
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Ошибка заполнения");
                alert.setHeaderText("Пожалуйста, исправьте следующие ошибки:");
                alert.setContentText(errors.toString().trim());
                alert.showAndWait();
            }
        });

        dialog.setResultConverter(btn -> {
            if (btn != saveBtn) return null;
            Product p = new Product();
            p.setArticle(fArticle.getText().trim());
            p.setName(fName.getText().trim());
            p.setCategory(fCategory.getText().trim());
            p.setUnit(fUnit.getText().trim());
            p.setPurchasePrice(new BigDecimal(fPurchase.getText().trim()));
            p.setSellingPrice(new BigDecimal(fSelling.getText().trim()));
            p.setMinStockLevel(Integer.parseInt(fMinStock.getText().trim()));
            p.setDescription(fDesc.getText().trim());
            return p;
        });

        return dialog.showAndWait();
    }

    private void showProductDetailsDialog(Product product) {
        Dialog<Void> detailsDialog = new Dialog<>();
        detailsDialog.setTitle("Карточка товара");
        detailsDialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        DialogPane dialogPane = detailsDialog.getDialogPane();
        dialogPane.setPrefSize(550, 500);
        dialogPane.setMinSize(450, 400);
        detailsDialog.setResizable(true);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        Label headerLabel = new Label("📦 " + product.getName());
        headerLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        grid.add(headerLabel, 0, 0, 2, 1);

        addDetailRow(grid, 1, "Артикул:", product.getArticle());
        addDetailRow(grid, 2, "Категория:", product.getCategory() != null ? product.getCategory() : "—");
        addDetailRow(grid, 3, "Ед. измерения:", product.getUnit() != null ? product.getUnit() : "—");
        addDetailRow(grid, 4, "Макс. цена закупки:", product.getPurchasePrice() != null ? product.getPurchasePrice().toString() + " руб." : "—");
        addDetailRow(grid, 5, "Мин. цена продажи:", product.getSellingPrice() != null ? product.getSellingPrice().toString() + " руб." : "—");
        addDetailRow(grid, 6, "Мин. остаток:", String.valueOf(product.getMinStockLevel()));
        addDetailRow(grid, 7, "Описание:", product.getDescription() != null && !product.getDescription().isEmpty() ? product.getDescription() : "—");

        Separator separator = new Separator();
        grid.add(separator, 0, 8, 2, 1);

        Label stockHeader = new Label("📊 Остатки по складам:");
        stockHeader.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        grid.add(stockHeader, 0, 9, 2, 1);

        Label loadingLabel = new Label("Загрузка...");
        grid.add(loadingLabel, 0, 10, 2, 1);

        new Thread(() -> {
            try {
                Response resp = ClientContext.getInstance()
                        .send(new Request.Builder(Action.GET_STOCK_ALL).build());
                Platform.runLater(() -> {
                    grid.getChildren().remove(loadingLabel);
                    if (resp.isSuccess() && resp.getData() instanceof List<?> raw) {
                        @SuppressWarnings("unchecked")
                        List<Stock> stocks = (List<Stock>) raw;
                        int row = 10;
                        for (Stock stock : stocks) {
                            if (stock.getProductId() == product.getId()) {
                                Label warehouseLabel = new Label(stock.getWarehouseName() + ":");
                                String status = stock.isBelowMinLevel() ? " ⚠ Критический!" : "";
                                Label qtyLabel = new Label(stock.getQuantity() + " " + (product.getUnit() != null ? product.getUnit() : "шт.") + status);
                                qtyLabel.setStyle(stock.isBelowMinLevel() ? "-fx-text-fill: #e74c3c; -fx-font-weight: bold;" : "-fx-text-fill: #2c3e50;");
                                grid.add(warehouseLabel, 0, row);
                                grid.add(qtyLabel, 1, row);
                                row++;
                            }
                        }
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    grid.getChildren().remove(loadingLabel);
                    grid.add(new Label("Не удалось загрузить остатки"), 0, 10, 2, 1);
                });
            }
        }).start();

        ButtonType editBtnType = new ButtonType("✏ Редактировать", ButtonBar.ButtonData.LEFT);
        detailsDialog.getDialogPane().getButtonTypes().add(editBtnType);

        detailsDialog.getDialogPane().setContent(grid);

        detailsDialog.setResultConverter(btn -> {
            if (btn == editBtnType) {
                showProductDialog(product).ifPresent(updated -> {
                    updated.setId(product.getId());
                    runAsync(() -> {
                        try {
                            Response resp = ClientContext.getInstance()
                                    .send(new Request.Builder(Action.UPDATE_PRODUCT)
                                            .payload(updated).build());
                            Platform.runLater(() -> {
                                if (resp.isSuccess()) {
                                    loadData();
                                    showInfo("Товар обновлён");
                                } else {
                                    showError(resp.getMessage());
                                }
                            });
                        } catch (java.io.IOException e) {
                            Platform.runLater(() -> showError("Ошибка соединения: " + e.getMessage()));
                        }
                    });
                });
            }
            return null;
        });

        detailsDialog.showAndWait();
    }

    private void addDetailRow(GridPane grid, int row, String label, String value) {
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        Label val = new Label(value);
        val.setStyle("-fx-font-size: 13px;");
        grid.add(lbl, 0, row);
        grid.add(val, 1, row);
    }

    private void runAsync(Runnable task) {
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private void showInfo(String msg) {
        new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK).showAndWait();
    }

    private void showError(String msg) {
        new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
    }
}