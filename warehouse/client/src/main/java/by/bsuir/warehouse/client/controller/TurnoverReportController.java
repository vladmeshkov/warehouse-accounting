package by.bsuir.warehouse.client.controller;

import by.bsuir.warehouse.client.network.ClientContext;
import by.bsuir.warehouse.common.model.TurnoverEntry;
import by.bsuir.warehouse.common.protocol.Action;
import by.bsuir.warehouse.common.protocol.Request;
import by.bsuir.warehouse.common.protocol.Response;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class TurnoverReportController {

    @FXML private TableView<TurnoverEntry> reportTable;
    @FXML private TableColumn<TurnoverEntry, String> colArticle, colProduct, colUnit;
    @FXML private TableColumn<TurnoverEntry, BigDecimal> colIncome, colOutcome;
    @FXML private TableColumn<TurnoverEntry, Integer> colStock;
    @FXML private TableColumn<TurnoverEntry, String> colTurnover;
    @FXML private Label statusLabel;
    @FXML private Button saveButton;

    private final ObservableList<TurnoverEntry> entries = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colArticle .setCellValueFactory(new PropertyValueFactory<>("article"));
        colProduct .setCellValueFactory(new PropertyValueFactory<>("productName"));
        colUnit    .setCellValueFactory(new PropertyValueFactory<>("unit"));
        colIncome  .setCellValueFactory(new PropertyValueFactory<>("incomeQty"));
        colOutcome .setCellValueFactory(new PropertyValueFactory<>("outcomeQty"));
        colStock   .setCellValueFactory(new PropertyValueFactory<>("currentStock"));
        colTurnover.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTurnoverRatio()));

        reportTable.setItems(entries);
        reportTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        loadReport();
    }

    @FXML
    public void loadReport() {
        statusLabel.setText("Загрузка отчёта...");
        new Thread(() -> {
            try {
                Response resp = ClientContext.getInstance()
                        .send(new Request.Builder(Action.GET_REPORT_TURNOVER).build());
                Platform.runLater(() -> {
                    if (resp.isSuccess() && resp.getData() instanceof List<?> raw) {
                        entries.setAll((List<TurnoverEntry>) raw);
                        statusLabel.setText("Позиций: " + entries.size());
                    } else {
                        statusLabel.setText("Ошибка: " + resp.getMessage());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Ошибка соединения"));
            }
        }).start();
    }

    @FXML
    public void saveToFile() {
        if (entries.isEmpty()) {
            statusLabel.setText("Нет данных для сохранения");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Сохранить отчёт по оборачиваемости");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Текстовый файл", "*.txt"));
        fileChooser.setInitialFileName("turnover_report_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".txt");

        Stage stage = (Stage) reportTable.getScene().getWindow();
        File file = fileChooser.showSaveDialog(stage);
        if (file == null) return;

        new Thread(() -> {
            try (java.io.OutputStreamWriter writer =
                         new java.io.OutputStreamWriter(
                                 new java.io.FileOutputStream(file),
                                 java.nio.charset.StandardCharsets.UTF_8)) {

                writer.write("Отчёт по оборачиваемости товаров\n");
                writer.write("Сформирован: " +
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")) + "\n\n");

                // Фиксированная ширина колонок для идеального выравнивания
                String header = String.format("%-12s %-25s %-10s %-10s %-10s %-10s %-10s\n",
                        "Артикул", "Товар", "Ед.изм.", "Приход", "Расход", "Остаток", "Оборот");
                writer.write(header);
                writer.write("-".repeat(header.length()) + "\n");

                for (TurnoverEntry e : entries) {
                    String name = e.getProductName() != null ? e.getProductName() : "";
                    if (name.length() > 25) name = name.substring(0, 23) + "..";

                    String unit = e.getUnit() != null ? e.getUnit() : "";
                    if (unit.length() > 10) unit = unit.substring(0, 8) + "..";

                    String income = e.getIncomeQty() != null ? e.getIncomeQty().toString() : "0";
                    String outcome = e.getOutcomeQty() != null ? e.getOutcomeQty().toString() : "0";

                    writer.write(String.format("%-12s %-25s %-10s %-10s %-10s %-10d %-10s\n",
                            e.getArticle(),
                            name,
                            unit,
                            income,
                            outcome,
                            e.getCurrentStock(),
                            e.getTurnoverRatio()));
                }
                Platform.runLater(() -> statusLabel.setText("Файл сохранён: " + file.getName()));
            } catch (IOException e) {
                Platform.runLater(() -> statusLabel.setText("Ошибка сохранения: " + e.getMessage()));
            }
        }).start();
    }
}