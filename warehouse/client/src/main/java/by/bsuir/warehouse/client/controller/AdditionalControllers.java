package by.bsuir.warehouse.client.controller;

import by.bsuir.warehouse.client.network.ClientContext;
import by.bsuir.warehouse.common.model.*;
import by.bsuir.warehouse.common.protocol.Action;
import by.bsuir.warehouse.common.protocol.Request;
import by.bsuir.warehouse.common.protocol.Response;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.*;

// ═══════════════════════════════════════════════════════════════════════════════
// TransferController
// ═══════════════════════════════════════════════════════════════════════════════
class TransferController {
    @FXML ComboBox<Warehouse> fromWarehouseCombo, toWarehouseCombo;
    @FXML TableView<DocumentItem> itemsTable;
    @FXML TableColumn<DocumentItem,String> colArticle,colProduct,colUnit,colQty;
    @FXML Label statusLabel;

    private final ObservableList<DocumentItem> items = FXCollections.observableArrayList();
    private List<Product> allProducts = new ArrayList<>();

    @FXML public void initialize() {
        colArticle.setCellValueFactory(c->new SimpleStringProperty(c.getValue().getProduct()!=null?c.getValue().getProduct().getArticle():""));
        colProduct.setCellValueFactory(c->new SimpleStringProperty(c.getValue().getProduct()!=null?c.getValue().getProduct().getName():""));
        colUnit.setCellValueFactory(c->new SimpleStringProperty(c.getValue().getProduct()!=null?c.getValue().getProduct().getUnit():""));
        colQty.setCellValueFactory(c->new SimpleStringProperty(c.getValue().getQuantity()!=null?c.getValue().getQuantity().toPlainString():""));
        itemsTable.setItems(items);
        new Thread(() -> {
            try {
                Response wh = ClientContext.getInstance().send(new Request.Builder(Action.GET_ALL_WAREHOUSES).build());
                Response pr = ClientContext.getInstance().send(new Request.Builder(Action.GET_ALL_PRODUCTS).build());
                Platform.runLater(() -> {
                    if (wh.isSuccess() && wh.getData() instanceof List<?> l) {
                        fromWarehouseCombo.getItems().setAll((List<Warehouse>) l);
                        toWarehouseCombo.getItems().setAll((List<Warehouse>) l);
                    }
                    if (pr.isSuccess() && pr.getData() instanceof List<?> l) allProducts = (List<Product>) l;
                });
            } catch (Exception e) { Platform.runLater(() -> statusLabel.setText("Ошибка: " + e.getMessage())); }
        }) {{ setDaemon(true); start(); }};
    }

