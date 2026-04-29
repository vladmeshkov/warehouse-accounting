package by.bsuir.warehouse.client.controller;

import by.bsuir.warehouse.client.network.ClientContext;
import by.bsuir.warehouse.common.model.DeficitForecast;
import by.bsuir.warehouse.common.protocol.Action;
import by.bsuir.warehouse.common.protocol.Request;
import by.bsuir.warehouse.common.protocol.Response;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class DashboardController {

    @FXML private Label subtitleLabel;
    @FXML private Label productCountLabel;
    @FXML private Label warehouseCountLabel;
    @FXML private Label criticalCountLabel;
    @FXML private Label docsTodayLabel;

    @FXML private TableView<DeficitForecast> criticalTable;
    @FXML private TableColumn<DeficitForecast, String>  colWarehouse;
    @FXML private TableColumn<DeficitForecast, String>  colArticle;
    @FXML private TableColumn<DeficitForecast, String>  colProduct;
    @FXML private TableColumn<DeficitForecast, Integer> colStock;
    @FXML private TableColumn<DeficitForecast, Integer> colMin;
    @FXML private TableColumn<DeficitForecast, Integer> colDays;
    @FXML private TableColumn<DeficitForecast, String>  colDate;

    @FXML
    public void initialize() {
        subtitleLabel.setText("Обновлено: "
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
        setupTable();
        refresh();
    }

    private void setupTable() {
        colWarehouse.setCellValueFactory(new PropertyValueFactory<>("warehouseName"));
        colArticle  .setCellValueFactory(new PropertyValueFactory<>("productArticle"));
        colProduct  .setCellValueFactory(new PropertyValueFactory<>("productName"));
        colStock    .setCellValueFactory(new PropertyValueFactory<>("currentStock"));
        colMin      .setCellValueFactory(new PropertyValueFactory<>("minStockLevel"));
        colDays     .setCellValueFactory(new PropertyValueFactory<>("daysUntilDeficit"));
        colDate.setCellValueFactory(c -> {
            DeficitForecast f = c.getValue();
            String val = f.getEstimatedDeficitDate() != null
                    ? f.getEstimatedDeficitDate().toString() : "—";
            return new javafx.beans.property.SimpleStringProperty(val);
        });

        // Подсветка строк: красный — дефицит, жёлтый — критический
        criticalTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(DeficitForecast item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("row-critical", "row-warning");
                if (!empty && item != null) {
                    if (item.isDeficitNow()) getStyleClass().add("row-critical");
                    else if (item.isCritical()) getStyleClass().add("row-warning");
                }
            }
        });
    }

    @FXML
    public void refresh() {
        Thread t = new Thread(() -> {
            try {
                ClientContext ctx = ClientContext.getInstance();

                // Подсчёт товаров
                Response prodResp = ctx.send(new Request.Builder(Action.GET_ALL_PRODUCTS).build());
                // Подсчёт складов
                Response whResp = ctx.send(new Request.Builder(Action.GET_ALL_WAREHOUSES).build());
                // Прогнозы
                Response fcResp = ctx.send(new Request.Builder(Action.GET_FORECAST_ALL).build());

                Platform.runLater(() -> {
                    if (prodResp.isSuccess() && prodResp.getData() instanceof List<?> pl)
                        productCountLabel.setText(String.valueOf(pl.size()));
                    if (whResp.isSuccess() && whResp.getData() instanceof List<?> wl)
                        warehouseCountLabel.setText(String.valueOf(wl.size()));

                    if (fcResp.isSuccess() && fcResp.getData() instanceof List<?> raw) {
                        @SuppressWarnings("unchecked")
                        List<DeficitForecast> forecasts = (List<DeficitForecast>) raw;
                        List<DeficitForecast> critical = forecasts.stream()
                                .filter(f -> f.getDaysUntilDeficit() <= 7)
                                .toList();
                        criticalCountLabel.setText(String.valueOf(critical.size()));
                        criticalTable.setItems(FXCollections.observableArrayList(critical));
                    }

                    subtitleLabel.setText("Обновлено: "
                            + LocalDateTime.now().format(
                                    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
                });
            } catch (Exception e) {
                Platform.runLater(() -> subtitleLabel.setText("Ошибка загрузки: " + e.getMessage()));
            }
        });
        t.setDaemon(true);
        t.start();
    }
}
