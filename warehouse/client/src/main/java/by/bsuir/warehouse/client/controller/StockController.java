package by.bsuir.warehouse.client.controller;

import by.bsuir.warehouse.client.network.ClientContext;
import by.bsuir.warehouse.common.model.Stock;
import by.bsuir.warehouse.common.model.Warehouse;
import by.bsuir.warehouse.common.protocol.Action;
import by.bsuir.warehouse.common.protocol.Request;
import by.bsuir.warehouse.common.protocol.Response;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.List;

public class StockController {

    @FXML private TableView<Stock>               stockTable;
    @FXML private TableColumn<Stock, String>     colWarehouse;
    @FXML private TableColumn<Stock, String>     colArticle;
    @FXML private TableColumn<Stock, String>     colProduct;
    @FXML private TableColumn<Stock, String>     colUnit;
    @FXML private TableColumn<Stock, Integer>    colQty;
    @FXML private TableColumn<Stock, Integer>    colMin;
    @FXML private TableColumn<Stock, String>     colStatus;
    @FXML private ComboBox<String>               warehouseFilter;
    @FXML private CheckBox                       showOnlyLow;
    @FXML private Label                          statusLabel;

    private final ObservableList<Stock> allStock = FXCollections.observableArrayList();
    private FilteredList<Stock> filteredStock;

    @FXML
    public void initialize() {
        setupColumns();
        filteredStock = new FilteredList<>(allStock, s -> true);
        stockTable.setItems(filteredStock);

        warehouseFilter.getItems().add("Все склады");
        warehouseFilter.getSelectionModel().selectFirst();
        warehouseFilter.setOnAction(e -> applyFilter());

        loadData();
    }

    private void setupColumns() {
        colWarehouse.setCellValueFactory(new PropertyValueFactory<>("warehouseName"));
        colArticle  .setCellValueFactory(new PropertyValueFactory<>("productArticle"));
        colProduct  .setCellValueFactory(new PropertyValueFactory<>("productName"));
        colUnit     .setCellValueFactory(new PropertyValueFactory<>("productUnit"));
        colQty      .setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colMin      .setCellValueFactory(new PropertyValueFactory<>("minStockLevel"));
        colStatus.setCellValueFactory(c -> {
            Stock s = c.getValue();
            String status = s.isBelowMinLevel() ? "⚠ Критический" : "✓ В норме";
            return new SimpleStringProperty(status);
        });

        // Подсветка критических строк
        stockTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(Stock item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("row-critical", "row-warning");
                if (!empty && item != null && item.isBelowMinLevel()) {
                    getStyleClass().add("row-warning");
                }
            }
        });
    }

    @FXML
    public void loadData() {
        statusLabel.setText("Загрузка...");
        Thread t = new Thread(() -> {
            try {
                Response resp = ClientContext.getInstance()
                        .send(new Request.Builder(Action.GET_STOCK_ALL).build());

                // Загружаем склады для фильтра
                Response whResp = ClientContext.getInstance()
                        .send(new Request.Builder(Action.GET_ALL_WAREHOUSES).build());

                Platform.runLater(() -> {
                    if (resp.isSuccess() && resp.getData() instanceof List<?> raw) {
                        allStock.setAll((List<Stock>) raw);
                        applyFilter();
                        statusLabel.setText("Загружено: " + allStock.size() + " позиций");
                    }
                    if (whResp.isSuccess() && whResp.getData() instanceof List<?> raw) {
                        warehouseFilter.getItems().clear();
                        warehouseFilter.getItems().add("Все склады");
                        for (Object o : raw) {
                            warehouseFilter.getItems().add(((Warehouse)o).getName());
                        }
                        warehouseFilter.getSelectionModel().selectFirst();
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Ошибка: " + e.getMessage()));
            }
        });
        t.setDaemon(true);
        t.start();
    }

    @FXML
    public void applyFilter() {
        String whName = warehouseFilter.getValue();
        boolean onlyLow = showOnlyLow != null && showOnlyLow.isSelected();

        filteredStock.setPredicate(s -> {
            boolean whMatch = whName == null || "Все склады".equals(whName)
                    || whName.equals(s.getWarehouseName());
            boolean lowMatch = !onlyLow || s.isBelowMinLevel();
            return whMatch && lowMatch;
        });
        statusLabel.setText("Отображено: " + filteredStock.size() + " из " + allStock.size());
    }
}