    @FXML public void addItem() {
        if (allProducts.isEmpty()) return;
        Dialog<DocumentItem> d = new Dialog<>(); d.setTitle("Добавить позицию");
        ButtonType add = new ButtonType("Добавить", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(add, ButtonType.CANCEL);
        GridPane g = new GridPane(); g.setHgap(10); g.setVgap(10); g.setPadding(new Insets(16));
        ComboBox<Product> pc = new ComboBox<>(FXCollections.observableArrayList(allProducts));
        pc.setConverter(new javafx.util.StringConverter<>() {
            public String toString(Product p) { return p == null ? "" : "[" + p.getArticle() + "] " + p.getName(); }
            public Product fromString(String s) { return null; }
        });
        TextField qf = new TextField("1");
        g.add(new Label("Товар*:"), 0, 0); g.add(pc, 1, 0);
        g.add(new Label("Кол-во*:"), 0, 1); g.add(qf, 1, 1);
        d.getDialogPane().setContent(g);
        d.setResultConverter(btn -> {
            if (btn != add || pc.getValue() == null) return null;
            try { return new DocumentItem(pc.getValue(), new BigDecimal(qf.getText().trim()), null); }
            catch (Exception e) { return null; }
        });
        d.showAndWait().ifPresent(items::add);
    }

    @FXML public void removeItem() {
        DocumentItem s = itemsTable.getSelectionModel().getSelectedItem();
        if (s != null) items.remove(s);
    }

    @FXML public void handleSubmit() {
        if (fromWarehouseCombo.getValue() == null || toWarehouseCombo.getValue() == null) {
            statusLabel.setText("Выберите склады"); return;
        }
        if (items.isEmpty()) { statusLabel.setText("Добавьте позиции"); return; }
        Document doc = new Document(DocumentType.TRANSFER, "AUTO", null);
        doc.setWarehouseFrom(fromWarehouseCombo.getValue());
        doc.setWarehouseTo(toWarehouseCombo.getValue());
        doc.setItems(new ArrayList<>(items));
        new Thread(() -> {
            try {
                Response r = ClientContext.getInstance().send(
                        new Request.Builder(Action.PROCESS_TRANSFER).payload(doc).build());
                Platform.runLater(() -> {
                    if (r.isSuccess()) {
                        new Alert(Alert.AlertType.INFORMATION, r.getMessage(), ButtonType.OK).showAndWait();
                        items.clear(); statusLabel.setText("");
                    } else statusLabel.setText("Ошибка: " + r.getMessage());
                });
            } catch (Exception e) { Platform.runLater(() -> statusLabel.setText("Ошибка: " + e.getMessage())); }
        }) {{ setDaemon(true); start(); }};
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// InventoryRequest — локальный DTO для передачи данных инвентаризации на сервер
// (дублирует RequestDispatcher.InventoryRequest, но в пакете клиента)
// ═══════════════════════════════════════════════════════════════════════════════
class InventoryRequest implements Serializable {
    public Warehouse warehouse;
    public Map<Integer, Integer> actualQuantities;

    public InventoryRequest(Warehouse warehouse, Map<Integer, Integer> actualQuantities) {
        this.warehouse = warehouse;
        this.actualQuantities = actualQuantities;
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// InventoryResult — локальный DTO для разбора ответа сервера
// ═══════════════════════════════════════════════════════════════════════════════
class InventoryResult implements Serializable {
    public Document document;
    public int totalChecked;
    public int totalDiscrepancies;
}

// ═══════════════════════════════════════════════════════════════════════════════
// InventoryFormController — editable table for actual quantities
// ═══════════════════════════════════════════════════════════════════════════════
class InventoryFormController {

    public static class InventoryRow {
        public int productId;
        public String article, name, unit;
        public int accounting;
        public int actual;
        public int diff;

        public InventoryRow(Stock s) {
            productId  = s.getProductId();
            article    = s.getProductArticle();
            name       = s.getProductName();
            unit       = s.getProductUnit();
            accounting = s.getQuantity();
            actual     = s.getQuantity();
            diff       = 0;
        }
    }

    @FXML ComboBox<Warehouse> warehouseCombo;
    @FXML TableView<InventoryRow> inventoryTable;
    @FXML TableColumn<InventoryRow, String>  colArticle, colProduct, colUnit, colDiff;
    @FXML TableColumn<InventoryRow, Integer> colAccounting, colActual;
    @FXML Label statusLabel;

    private final ObservableList<InventoryRow> rows = FXCollections.observableArrayList();

    @FXML public void initialize() {
        colArticle   .setCellValueFactory(c -> new SimpleStringProperty(c.getValue().article));
        colProduct   .setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name));
        colUnit      .setCellValueFactory(c -> new SimpleStringProperty(c.getValue().unit));
        colAccounting.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().accounting).asObject());
        colDiff      .setCellValueFactory(c -> {
            int d = c.getValue().diff;
            return new SimpleStringProperty(d == 0 ? "—" : d > 0 ? "+" + d : String.valueOf(d));
        });

        colActual.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().actual).asObject());
        colActual.setCellFactory(col -> new TableCell<>() {
            private final TextField tf = new TextField();
            {
                tf.setOnAction(e -> commitEdit(parse(tf.getText())));
                tf.focusedProperty().addListener((o, ov, nv) -> { if (!nv) commitEdit(parse(tf.getText())); });
            }
            @Override protected void updateItem(Integer v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setGraphic(null); } else { tf.setText(v != null ? v.toString() : "0"); setGraphic(tf); }
            }
            @Override public void commitEdit(Integer v) {
                super.commitEdit(v);
                InventoryRow row = getTableView().getItems().get(getIndex());
                row.actual = v; row.diff = v - row.accounting;
                inventoryTable.refresh();
            }
            private int parse(String s) {
                try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 0; }
            }
        });

        colActual.setEditable(true);
        inventoryTable.setEditable(true);
        inventoryTable.setItems(rows);

        new Thread(() -> {
            try {
                Response r = ClientContext.getInstance().send(
                        new Request.Builder(Action.GET_ALL_WAREHOUSES).build());
                Platform.runLater(() -> {
                    if (r.isSuccess() && r.getData() instanceof List<?> l)
                        warehouseCombo.getItems().setAll((List<Warehouse>) l);
                });
            } catch (Exception e) { /* тихо игнорируем — UI покажет пустой список */ }
        }) {{ setDaemon(true); start(); }};
    }

    @FXML public void loadStockForWarehouse() {
        Warehouse wh = warehouseCombo.getValue();
        if (wh == null) return;
        new Thread(() -> {
            try {
                Response r = ClientContext.getInstance().send(
                        new Request.Builder(Action.GET_STOCK_BY_WAREHOUSE)
                                .param("warehouseId", wh.getId()).build());
                Platform.runLater(() -> {
                    rows.clear();
                    if (r.isSuccess() && r.getData() instanceof List<?> l)
                        for (Object o : l) rows.add(new InventoryRow((Stock) o));
                    statusLabel.setText("Загружено: " + rows.size() + " позиций");
                });
            } catch (Exception e) { Platform.runLater(() -> statusLabel.setText("Ошибка: " + e.getMessage())); }
        }) {{ setDaemon(true); start(); }};
    }

    @FXML public void handleSubmit() {
        if (warehouseCombo.getValue() == null) { statusLabel.setText("Выберите склад"); return; }
        if (rows.isEmpty()) { statusLabel.setText("Нет позиций"); return; }

        Map<Integer, Integer> actual = new HashMap<>();
        for (InventoryRow r : rows) actual.put(r.productId, r.actual);

        // Используем локальный DTO, а не серверный RequestDispatcher.InventoryRequest
        InventoryRequest req = new InventoryRequest(warehouseCombo.getValue(), actual);

        new Thread(() -> {
            try {
                Response r = ClientContext.getInstance().send(
                        new Request.Builder(Action.PROCESS_INVENTORY).payload(req).build());
                Platform.runLater(() -> {
                    if (r.isSuccess()) {
                        // Разбираем ответ через рефлексию / cast к нашему DTO
                        String msg;
                        if (r.getData() instanceof InventoryResult res) {
                            msg = "Инвентаризация проведена.\nПроверено: " + res.totalChecked
                                    + "\nРасхождений: " + res.totalDiscrepancies;
                        } else {
                            msg = r.getMessage();
                        }
                        new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK).showAndWait();
                        statusLabel.setText("");
                    } else {
                        statusLabel.setText("Ошибка: " + r.getMessage());
                    }
                });
            } catch (Exception e) { Platform.runLater(() -> statusLabel.setText("Ошибка: " + e.getMessage())); }
        }) {{ setDaemon(true); start(); }};
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SuppliersController
// ═══════════════════════════════════════════════════════════════════════════════
class SuppliersController {
    @FXML TableView<Supplier> suppliersTable;
    @FXML TableColumn<Supplier, Integer> colId;
    @FXML TableColumn<Supplier, String>  colName, colContact, colPhone, colEmail, colInn;
    @FXML Label statusLabel;
    private final ObservableList<Supplier> list = FXCollections.observableArrayList();

