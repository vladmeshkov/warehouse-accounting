package by.bsuir.warehouse.client.controller;

import by.bsuir.warehouse.client.network.ClientContext;
import by.bsuir.warehouse.common.model.DeficitForecast;
import by.bsuir.warehouse.common.model.Document;
import by.bsuir.warehouse.common.protocol.Action;
import by.bsuir.warehouse.common.protocol.Request;
import by.bsuir.warehouse.common.protocol.Response;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class DashboardController {

    @FXML private Label subtitleLabel;
    @FXML private Label greetingLabel;
    @FXML private Label avatarLabel;
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
    @FXML private TableColumn<DeficitForecast, String>  colDays;
    @FXML private TableColumn<DeficitForecast, String>  colDate;

    @FXML
    public void initialize() {
        String fullName = ClientContext.getInstance().getCurrentUser().getFullName();
        String initials = (fullName != null && !fullName.isEmpty())
                ? fullName.substring(0, 1).toUpperCase()
                : "A";
        avatarLabel.setText(initials);
        greetingLabel.setText("Добро пожаловать, " + (fullName != null ? fullName : "пользователь") + "!");

        setupTable();
        criticalTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        refresh();
    }

    private void setupTable() {
        colWarehouse.setCellValueFactory(new PropertyValueFactory<>("warehouseName"));
        colArticle  .setCellValueFactory(new PropertyValueFactory<>("productArticle"));
        colProduct  .setCellValueFactory(new PropertyValueFactory<>("productName"));
        colStock    .setCellValueFactory(new PropertyValueFactory<>("currentStock"));
        colMin      .setCellValueFactory(new PropertyValueFactory<>("minStockLevel"));

        // Столбец "Статус" (бывший "Дней до дефицита")
        colDays.setCellValueFactory(c -> {
            DeficitForecast f = c.getValue();
            if (f.getDaysUntilDeficit() < 0) {
                return new javafx.beans.property.SimpleStringProperty("⚠ Уже сейчас");
            } else if (f.getDaysUntilDeficit() == Integer.MAX_VALUE) {
                return new javafx.beans.property.SimpleStringProperty("∞ Нет расхода");
            } else {
                return new javafx.beans.property.SimpleStringProperty(f.getDaysUntilDeficit() + " дн.");
            }
        });

        // Столбец "Дата дефицита"
        colDate.setCellValueFactory(c -> {
            DeficitForecast f = c.getValue();
            if (f.getDaysUntilDeficit() < 0) {
                return new javafx.beans.property.SimpleStringProperty("Уже наступил");
            }
            if (f.getEstimatedDeficitDate() != null) {
                return new javafx.beans.property.SimpleStringProperty(f.getEstimatedDeficitDate().toString());
            }
            return new javafx.beans.property.SimpleStringProperty("—");
        });

        // Подсветка строк
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
                Response prodResp = ctx.send(new Request.Builder(Action.GET_ALL_PRODUCTS).build());
                Response whResp = ctx.send(new Request.Builder(Action.GET_ALL_WAREHOUSES).build());
                Response fcResp = ctx.send(new Request.Builder(Action.GET_FORECAST_ALL).build());
                Response docsResp = ctx.send(new Request.Builder(Action.GET_DOCUMENTS).build());

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

                    if (docsResp.isSuccess() && docsResp.getData() instanceof List<?> docsRaw) {
                        @SuppressWarnings("unchecked")
                        List<Document> docs = (List<Document>) docsRaw;
                        LocalDate today = LocalDate.now();
                        long todayCount = docs.stream()
                                .filter(d -> d.getDocumentDate() != null
                                        && d.getDocumentDate().toLocalDate().equals(today))
                                .count();
                        docsTodayLabel.setText(String.valueOf(todayCount));
                    }

                    subtitleLabel.setText("Обновлено: "
                            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
                });
            } catch (Exception e) {
                Platform.runLater(() -> subtitleLabel.setText("Ошибка загрузки: " + e.getMessage()));
            }
        });
        t.setDaemon(true);
        t.start();
    }
}