package by.bsuir.warehouse.client.controller;

import by.bsuir.warehouse.client.network.ClientContext;
import by.bsuir.warehouse.common.model.Customer;
import by.bsuir.warehouse.common.protocol.Action;
import by.bsuir.warehouse.common.protocol.Request;
import by.bsuir.warehouse.common.protocol.Response;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;

import java.util.List;
import java.util.Optional;

public class CustomersController {

    @FXML private TableView<Customer> customersTable;
    @FXML private TableColumn<Customer, Integer> colId;
    @FXML private TableColumn<Customer, String> colName, colContact, colPhone, colEmail;
    @FXML private Label statusLabel;

    private final ObservableList<Customer> list = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colId     .setCellValueFactory(new PropertyValueFactory<>("id"));
        colName   .setCellValueFactory(new PropertyValueFactory<>("name"));
        colContact.setCellValueFactory(new PropertyValueFactory<>("contactPerson"));
        colPhone  .setCellValueFactory(new PropertyValueFactory<>("phone"));
        colEmail  .setCellValueFactory(new PropertyValueFactory<>("email"));
        customersTable.setItems(list);
        customersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        loadData();
    }

    @FXML
    public void loadData() {
        new Thread(() -> {
            try {
                Response r = ClientContext.getInstance()
                        .send(new Request.Builder(Action.GET_ALL_CUSTOMERS).build());
                Platform.runLater(() -> {
                    if (r.isSuccess() && r.getData() instanceof List<?> l) {
                        list.setAll((List<Customer>) l);
                        statusLabel.setText("Покупателей: " + list.size());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Ошибка: " + e.getMessage()));
            }
        }).start();
    }

    @FXML
    public void handleAdd() {
        showDialog(null).ifPresent(c -> new Thread(() -> {
            try {
                Response r = ClientContext.getInstance()
                        .send(new Request.Builder(Action.CREATE_CUSTOMER).payload(c).build());
                Platform.runLater(() -> { if (r.isSuccess()) loadData(); });
            } catch (Exception ignored) {}
        }).start());
    }

    @FXML
    public void handleEdit() {
        Customer sel = customersTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        showDialog(sel).ifPresent(c -> {
            c.setId(sel.getId());
            new Thread(() -> {
                try {
                    Response r = ClientContext.getInstance()
                            .send(new Request.Builder(Action.UPDATE_CUSTOMER).payload(c).build());
                    Platform.runLater(() -> { if (r.isSuccess()) loadData(); });
                } catch (Exception ignored) {}
            }).start();
        });
    }

    @FXML
    public void handleDelete() {
        Customer sel = customersTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        new Thread(() -> {
            try {
                Response r = ClientContext.getInstance()
                        .send(new Request.Builder(Action.DELETE_CUSTOMER).param("id", sel.getId()).build());
                Platform.runLater(() -> { if (r.isSuccess()) loadData(); });
            } catch (Exception ignored) {}
        }).start();
    }

    private Optional<Customer> showDialog(Customer e) {
        Dialog<Customer> d = new Dialog<>();
        d.setTitle(e == null ? "Добавить покупателя" : "Редактировать");
        ButtonType save = new ButtonType("Сохранить", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        GridPane g = new GridPane();
        g.setHgap(10); g.setVgap(10); g.setPadding(new Insets(16));
        TextField fn = new TextField(e != null ? e.getName() : ""),
                fc = new TextField(e != null && e.getContactPerson() != null ? e.getContactPerson() : ""),
                fp = new TextField(e != null && e.getPhone() != null ? e.getPhone() : ""),
                fe = new TextField(e != null && e.getEmail() != null ? e.getEmail() : "");
        g.add(new Label("Название*:"), 0, 0); g.add(fn, 1, 0);
        g.add(new Label("Контакт:"),   0, 1); g.add(fc, 1, 1);
        g.add(new Label("Телефон:"),   0, 2); g.add(fp, 1, 2);
        g.add(new Label("Email:"),     0, 3); g.add(fe, 1, 3);
        d.getDialogPane().setContent(g);
        d.setResultConverter(btn -> {
            if (btn != save || fn.getText().trim().isEmpty()) return null;
            Customer c = new Customer();
            c.setName(fn.getText().trim());
            c.setContactPerson(fc.getText().trim());
            c.setPhone(fp.getText().trim());
            c.setEmail(fe.getText().trim());
            return c;
        });
        return d.showAndWait();
    }
}