    @FXML public void initialize() {
        colId     .setCellValueFactory(new PropertyValueFactory<>("id"));
        colName   .setCellValueFactory(new PropertyValueFactory<>("name"));
        colContact.setCellValueFactory(new PropertyValueFactory<>("contactPerson"));
        colPhone  .setCellValueFactory(new PropertyValueFactory<>("phone"));
        colEmail  .setCellValueFactory(new PropertyValueFactory<>("email"));
        colInn    .setCellValueFactory(new PropertyValueFactory<>("inn"));
        suppliersTable.setItems(list);
        loadData();
    }

    @FXML public void loadData() {
        new Thread(() -> {
            try {
                Response r = ClientContext.getInstance().send(new Request.Builder(Action.GET_ALL_SUPPLIERS).build());
                Platform.runLater(() -> {
                    if (r.isSuccess() && r.getData() instanceof List<?> l) {
                        list.setAll((List<Supplier>) l);
                        statusLabel.setText("Поставщиков: " + list.size());
                    }
                });
            } catch (Exception e) { Platform.runLater(() -> statusLabel.setText("Ошибка: " + e.getMessage())); }
        }) {{ setDaemon(true); start(); }};
    }

    @FXML public void handleAdd() {
        showDialog(null).ifPresent(s -> new Thread(() -> {
            try {
                Response r = ClientContext.getInstance().send(new Request.Builder(Action.CREATE_SUPPLIER).payload(s).build());
                Platform.runLater(() -> { if (r.isSuccess()) loadData(); else new Alert(Alert.AlertType.ERROR, r.getMessage()).showAndWait(); });
            } catch (Exception e) { Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait()); }
        }) {{ setDaemon(true); start(); }});
    }

    @FXML public void handleEdit() {
        Supplier sel = suppliersTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        showDialog(sel).ifPresent(s -> {
            s.setId(sel.getId());
            new Thread(() -> {
                try {
                    Response r = ClientContext.getInstance().send(new Request.Builder(Action.UPDATE_SUPPLIER).payload(s).build());
                    Platform.runLater(() -> { if (r.isSuccess()) loadData(); else new Alert(Alert.AlertType.ERROR, r.getMessage()).showAndWait(); });
                } catch (Exception e) { Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait()); }
            }) {{ setDaemon(true); start(); }};
        });
    }

    @FXML public void handleDelete() {
        Supplier sel = suppliersTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        new Thread(() -> {
            try {
                Response r = ClientContext.getInstance().send(new Request.Builder(Action.DELETE_SUPPLIER).param("id", sel.getId()).build());
                Platform.runLater(() -> { if (r.isSuccess()) loadData(); });
            } catch (Exception e) { /* тихо */ }
        }) {{ setDaemon(true); start(); }};
    }

    private Optional<Supplier> showDialog(Supplier e) {
        Dialog<Supplier> d = new Dialog<>();
        d.setTitle(e == null ? "Добавить поставщика" : "Редактировать");
        ButtonType save = new ButtonType("Сохранить", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        GridPane g = new GridPane(); g.setHgap(10); g.setVgap(10); g.setPadding(new Insets(16));
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
            s.setName(fn.getText().trim()); s.setContactPerson(fc.getText().trim());
            s.setPhone(fp.getText().trim()); s.setEmail(fe.getText().trim()); s.setInn(fi.getText().trim());
            return s;
        });
        return d.showAndWait();
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// CustomersController
// ═══════════════════════════════════════════════════════════════════════════════
class CustomersController {
    @FXML TableView<Customer> customersTable;
    @FXML TableColumn<Customer, Integer> colId;
    @FXML TableColumn<Customer, String>  colName, colContact, colPhone, colEmail;
    @FXML Label statusLabel;
    private final ObservableList<Customer> list = FXCollections.observableArrayList();

