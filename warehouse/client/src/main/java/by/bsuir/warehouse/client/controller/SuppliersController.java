package by.bsuir.warehouse.client.controller;

import by.bsuir.warehouse.client.network.ClientContext;
import by.bsuir.warehouse.common.model.Supplier;
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

public class SuppliersController {

    @FXML private TableView<Supplier> suppliersTable;
    @FXML private TableColumn<Supplier, Integer> colId;
    @FXML private TableColumn<Supplier, String> colName, colContact, colPhone, colEmail, colInn;
    @FXML private Label statusLabel;

    private final ObservableList<Supplier> list = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colId     .setCellValueFactory(new PropertyValueFactory<>("id"));
        colName   .setCellValueFactory(new PropertyValueFactory<>("name"));
        colContact.setCellValueFactory(new PropertyValueFactory<>("contactPerson"));
        colPhone  .setCellValueFactory(new PropertyValueFactory<>("phone"));
        colEmail  .setCellValueFactory(new PropertyValueFactory<>("email"));
        colInn    .setCellValueFactory(new PropertyValueFactory<>("inn"));
        suppliersTable.setItems(list);
        suppliersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        loadData();
    }

    @FXML
    public void loadData() {
        new Thread(() -> {
            try {
                Response r = ClientContext.getInstance()
                        .send(new Request.Builder(Action.GET_ALL_SUPPLIERS).build());
                Platform.runLater(() -> {
                    if (r.isSuccess() && r.getData() instanceof List<?> l) {
                        list.setAll((List<Supplier>) l);
                        statusLabel.setText("Поставщиков: " + list.size());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Ошибка: " + e.getMessage()));
            }
        }).start();
    }

    @FXML
    public void handleAdd() {
        showDialog(null).ifPresent(s -> new Thread(() -> {
            try {
                Response r = ClientContext.getInstance()
                        .send(new Request.Builder(Action.CREATE_SUPPLIER).payload(s).build());
                Platform.runLater(() -> {
                    if (r.isSuccess()) loadData();
                    else new Alert(Alert.AlertType.ERROR, r.getMessage()).showAndWait();
                });
            } catch (Exception e) {
                Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait());
            }
        }).start());
    }

    @FXML
    public void handleEdit() {
        Supplier sel = suppliersTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        showDialog(sel).ifPresent(s -> {
            s.setId(sel.getId());
            new Thread(() -> {
                try {
                    Response r = ClientContext.getInstance()
                            .send(new Request.Builder(Action.UPDATE_SUPPLIER).payload(s).build());
                    Platform.runLater(() -> {
                        if (r.isSuccess()) loadData();
                        else new Alert(Alert.AlertType.ERROR, r.getMessage()).showAndWait();
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait());
                }
            }).start();
        });
    }

    @FXML
    public void handleDelete() {
        Supplier sel = suppliersTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        new Thread(() -> {
            try {
                Response r = ClientContext.getInstance()
                        .send(new Request.Builder(Action.DELETE_SUPPLIER).param("id", sel.getId()).build());
                Platform.runLater(() -> { if (r.isSuccess()) loadData(); });
            } catch (Exception ignored) {}
        }).start();
    }

    private Optional<Supplier> showDialog(Supplier e) {
        Dialog<Supplier> d = new Dialog<>();
        d.setTitle(e == null ? "Добавить поставщика" : "Редактировать");
        ButtonType save = new ButtonType("Сохранить", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        GridPane g = new GridPane();
        g.setHgap(10); g.setVgap(10); g.setPadding(new Insets(16));
        TextField fn = new TextField(e != null ? e.getName() : ""),
                fc = new TextField(e != null && e.getContactPerson() != null ? e.getContactPerson() : ""),
                fp = new TextField(e != null && e.getPhone() != null ? e.getPhone() : ""),
                fe = new TextField(e != null && e.getEmail() != null ? e.getEmail() : ""),
                fi = new TextField(e != null && e.getInn()   != null ? e.getInn()   : "");
        g.add(new Label("Название*:"), 0, 0); g.add(fn, 1, 0);
        g.add(new Label("Контакт:"),   0, 1); g.add(fc, 1, 1);
        g.add(new Label("Телефон:"),   0, 2); g.add(fp, 1, 2);
        g.add(new Label("Email:"),     0, 3); g.add(fe, 1, 3);
        g.add(new Label("УНП:"),       0, 4); g.add(fi, 1, 4);
        d.getDialogPane().setContent(g);
        d.setResultConverter(btn -> {
            if (btn != save || fn.getText().trim().isEmpty()) return null;
            Supplier s = new Supplier();
            s.setName(fn.getText().trim());
            s.setContactPerson(fc.getText().trim());
            s.setPhone(fp.getText().trim());
            s.setEmail(fe.getText().trim());
            s.setInn(fi.getText().trim());
            return s;
        });
        return d.showAndWait();
    }
}