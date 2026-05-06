package by.bsuir.warehouse.client.controller;

import by.bsuir.warehouse.client.network.ClientContext;
import by.bsuir.warehouse.common.model.SalesReportEntry;
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
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class SalesReportController {

    @FXML private DatePicker dateFrom;
    @FXML private DatePicker dateTo;
    @FXML private TableView<SalesReportEntry> reportTable;
    @FXML private TableColumn<SalesReportEntry, String> colArticle, colProduct, colQty, colPrice, colTotal;
    @FXML private Label statusLabel;
    @FXML private Button generateBtn;

    private final ObservableList<SalesReportEntry> entries = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colArticle.setCellValueFactory(new PropertyValueFactory<>("article"));
        colProduct.setCellValueFactory(new PropertyValueFactory<>("productName"));
        colQty.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getTotalQuantity() != null ? c.getValue().getTotalQuantity().toPlainString() : "0"));
        colPrice.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getAvgPrice() != null ?
                        c.getValue().getAvgPrice().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() : "—"));
        colTotal.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getTotalCost() != null ?
                        c.getValue().getTotalCost().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() : "0"));

        reportTable.setItems(entries);
        reportTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        dateTo.setValue(LocalDate.now());
        dateFrom.setValue(LocalDate.now().minusDays(30));

        // Запрет выбора будущих дат
        dateTo.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                if (date.isAfter(LocalDate.now())) {
                    setDisable(true);
                    setStyle("-fx-background-color: #eeeeee;");
                }
            }
        });

        dateFrom.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                if (date.isAfter(LocalDate.now())) {
                    setDisable(true);
                    setStyle("-fx-background-color: #eeeeee;");
                }
            }
        });
    }

    @FXML
    public void generateReport() {
        LocalDate from = dateFrom.getValue();
        LocalDate to = dateTo.getValue();

        if (from == null || to == null) {
            showAlert("Выберите даты", "Необходимо выбрать обе даты: «С» и «По».");
            return;
        }

        // Проверка, что дата "С" не позже "По"
        if (from.isAfter(to)) {
            showAlert("Ошибка периода", "Дата «С» должна быть раньше даты «По».");
            return;
        }

        // Проверка, что дата "По" не в будущем (хотя календарь не даёт выбрать, но на всякий случай)
        if (to.isAfter(LocalDate.now())) {
            showAlert("Ошибка периода", "Конечная дата не может быть в будущем.");
            return;
        }

        statusLabel.setText("Формирование отчёта...");
        generateBtn.setDisable(true);
        new Thread(() -> {
            try {
                Request req = new Request.Builder(Action.GET_SALES_REPORT)
                        .param("dateFrom", from.toString())
                        .param("dateTo", to.toString())
                        .build();
                Response resp = ClientContext.getInstance().send(req);
                Platform.runLater(() -> {
                    generateBtn.setDisable(false);
                    if (resp.isSuccess() && resp.getData() instanceof List<?> raw) {
                        entries.setAll((List<SalesReportEntry>) raw);
                        if (entries.isEmpty()) {
                            statusLabel.setText("Нет данных за выбранный период");
                        } else {
                            statusLabel.setText("Продано позиций: " + entries.size());
                        }
                    } else {
                        statusLabel.setText("Ошибка: " + resp.getMessage());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    generateBtn.setDisable(false);
                    statusLabel.setText("Ошибка соединения");
                });
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
        fileChooser.setTitle("Сохранить отчёт по продажам");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Текстовый файл", "*.txt"));
        fileChooser.setInitialFileName("sales_report_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".txt");

        Stage stage = (Stage) reportTable.getScene().getWindow();
        File file = fileChooser.showSaveDialog(stage);
        if (file == null) return;

        new Thread(() -> {
            try (OutputStreamWriter writer = new OutputStreamWriter(
                    new java.io.FileOutputStream(file), StandardCharsets.UTF_8)) {
                writer.write("Отчёт по стоимости проданных товаров\n");
                writer.write("Период: " + dateFrom.getValue() + " – " + dateTo.getValue() + "\n");
                writer.write("Сформирован: " +
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")) + "\n\n");

                String header = String.format("%-15s %-25s %-12s %-12s %-15s\n",
                        "Артикул", "Товар", "Продано", "Ср. цена", "Стоимость");
                writer.write(header);
                writer.write("-".repeat(header.length()) + "\n");

                java.math.BigDecimal grandTotal = java.math.BigDecimal.ZERO;
                for (SalesReportEntry e : entries) {
                    String name = e.getProductName() != null ? e.getProductName() : "";
                    if (name.length() > 25) name = name.substring(0, 23) + "..";
                    String qty = e.getTotalQuantity() != null ? e.getTotalQuantity().toPlainString() : "0";
                    String avg = e.getAvgPrice() != null ?
                            e.getAvgPrice().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() : "—";
                    String cost = e.getTotalCost() != null ?
                            e.getTotalCost().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() : "0";
                    writer.write(String.format("%-15s %-25s %-12s %-12s %-15s\n",
                            e.getArticle(), name, qty, avg, cost));
                    if (e.getTotalCost() != null) grandTotal = grandTotal.add(e.getTotalCost());
                }

                writer.write("\n" + String.format("%-15s %-25s %-12s %-12s %-15s\n",
                        "", "", "", "ИТОГО:", grandTotal.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()));

                Platform.runLater(() -> statusLabel.setText("Файл сохранён: " + file.getName()));
            } catch (IOException e) {
                Platform.runLater(() -> statusLabel.setText("Ошибка сохранения: " + e.getMessage()));
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