    @FXML public void initialize() {
        colId     .setCellValueFactory(new PropertyValueFactory<>("id"));
        colName   .setCellValueFactory(new PropertyValueFactory<>("name"));
        colContact.setCellValueFactory(new PropertyValueFactory<>("contactPerson"));
        colPhone  .setCellValueFactory(new PropertyValueFactory<>("phone"));
        colEmail  .setCellValueFactory(new PropertyValueFactory<>("email"));
        customersTable.setItems(list);
        loadData();
    }

    @FXML public void loadData() {
        new Thread(() -> {
            try {
                Response r = ClientContext.getInstance().send(new Request.Builder(Action.GET_ALL_CUSTOMERS).build());
                Platform.runLater(() -> {
                    if (r.isSuccess() && r.getData() instanceof List<?> l) {
                        list.setAll((List<Customer>) l);
                        statusLabel.setText("Покупателей: " + list.size());
                    }
                });
            } catch (Exception e) { Platform.runLater(() -> statusLabel.setText("Ошибка: " + e.getMessage())); }
        }) {{ setDaemon(true); start(); }};
    }

    @FXML public void handleAdd() {
        showDialog(null).ifPresent(c -> new Thread(() -> {
            try {
                Response r = ClientContext.getInstance().send(new Request.Builder(Action.CREATE_CUSTOMER).payload(c).build());
                Platform.runLater(() -> { if (r.isSuccess()) loadData(); });
            } catch (Exception e) { /* тихо */ }
        }) {{ setDaemon(true); start(); }});
    }

