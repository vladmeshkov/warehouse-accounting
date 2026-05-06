package by.bsuir.warehouse.client.controller;

import by.bsuir.warehouse.client.network.ClientContext;
import by.bsuir.warehouse.common.model.AuditEntry;
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

import java.time.format.DateTimeFormatter;
import java.util.List;

public class AuditController {

    @FXML private TableView<AuditEntry> auditTable;
    @FXML private TableColumn<AuditEntry, Integer> colId;
    @FXML private TableColumn<AuditEntry, String> colTimestamp, colUser, colAction, colDetails;
    @FXML private ComboBox<String> actionFilter;
    @FXML private Label statusLabel;

    private final ObservableList<AuditEntry> entries = FXCollections.observableArrayList();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    @FXML
    public void initialize() {
        colId       .setCellValueFactory(new PropertyValueFactory<>("id"));
        colTimestamp.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getTimestamp() != null ? c.getValue().getTimestamp().format(FMT) : "—"));
        colUser     .setCellValueFactory(new PropertyValueFactory<>("userFullName"));
        colAction   .setCellValueFactory(c -> {
            String code = c.getValue().getAction();
            String display = switch (code) {
                case "LOGIN" -> "Вход в систему";
                case "LOGOUT" -> "Выход из системы";
                case "CREATE_USER" -> "Добавление пользователя";
                case "UPDATE_USER" -> "Изменение пользователя";
                case "DELETE_USER" -> "Удаление пользователя";
                case "BLOCK_USER" -> "Блокировка пользователя";
                case "APPROVE_USER" -> "Одобрение регистрации";
                case "REJECT_USER" -> "Отклонение регистрации";
                case "CREATE_PRODUCT" -> "Добавление товара";
                case "UPDATE_PRODUCT" -> "Изменение товара";
                case "DELETE_PRODUCT" -> "Удаление товара";
                case "CREATE_WAREHOUSE" -> "Добавление склада";
                case "UPDATE_WAREHOUSE" -> "Изменение склада";
                case "DELETE_WAREHOUSE" -> "Удаление склада";
                case "CREATE_SUPPLIER" -> "Добавление поставщика";
                case "UPDATE_SUPPLIER" -> "Изменение поставщика";
                case "DELETE_SUPPLIER" -> "Удаление поставщика";
                case "CREATE_CUSTOMER" -> "Добавление покупателя";
                case "UPDATE_CUSTOMER" -> "Изменение покупателя";
                case "DELETE_CUSTOMER" -> "Удаление покупателя";
                case "INCOME" -> "Приходная накладная";
                case "OUTCOME" -> "Расходная накладная";
                case "TRANSFER" -> "Перемещение";
                case "INVENTORY" -> "Инвентаризация";
                default -> code;
            };
            return new SimpleStringProperty(display);
        });
        colDetails  .setCellValueFactory(new PropertyValueFactory<>("details"));

        auditTable.setItems(entries);
        auditTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Заполняем фильтр
        actionFilter.getItems().add("Все действия");
        actionFilter.getItems().addAll(
                "Вход в систему", "Выход из системы",
                "Добавление пользователя", "Изменение пользователя", "Удаление пользователя",
                "Блокировка пользователя", "Одобрение регистрации", "Отклонение регистрации",
                "Добавление товара", "Изменение товара", "Удаление товара",
                "Добавление склада", "Изменение склада", "Удаление склада",
                "Добавление поставщика", "Изменение поставщика", "Удаление поставщика",
                "Добавление покупателя", "Изменение покупателя", "Удаление покупателя",
                "Приходная накладная", "Расходная накладная", "Перемещение", "Инвентаризация"
        );
        actionFilter.getSelectionModel().selectFirst();
        actionFilter.setOnAction(e -> loadAudit());

        loadAudit();
    }

    @FXML
    public void loadAudit() {
        statusLabel.setText("Загрузка...");
        String selected = actionFilter.getValue();
        String filterCode = mapToCode(selected);

        new Thread(() -> {
            try {
                Request.Builder builder = new Request.Builder(Action.GET_AUDIT_LOG);
                if (filterCode != null) {
                    builder.param("actionType", filterCode);
                }
                Response resp = ClientContext.getInstance().send(builder.build());
                Platform.runLater(() -> {
                    if (resp.isSuccess() && resp.getData() instanceof List<?> raw) {
                        entries.setAll((List<AuditEntry>) raw);
                        statusLabel.setText("Записей: " + entries.size());
                    } else {
                        statusLabel.setText("Ошибка: " + resp.getMessage());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Ошибка соединения"));
            }
        }).start();
    }

    private String mapToCode(String display) {
        if (display == null || "Все действия".equals(display)) return null;
        return switch (display) {
            case "Вход в систему" -> "LOGIN";
            case "Выход из системы" -> "LOGOUT";
            case "Добавление пользователя" -> "CREATE_USER";
            case "Изменение пользователя" -> "UPDATE_USER";
            case "Удаление пользователя" -> "DELETE_USER";
            case "Блокировка пользователя" -> "BLOCK_USER";
            case "Одобрение регистрации" -> "APPROVE_USER";
            case "Отклонение регистрации" -> "REJECT_USER";
            case "Добавление товара" -> "CREATE_PRODUCT";
            case "Изменение товара" -> "UPDATE_PRODUCT";
            case "Удаление товара" -> "DELETE_PRODUCT";
            case "Добавление склада" -> "CREATE_WAREHOUSE";
            case "Изменение склада" -> "UPDATE_WAREHOUSE";
            case "Удаление склада" -> "DELETE_WAREHOUSE";
            case "Добавление поставщика" -> "CREATE_SUPPLIER";
            case "Изменение поставщика" -> "UPDATE_SUPPLIER";
            case "Удаление поставщика" -> "DELETE_SUPPLIER";
            case "Добавление покупателя" -> "CREATE_CUSTOMER";
            case "Изменение покупателя" -> "UPDATE_CUSTOMER";
            case "Удаление покупателя" -> "DELETE_CUSTOMER";
            case "Приходная накладная" -> "INCOME";
            case "Расходная накладная" -> "OUTCOME";
            case "Перемещение" -> "TRANSFER";
            case "Инвентаризация" -> "INVENTORY";
            default -> null;
        };
    }
}