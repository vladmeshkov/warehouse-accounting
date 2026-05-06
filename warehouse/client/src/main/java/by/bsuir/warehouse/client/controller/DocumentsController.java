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
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
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
    @FXML private Button                            exportBtn;

    private final ObservableList<Document> allDocs = FXCollections.observableArrayList();
    private FilteredList<Document> filtered;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    @FXML
    public void initialize() {
        setupColumns();
        filtered = new FilteredList<>(allDocs, d -> true);
        docsTable.setItems(filtered);
        docsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        typeFilter.getItems().add("Все типы");
        for (DocumentType dt : DocumentType.values())
            typeFilter.getItems().add(dt.getDisplayName());
        typeFilter.getSelectionModel().selectFirst();

        // Экспорт в Excel – только для бухгалтера
        boolean isAccountant = ClientContext.getInstance().getCurrentUser() != null
                && Role.ACCOUNTANT.equals(ClientContext.getInstance().getCurrentUser().getRole().getRoleName());
        exportBtn.setVisible(isAccountant);
        exportBtn.setManaged(isAccountant);

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

    @FXML
    public void showDetails() {
        Document selected = docsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Выберите документ для просмотра");
            return;
        }

        statusLabel.setText("Загрузка деталей...");
        new Thread(() -> {
            try {
                Response resp = ClientContext.getInstance()
                        .send(new Request.Builder(Action.GET_DOCUMENT_BY_ID)
                                .param("id", selected.getId()).build());
                Platform.runLater(() -> {
                    if (resp.isSuccess() && resp.getData() instanceof Document doc) {
                        showDocumentDetailsDialog(doc);
                        statusLabel.setText("");
                    } else {
                        statusLabel.setText("Ошибка загрузки документа");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Ошибка: " + e.getMessage()));
            }
        }).start();
    }

    private void showDocumentDetailsDialog(Document doc) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Детали документа");
        dialog.setResizable(true);

        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.setPrefSize(750, 520);
        dialogPane.getStylesheets().add(getClass().getResource(
                "/by/bsuir/warehouse/client/css/style.css").toExternalForm());
        ButtonType closeBtn = new ButtonType("Закрыть", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().add(closeBtn);

        VBox root = new VBox(16);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: white;");

        Label titleLabel = new Label(doc.getDocumentType().getDisplayName() + " №" + doc.getDocumentNumber());
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        titleLabel.setTextFill(Color.web("#2c3e50"));

        VBox mainInfoSection = createSection("📋 Основная информация",
                new String[]{"Дата", "Склад отправитель", "Склад получатель", "Ответственный", "Комментарий"},
                new String[]{
                        doc.getDocumentDate() != null ? doc.getDocumentDate().format(FMT) : "—",
                        doc.getWarehouseFrom() != null ? doc.getWarehouseFrom().getName() : "—",
                        doc.getWarehouseTo() != null ? doc.getWarehouseTo().getName() : "—",
                        doc.getResponsibleUser() != null ? doc.getResponsibleUser().getFullName() : "—",
                        doc.getComment() != null && !doc.getComment().isEmpty() ? doc.getComment() : "—"
                });

        VBox counterpartSection = createSection("🤝 Контрагенты",
                new String[]{"Поставщик", "Покупатель"},
                new String[]{
                        doc.getSupplier() != null ? doc.getSupplier().getName() : "—",
                        doc.getCustomer() != null ? doc.getCustomer().getName() : "—"
                });

        VBox itemsSection = new VBox(8);
        Label itemsTitle = new Label("📦 Товарные позиции");
        itemsTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        itemsTitle.setTextFill(Color.web("#2c3e50"));

        TableView<DocumentItem> itemsTable = new TableView<>();
        itemsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        itemsTable.setPrefHeight(150);

        TableColumn<DocumentItem, String> colName = new TableColumn<>("Товар");
        colName.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getProduct() != null ? c.getValue().getProduct().getName() : "?"));
        colName.setPrefWidth(250);

        TableColumn<DocumentItem, String> colArticle = new TableColumn<>("Артикул");
        colArticle.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getProduct() != null ? c.getValue().getProduct().getArticle() : "?"));
        colArticle.setPrefWidth(100);

        TableColumn<DocumentItem, String> colQty = new TableColumn<>("Кол-во");
        colQty.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getQuantity().toPlainString()));
        colQty.setPrefWidth(80);

        TableColumn<DocumentItem, String> colPrice = new TableColumn<>("Цена");
        colPrice.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getPrice() != null ? c.getValue().getPrice().toPlainString() : "—"));
        colPrice.setPrefWidth(100);

        TableColumn<DocumentItem, String> colSum = new TableColumn<>("Сумма");
        colSum.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTotal().toPlainString()));
        colSum.setPrefWidth(120);

        itemsTable.getColumns().addAll(colName, colArticle, colQty, colPrice, colSum);
        itemsTable.setItems(FXCollections.observableArrayList(doc.getItems()));

        BigDecimal totalSum = doc.getItems().stream()
                .map(DocumentItem::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Label totalLabel = new Label("Итого: " + totalSum.setScale(2, java.math.RoundingMode.HALF_UP) + " руб.");
        totalLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        totalLabel.setTextFill(Color.web("#27ae60"));
        totalLabel.setPadding(new Insets(4, 0, 0, 0));

        itemsSection.getChildren().addAll(itemsTitle, itemsTable, totalLabel);

        root.getChildren().addAll(titleLabel, new Separator(), mainInfoSection, counterpartSection, itemsSection);

        ScrollPane scrollPane = new ScrollPane(root);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        dialogPane.setContent(scrollPane);

        dialog.showAndWait();
    }

    private VBox createSection(String title, String[] labels, String[] values) {
        VBox section = new VBox(8);
        section.setPadding(new Insets(16));
        section.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 8;");

        Label sectionTitle = new Label(title);
        sectionTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        sectionTitle.setTextFill(Color.web("#2c3e50"));

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(6);

        for (int i = 0; i < labels.length; i++) {
            Label lbl = new Label(labels[i]);
            lbl.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 13));
            lbl.setTextFill(Color.web("#7f8c8d"));

            Label val = new Label(values[i]);
            val.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 13));
            val.setTextFill(Color.web("#2c3e50"));
            val.setWrapText(true);

            grid.add(lbl, 0, i);
            grid.add(val, 1, i);
        }

        section.getChildren().addAll(sectionTitle, grid);
        return section;
    }

    @FXML
    public void handleExport() {
        String selectedType = typeFilter.getValue();
        String typeCode = null;
        if (selectedType != null && !"Все типы".equals(selectedType)) {
            if (selectedType.equals("Приходная накладная")) typeCode = "INCOME";
            else if (selectedType.equals("Расходная накладная")) typeCode = "OUTCOME";
            else if (selectedType.equals("Внутреннее перемещение")) typeCode = "TRANSFER";
            else if (selectedType.equals("Инвентаризация")) typeCode = "INVENTORY";
        }

        final String finalTypeCode = typeCode;
        statusLabel.setText("Экспорт...");
        new Thread(() -> {
            try {
                Request.Builder builder = new Request.Builder(Action.EXPORT_DOCUMENTS_CSV);
                if (finalTypeCode != null) {
                    builder.param("type", finalTypeCode);
                } else {
                    builder.param("type", "ALL");
                }
                Response resp = ClientContext.getInstance().send(builder.build());
                Platform.runLater(() -> {
                    if (resp.isSuccess() && resp.getFileData() != null) {
                        FileChooser fileChooser = new FileChooser();
                        fileChooser.setTitle("Сохранить реестр документов");
                        fileChooser.getExtensionFilters().add(
                                new FileChooser.ExtensionFilter("Excel файл", "*.xlsx"));
                        fileChooser.setInitialFileName(resp.getFileName() != null ? resp.getFileName() : "documents.xlsx");
                        Stage stage = (Stage) docsTable.getScene().getWindow();
                        File file = fileChooser.showSaveDialog(stage);
                        if (file != null) {
                            try (FileOutputStream fos = new FileOutputStream(file)) {
                                fos.write(resp.getFileData());
                                statusLabel.setText("Файл сохранён: " + file.getName());
                            } catch (Exception e) {
                                statusLabel.setText("Ошибка сохранения: " + e.getMessage());
                            }
                        } else {
                            statusLabel.setText("Экспорт отменён");
                        }
                    } else {
                        statusLabel.setText("Ошибка экспорта: " + resp.getMessage());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Ошибка соединения"));
            }
        }).start();
    }
}