    @FXML public void handleEdit() {
        Customer sel = customersTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        showDialog(sel).ifPresent(c -> {
            c.setId(sel.getId());
            new Thread(() -> {
                try {
                    Response r = ClientContext.getInstance().send(new Request.Builder(Action.UPDATE_CUSTOMER).payload(c).build());
                    Platform.runLater(() -> { if (r.isSuccess()) loadData(); });
                } catch (Exception e) { /* тихо */ }
            }) {{ setDaemon(true); start(); }};
        });
    }

    @FXML public void handleDelete() {
        Customer sel = customersTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        new Thread(() -> {
            try {
                Response r = ClientContext.getInstance().send(new Request.Builder(Action.DELETE_CUSTOMER).param("id", sel.getId()).build());
                Platform.runLater(() -> { if (r.isSuccess()) loadData(); });
            } catch (Exception e) { /* тихо */ }
        }) {{ setDaemon(true); start(); }};
    }

    private Optional<Customer> showDialog(Customer e) {
        Dialog<Customer> d = new Dialog<>();
        d.setTitle(e == null ? "Добавить покупателя" : "Редактировать");
        ButtonType save = new ButtonType("Сохранить", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        GridPane g = new GridPane(); g.setHgap(10); g.setVgap(10); g.setPadding(new Insets(16));
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
            c.setName(fn.getText().trim()); c.setContactPerson(fc.getText().trim());
            c.setPhone(fp.getText().trim()); c.setEmail(fe.getText().trim());
            return c;
        });
        return d.showAndWait();
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// UsersController — ADMIN only
// ═══════════════════════════════════════════════════════════════════════════════
class UsersController {
    @FXML TableView<User> usersTable;
    @FXML TableColumn<User, Integer> colId;
    @FXML TableColumn<User, String>  colUsername, colFullName, colRole, colPhone, colEmail, colActive;
    @FXML Label statusLabel;
    private final ObservableList<User> list = FXCollections.observableArrayList();
    private List<Role> allRoles = new ArrayList<>();

