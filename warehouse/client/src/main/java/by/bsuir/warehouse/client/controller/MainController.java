package by.bsuir.warehouse.client.controller;

import by.bsuir.warehouse.client.network.ClientContext;
import by.bsuir.warehouse.common.model.Role;
import by.bsuir.warehouse.common.model.User;
import by.bsuir.warehouse.common.protocol.Action;
import by.bsuir.warehouse.common.protocol.Request;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Контроллер главного окна.
 * Управляет навигацией — загружает нужный FXML в contentPane.
 * Скрывает кнопки недоступные для роли текущего пользователя.
 */
public class MainController {

    private static final Logger log = Logger.getLogger(MainController.class.getName());

    @FXML private StackPane contentPane;
    @FXML private Label     userInfoLabel;

    // Навигационные кнопки
    @FXML private Button btnDashboard;
    @FXML private Button btnStock;
    @FXML private Button btnProducts;
    @FXML private Button btnWarehouses;
    @FXML private Button btnIncome;
    @FXML private Button btnOutcome;
    @FXML private Button btnTransfer;
    @FXML private Button btnInventory;
    @FXML private Button btnDocuments;
    @FXML private Button btnForecast;
    @FXML private Button btnSuppliers;
    @FXML private Button btnCustomers;
    @FXML private Button btnUsers;

    private Button activeButton;

    @FXML
    public void initialize() {
        User user = ClientContext.getInstance().getCurrentUser();
        if (user != null) {
            userInfoLabel.setText(user.getFullName() + "\n" + user.getRole().getRoleName());
        }
        applyRoleRestrictions(user);
        showDashboard(); // стартовый экран
    }

    // ── Навигация ────────────────────────────────────────────────────────

    @FXML public void showDashboard()  { loadView("Dashboard.fxml",  btnDashboard); }
    @FXML public void showStock()      { loadView("Stock.fxml",       btnStock); }
    @FXML public void showProducts()   { loadView("Products.fxml",    btnProducts); }
    @FXML public void showWarehouses() { loadView("Warehouses.fxml",  btnWarehouses); }
    @FXML public void showIncome()     { loadView("Income.fxml",      btnIncome); }
    @FXML public void showOutcome()    { loadView("Outcome.fxml",     btnOutcome); }
    @FXML public void showTransfer()   { loadView("Transfer.fxml",    btnTransfer); }
    @FXML public void showInventory()  { loadView("Inventory.fxml",   btnInventory); }
    @FXML public void showDocuments()  { loadView("Documents.fxml",   btnDocuments); }
    @FXML public void showForecast()   { loadView("Forecast.fxml",    btnForecast); }
    @FXML public void showSuppliers()  { loadView("Suppliers.fxml",   btnSuppliers); }
    @FXML public void showCustomers()  { loadView("Customers.fxml",   btnCustomers); }
    @FXML public void showUsers()      { loadView("Users.fxml",       btnUsers); }

    @FXML
    public void handleLogout() {
        try {
            ClientContext ctx = ClientContext.getInstance();
            ctx.send(new Request.Builder(Action.LOGOUT)
                    .token(ctx.send(new Request.Builder(Action.LOGOUT).build()).getToken())
                    .build());
        } catch (Exception ignored) {}

        ClientContext.getInstance().disconnect();

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/by/bsuir/warehouse/client/fxml/Login.fxml"));
            Stage stage = new Stage();
            Scene scene = new Scene(loader.load(), 420, 320);
            scene.getStylesheets().add(
                    getClass().getResource("/by/bsuir/warehouse/client/css/style.css")
                              .toExternalForm());
            stage.setTitle("Складской учёт — Вход");
            stage.setScene(scene);
            stage.setResizable(false);
            stage.show();

            Stage current = (Stage) contentPane.getScene().getWindow();
            current.close();
        } catch (IOException e) {
            showError("Ошибка при выходе: " + e.getMessage());
        }
    }

    // ── Вспомогательные методы ───────────────────────────────────────────

    private void loadView(String fxmlName, Button navButton) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/by/bsuir/warehouse/client/fxml/" + fxmlName));
            Node view = loader.load();
            contentPane.getChildren().setAll(view);

            // Выделяем активную кнопку
            if (activeButton != null) {
                activeButton.getStyleClass().remove("nav-button-active");
            }
            if (navButton != null) {
                navButton.getStyleClass().add("nav-button-active");
                activeButton = navButton;
            }
        } catch (IOException e) {
            log.severe("Ошибка загрузки " + fxmlName + ": " + e.getMessage());
            showError("Не удалось открыть экран: " + fxmlName);
        }
    }

    /**
     * Скрывает кнопки навигации недоступные для роли пользователя.
     */
    private void applyRoleRestrictions(User user) {
        if (user == null) return;
        String role = user.getRole().getRoleName();

        // Пользователи — только ADMIN
        btnUsers.setVisible(Role.ADMIN.equals(role));
        btnUsers.setManaged(Role.ADMIN.equals(role));

        // Приход — ADMIN, PURCHASE_MANAGER, WAREHOUSE_WORKER
        boolean canIncome = Role.ADMIN.equals(role)
                || Role.PURCHASE_MANAGER.equals(role)
                || Role.WAREHOUSE_WORKER.equals(role);
        btnIncome.setVisible(canIncome);
        btnIncome.setManaged(canIncome);

        // Расход — ADMIN, SALES_MANAGER, WAREHOUSE_WORKER
        boolean canOutcome = Role.ADMIN.equals(role)
                || Role.SALES_MANAGER.equals(role)
                || Role.WAREHOUSE_WORKER.equals(role);
        btnOutcome.setVisible(canOutcome);
        btnOutcome.setManaged(canOutcome);

        // Перемещение и инвентаризация — ADMIN, WAREHOUSE_WORKER
        boolean canTransfer = Role.ADMIN.equals(role) || Role.WAREHOUSE_WORKER.equals(role);
        btnTransfer.setVisible(canTransfer);
        btnTransfer.setManaged(canTransfer);
        btnInventory.setVisible(canTransfer);
        btnInventory.setManaged(canTransfer);

        // Поставщики — ADMIN, PURCHASE_MANAGER
        boolean canSuppliers = Role.ADMIN.equals(role) || Role.PURCHASE_MANAGER.equals(role);
        btnSuppliers.setVisible(canSuppliers);
        btnSuppliers.setManaged(canSuppliers);
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg);
        alert.setHeaderText("Ошибка");
        alert.showAndWait();
    }
}
