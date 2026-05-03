package by.bsuir.warehouse.client.controller;

import by.bsuir.warehouse.client.network.ClientContext;
import by.bsuir.warehouse.common.model.DeficitForecast;
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

import java.time.format.DateTimeFormatter;
import java.util.List;

public class ForecastController {

    @FXML private TableView<DeficitForecast>                 forecastTable;
    @FXML private TableColumn<DeficitForecast, String>       colWarehouse;
    @FXML private TableColumn<DeficitForecast, String>       colArticle;
    @FXML private TableColumn<DeficitForecast, String>       colProduct;
    @FXML private TableColumn<DeficitForecast, Integer>      colStock;
    @FXML private TableColumn<DeficitForecast, Integer>      colMin;
    @FXML private TableColumn<DeficitForecast, String>       colConsumption;
    @FXML private TableColumn<DeficitForecast, Integer>      colDays;
    @FXML private TableColumn<DeficitForecast, String>       colDeficitDate;
    @FXML private TableColumn<DeficitForecast, String>       colCalculated;
    @FXML private ComboBox<String>                           warehouseFilter;
    @FXML private Label                                      statusLabel;

    private final ObservableList<DeficitForecast> allForecasts = FXCollections.observableArrayList();
    private FilteredList<DeficitForecast> filtered;

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    @FXML
    public void initialize() {
        setupColumns();
        filtered = new FilteredList<>(allForecasts, f -> true);
        forecastTable.setItems(filtered);
        forecastTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        loadWarehouses();
        loadData();
    }

    private void setupColumns() {
        colWarehouse  .setCellValueFactory(new PropertyValueFactory<>("warehouseName"));
        colArticle    .setCellValueFactory(new PropertyValueFactory<>("productArticle"));
        colProduct    .setCellValueFactory(new PropertyValueFactory<>("productName"));
        colStock      .setCellValueFactory(new PropertyValueFactory<>("currentStock"));
        colMin        .setCellValueFactory(new PropertyValueFactory<>("minStockLevel"));
        colDays       .setCellValueFactory(new PropertyValueFactory<>("daysUntilDeficit"));

        colConsumption.setCellValueFactory(c -> {
            DeficitForecast f = c.getValue();
            String val = f.getAvgDailyConsumption() != null
                    ? f.getAvgDailyConsumption().setScale(2,
                        java.math.RoundingMode.HALF_UP).toPlainString()
                    : "—";
            return new SimpleStringProperty(val);
        });

        colDeficitDate.setCellValueFactory(c -> {
            DeficitForecast f = c.getValue();
            if (f.isDeficitNow()) return new SimpleStringProperty("⛔ Уже сейчас!");
            if (f.getEstimatedDeficitDate() == null) return new SimpleStringProperty("—");
            return new SimpleStringProperty(f.getEstimatedDeficitDate().toString());
        });

        colCalculated.setCellValueFactory(c -> {
            DeficitForecast f = c.getValue();
            String val = f.getLastCalculated() != null
                    ? f.getLastCalculated().format(DT_FMT) : "—";
            return new SimpleStringProperty(val);
        });

        // Форматирование дней до дефицита
        colDays.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Integer days, boolean empty) {
                super.updateItem(days, empty);
                if (empty || days == null) { setText(null); return; }
                if (days < 0)             setText("⛔ Дефицит!");
                else if (days == Integer.MAX_VALUE) setText("∞ Нет расхода");
                else                      setText(days + " дн.");
            }
        });

        // Цветовая подсветка строк
        forecastTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(DeficitForecast item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("row-critical", "row-warning");
                if (!empty && item != null) {
                    if (item.isDeficitNow())  getStyleClass().add("row-critical");
                    else if (item.isCritical()) getStyleClass().add("row-warning");
                }
            }
        });
    }

    @FXML
    public void loadData() {
        statusLabel.setText("Загрузка прогнозов...");
        Thread t = new Thread(() -> {
            try {
                Response resp = ClientContext.getInstance()
                        .send(new Request.Builder(Action.GET_FORECAST_ALL).build());
                Platform.runLater(() -> {
                    if (resp.isSuccess() && resp.getData() instanceof List<?> raw) {
                        allForecasts.setAll((List<DeficitForecast>) raw);
                        applyFilter();
                        long critical = allForecasts.stream()
                                .filter(DeficitForecast::isCritical).count();
                        long deficit  = allForecasts.stream()
                                .filter(DeficitForecast::isDeficitNow).count();
                        statusLabel.setText("Позиций: " + allForecasts.size()
                                + "  |  Критических: " + critical
                                + "  |  Дефицит сейчас: " + deficit);
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

    @FXML
    public void applyFilter() {
        String wh = warehouseFilter.getValue();
        filtered.setPredicate(f ->
                wh == null || "Все склады".equals(wh) || wh.equals(f.getWarehouseName()));
    }

    @FXML
    public void handleRecalculate() {
        statusLabel.setText("Пересчёт прогнозов...");
        Thread t = new Thread(() -> {
            try {
                Response resp = ClientContext.getInstance()
                        .send(new Request.Builder(Action.RECALCULATE_FORECAST).build());
                Platform.runLater(() -> {
                    if (resp.isSuccess()) { loadData(); }
                    else statusLabel.setText("Ошибка: " + resp.getMessage());
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Ошибка: " + e.getMessage()));
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void loadWarehouses() {
        Thread t = new Thread(() -> {
            try {
                Response resp = ClientContext.getInstance()
                        .send(new Request.Builder(Action.GET_ALL_WAREHOUSES).build());
                Platform.runLater(() -> {
                    warehouseFilter.getItems().clear();
                    warehouseFilter.getItems().add("Все склады");
                    if (resp.isSuccess() && resp.getData() instanceof List<?> raw) {
                        for (Object o : raw)
                            warehouseFilter.getItems().add(((Warehouse) o).getName());
                    }
                    warehouseFilter.getSelectionModel().selectFirst();
                });
            } catch (Exception ignored) {}
        });
        t.setDaemon(true);
        t.start();
    }
}