    @FXML public void initialize() {
        colId      .setCellValueFactory(new PropertyValueFactory<>("id"));
        colUsername.setCellValueFactory(new PropertyValueFactory<>("username"));
        colFullName.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        colRole    .setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getRole() != null ? c.getValue().getRole().getRoleName() : "—"));
        colPhone   .setCellValueFactory(new PropertyValueFactory<>("phone"));
        colEmail   .setCellValueFactory(new PropertyValueFactory<>("email"));
        colActive  .setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isActive() ? "✓ Да" : "✗ Нет"));
        usersTable.setItems(list);

        new Thread(() -> {
            try {
                Response r = ClientContext.getInstance().send(new Request.Builder(Action.GET_ALL_ROLES).build());
                Platform.runLater(() -> { if (r.isSuccess() && r.getData() instanceof List<?> l) allRoles = (List<Role>) l; });
            } catch (Exception e) { /* тихо */ }
        }) {{ setDaemon(true); start(); }};

        loadData();
    }

    @FXML public void loadData() {
        new Thread(() -> {
            try {
                Response r = ClientContext.getInstance().send(new Request.Builder(Action.GET_ALL_USERS).build());
                Platform.runLater(() -> {
                    if (r.isSuccess() && r.getData() instanceof List<?> l) {
                        list.setAll((List<User>) l);
                        statusLabel.setText("Пользователей: " + list.size());
                    }
                });
            } catch (Exception e) { Platform.runLater(() -> statusLabel.setText("Ошибка: " + e.getMessage())); }
        }) {{ setDaemon(true); start(); }};
    }

    @FXML public void handleAdd() {
        showDialog(null).ifPresent(u -> new Thread(() -> {
            try {
                Response r = ClientContext.getInstance().send(new Request.Builder(Action.CREATE_USER).payload(u).build());
                Platform.runLater(() -> { if (r.isSuccess()) loadData(); else new Alert(Alert.AlertType.ERROR, r.getMessage()).showAndWait(); });
            } catch (Exception e) { Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait()); }
        }) {{ setDaemon(true); start(); }});
    }

    @FXML public void handleEdit() {
        User sel = usersTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        showDialog(sel).ifPresent(u -> {
            u.setId(sel.getId());
            new Thread(() -> {
                try {
                    Response r = ClientContext.getInstance().send(new Request.Builder(Action.UPDATE_USER).payload(u).build());
                    Platform.runLater(() -> { if (r.isSuccess()) loadData(); });
                } catch (Exception e) { /* тихо */ }
            }) {{ setDaemon(true); start(); }};
        });
    }

    @FXML public void handleDeactivate() {
        User sel = usersTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        boolean newState = !sel.isActive();
        new Thread(() -> {
            try {
                Response r = ClientContext.getInstance().send(
                        new Request.Builder(Action.DEACTIVATE_USER)
                                .param("id", sel.getId())
                                .param("active", String.valueOf(newState)).build());
                Platform.runLater(() -> { if (r.isSuccess()) loadData(); });
            } catch (Exception e) { /* тихо */ }
        }) {{ setDaemon(true); start(); }};
    }

    private Optional<User> showDialog(User e) {
        Dialog<User> d = new Dialog<>();
        d.setTitle(e == null ? "Добавить пользователя" : "Редактировать");
        ButtonType save = new ButtonType("Сохранить", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        GridPane g = new GridPane(); g.setHgap(10); g.setVgap(10); g.setPadding(new Insets(16));
        TextField fu = new TextField(e != null ? e.getUsername() : ""),
                ff = new TextField(e != null && e.getFullName() != null ? e.getFullName() : ""),
                fp = new TextField(e != null && e.getPhone()    != null ? e.getPhone()    : ""),
                fe = new TextField(e != null && e.getEmail()    != null ? e.getEmail()    : "");
        PasswordField fpw = new PasswordField();
        ComboBox<Role> rc = new ComboBox<>(FXCollections.observableArrayList(allRoles));
        rc.setConverter(new javafx.util.StringConverter<>() {
            public String toString(Role r) { return r == null ? "" : r.getRoleName(); }
            public Role fromString(String s) { return null; }
        });
        if (e != null) rc.setValue(e.getRole());
        g.add(new Label("Логин*:"),                      0, 0); g.add(fu,  1, 0);
        g.add(new Label("ФИО:"),                         0, 1); g.add(ff,  1, 1);
        g.add(new Label("Роль*:"),                       0, 2); g.add(rc,  1, 2);
        g.add(new Label(e == null ? "Пароль*:" : "Новый пароль:"), 0, 3); g.add(fpw, 1, 3);
        g.add(new Label("Телефон:"),                     0, 4); g.add(fp,  1, 4);
        g.add(new Label("Email:"),                       0, 5); g.add(fe,  1, 5);
        d.getDialogPane().setContent(g);
        d.setResultConverter(btn -> {
            if (btn != save || fu.getText().trim().isEmpty() || rc.getValue() == null) return null;
            if (e == null && fpw.getText().isEmpty()) return null;
            User u = new User();
            u.setUsername(fu.getText().trim()); u.setFullName(ff.getText().trim());
            u.setRole(rc.getValue()); u.setPhone(fp.getText().trim());
            u.setEmail(fe.getText().trim()); u.setActive(true);
            if (!fpw.getText().isEmpty())
                u.setPasswordHash(by.bsuir.warehouse.common.util.PasswordUtil.hash(fpw.getText()));
            else if (e != null)
                u.setPasswordHash(e.getPasswordHash());
            return u;
        });
        return d.showAndWait();
    }
}