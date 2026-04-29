package by.bsuir.warehouse.client.controller;

import by.bsuir.warehouse.client.network.ClientContext;
import by.bsuir.warehouse.common.model.Document;
import by.bsuir.warehouse.common.model.DocumentType;
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

public class DocumentsController {

    @FXML private TableView<Document>               docsTable;
    @FXML private TableColumn<Document, Integer>    colId;
    @FXML private TableColumn<Document, String>     colNumber;
    @FXML private TableColumn<Document, String>     colType;
    @FXML private TableColumn<Document, String>     colDate;
    @FXML private TableColumn<Document, String>     colWarehouse;
    @FXML private TableColumn<Document, String>     colCounterpart;
    @FXML private TableColumn<Document, String>     colUser;
    @FXML private ComboBox<String>                  typeFilter;
    @FXML private Label                             statusLabel;

    private final ObservableList<Document> allDocs = FXCollections.observableArrayList();
    private FilteredList<Document> filtered;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    @FXML
    public void initialize() {
        setupColumns();
        filtered = new FilteredList<>(allDocs, d -> true);
        docsTable.setItems(filtered);

        typeFilter.getItems().add("Все типы");
        for (DocumentType dt : DocumentType.values())
            typeFilter.getItems().add(dt.getDisplayName());
        typeFilter.getSelectionModel().selectFirst();

        loadData();
    }

    private void setupColumns() {
        colId    .setCellValueFactory(new PropertyValueFactory<>("id"));
        colNumber.setCellValueFactory(new PropertyValueFactory<>("documentNumber"));
        colType  .setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getDocumentType().getDisplayName()));
        colDate  .setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getDocumentDate() != null
                        ? c.getValue().getDocumentDate().format(FMT) : "—"));
        colWarehouse.setCellValueFactory(c -> {
            Document d = c.getValue();
            String wh = "";
            if (d.getWarehouseTo() != null)   wh = "→ " + d.getWarehouseTo().getName();
            if (d.getWarehouseFrom() != null) wh = d.getWarehouseFrom().getName() + " " + wh;
            return new SimpleStringProperty(wh.trim());
        });
        colCounterpart.setCellValueFactory(c -> {
            Document d = c.getValue();
            if (d.getSupplier() != null) return new SimpleStringProperty(d.getSupplier().getName());
            if (d.getCustomer() != null) return new SimpleStringProperty(d.getCustomer().getName());
            return new SimpleStringProperty("—");
        });
        colUser.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getResponsibleUser() != null
                        ? c.getValue().getResponsibleUser().getFullName() : "—"));
    }

    @FXML
    public void loadData() {
        statusLabel.setText("Загрузка...");
        Thread t = new Thread(() -> {
            try {
                Response resp = ClientContext.getInstance()
                        .send(new Request.Builder(Action.GET_DOCUMENTS).build());
                Platform.runLater(() -> {
                    if (resp.isSuccess() && resp.getData() instanceof List<?> raw) {
                        allDocs.setAll((List<Document>) raw);
                        applyFilter();
                        statusLabel.setText("Документов: " + allDocs.size());
                    } else statusLabel.setText("Ошибка: " + resp.getMessage());
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
        String sel = typeFilter.getValue();
        filtered.setPredicate(d -> {
            if (sel == null || "Все типы".equals(sel)) return true;
            return d.getDocumentType().getDisplayName().equals(sel);
        });
        statusLabel.setText("Отображено: " + filtered.size() + " из " + allDocs.size());
    }
}
