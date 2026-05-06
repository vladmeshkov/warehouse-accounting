package by.bsuir.warehouse.client.controller;

import by.bsuir.warehouse.client.network.ClientContext;
import by.bsuir.warehouse.common.model.Role;
import by.bsuir.warehouse.common.model.User;
import by.bsuir.warehouse.common.protocol.Action;
import by.bsuir.warehouse.common.protocol.Request;
import by.bsuir.warehouse.common.protocol.Response;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.logging.Logger;

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
    @FXML private Button btnTurnover;
    @FXML private Button btnSalesReport;
    @FXML private Button btnAnalytics;
    @FXML private Button btnUsers;
    @FXML private Button btnRequests;
    @FXML private Button btnProfile;
    @FXML private Button btnAudit;
    @FXML private Button btnBackup;

    private Button activeButton;

    @FXML
    public void initialize() {
        User user = ClientContext.getInstance().getCurrentUser();
        if (user != null) {
            userInfoLabel.setText(user.getFullName() + "\n" + user.getRole().getRoleName());
        }
        applyRoleRestrictions(user);
        showDashboard();
    }

    @FXML public void showDashboard()      { loadView("Dashboard.fxml",            btnDashboard); }
    @FXML public void showStock()          { loadView("Stock.fxml",                 btnStock); }
    @FXML public void showProducts()       { loadView("Products.fxml",              btnProducts); }
    @FXML public void showWarehouses()     { loadView("Warehouses.fxml",            btnWarehouses); }
    @FXML public void showIncome()         { loadView("Income.fxml",                btnIncome); }
    @FXML public void showOutcome()        { loadView("Outcome.fxml",               btnOutcome); }
    @FXML public void showTransfer()       { loadView("Transfer.fxml",              btnTransfer); }
    @FXML public void showInventory()      { loadView("Inventory.fxml",             btnInventory); }
    @FXML public void showDocuments()      { loadView("Documents.fxml",             btnDocuments); }
    @FXML public void showForecast()       { loadView("Forecast.fxml",              btnForecast); }
    @FXML public void showSuppliers()      { loadView("Suppliers.fxml",             btnSuppliers); }
    @FXML public void showCustomers()      { loadView("Customers.fxml",             btnCustomers); }
    @FXML public void showTurnoverReport() { loadView("TurnoverReport.fxml",        btnTurnover); }
    @FXML public void showSalesReport()    { loadView("SalesReport.fxml",           btnSalesReport); }
    @FXML public void showAnalytics()      { loadView("Analytics.fxml",             btnAnalytics); }
    @FXML public void showUsers()          { loadView("Users.fxml",                 btnUsers); }
    @FXML public void showRequests()       { loadView("RegistrationRequests.fxml",  btnRequests); }
    @FXML public void showProfile()        { loadView("Profile.fxml",               btnProfile); }
    @FXML public void showAudit()          { loadView("Audit.fxml",                 btnAudit); }

    @FXML
    public void handleBackup() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Создать резервную копию базы данных?", ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Подтверждение");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                new Thread(() -> {
                    try {
                        Response r = ClientContext.getInstance()
                                .send(new Request.Builder(Action.BACKUP_DATABASE).build());
                        Platform.runLater(() -> {
                            if (r.isSuccess()) {
                                new Alert(Alert.AlertType.INFORMATION, r.getMessage(), ButtonType.OK).showAndWait();
                            } else {
                                new Alert(Alert.AlertType.ERROR, r.getMessage(), ButtonType.OK).showAndWait();
                            }
                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait());
                    }
                }).start();
            }
        });
    }

    @FXML
    public void handleLogout() {
        try {
            ClientContext ctx = ClientContext.getInstance();
            ctx.send(new Request.Builder(Action.LOGOUT).build());
        } catch (Exception ignored) {}

        ClientContext.getInstance().disconnect();

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/by/bsuir/warehouse/client/fxml/Login.fxml"));
            Stage stage = new Stage();
            Scene scene = new Scene(loader.load(), 400, 520);
            scene.getStylesheets().add(
                    getClass().getResource("/by/bsuir/warehouse/client/css/login-style.css").toExternalForm());
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

    private void loadView(String fxmlName, Button navButton) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/by/bsuir/warehouse/client/fxml/" + fxmlName));
            Node view = loader.load();
            contentPane.getChildren().setAll(view);

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

    private void applyRoleRestrictions(User user) {
        if (user == null) return;
        String role = user.getRole().getRoleName();

        boolean isAdmin = Role.ADMIN.equals(role);

        // Админ не видит складские операции
        btnIncome.setVisible(!isAdmin);
        btnIncome.setManaged(!isAdmin);
        btnOutcome.setVisible(!isAdmin);
        btnOutcome.setManaged(!isAdmin);
        btnTransfer.setVisible(!isAdmin);
        btnTransfer.setManaged(!isAdmin);
        btnInventory.setVisible(!isAdmin);
        btnInventory.setManaged(!isAdmin);

        // Пользователи и заявки – только ADMIN
        btnUsers.setVisible(isAdmin);
        btnUsers.setManaged(isAdmin);
        btnRequests.setVisible(isAdmin);
        btnRequests.setManaged(isAdmin);
        btnAudit.setVisible(isAdmin);
        btnAudit.setManaged(isAdmin);
        btnBackup.setVisible(isAdmin);
        btnBackup.setManaged(isAdmin);

        // Приход – PURCHASE_MANAGER, WAREHOUSE_WORKER (админу скрыто выше)
        boolean canIncome = Role.PURCHASE_MANAGER.equals(role) || Role.WAREHOUSE_WORKER.equals(role);
        btnIncome.setVisible(canIncome);
        btnIncome.setManaged(canIncome);

        // Расход – SALES_MANAGER, WAREHOUSE_WORKER
        boolean canOutcome = Role.SALES_MANAGER.equals(role) || Role.WAREHOUSE_WORKER.equals(role);
        btnOutcome.setVisible(canOutcome);
        btnOutcome.setManaged(canOutcome);

        // Перемещение, Инвентаризация – только WAREHOUSE_WORKER
        boolean canTransfer = Role.WAREHOUSE_WORKER.equals(role);
        btnTransfer.setVisible(canTransfer);
        btnTransfer.setManaged(canTransfer);
        btnInventory.setVisible(canTransfer);
        btnInventory.setManaged(canTransfer);

        // Товары, Склады – видны всем, кроме бухгалтера (просмотр)
        boolean canViewCatalog = !Role.ACCOUNTANT.equals(role);
        btnProducts.setVisible(canViewCatalog);
        btnProducts.setManaged(canViewCatalog);
        btnWarehouses.setVisible(canViewCatalog);
        btnWarehouses.setManaged(canViewCatalog);

        // Поставщики – ADMIN, PURCHASE_MANAGER, WAREHOUSE_WORKER
        boolean canSuppliers = Role.ADMIN.equals(role) || Role.PURCHASE_MANAGER.equals(role) || Role.WAREHOUSE_WORKER.equals(role);
        btnSuppliers.setVisible(canSuppliers);
        btnSuppliers.setManaged(canSuppliers);

        // Покупатели – ADMIN, SALES_MANAGER, WAREHOUSE_WORKER
        boolean canCustomers = Role.ADMIN.equals(role) || Role.SALES_MANAGER.equals(role) || Role.WAREHOUSE_WORKER.equals(role);
        btnCustomers.setVisible(canCustomers);
        btnCustomers.setManaged(canCustomers);

        // Отчёт по оборачиваемости – ADMIN, PURCHASE_MANAGER
        boolean canViewTurnover = Role.ADMIN.equals(role) || Role.PURCHASE_MANAGER.equals(role);
        btnTurnover.setVisible(canViewTurnover);
        btnTurnover.setManaged(canViewTurnover);

        // Отчёт по продажам – ADMIN, ACCOUNTANT
        boolean canViewSales = Role.ADMIN.equals(role) || Role.ACCOUNTANT.equals(role);
        btnSalesReport.setVisible(canViewSales);
        btnSalesReport.setManaged(canViewSales);

        // Аналитика, личный кабинет – всем
        btnAnalytics.setVisible(true);
        btnAnalytics.setManaged(true);
        btnProfile.setVisible(true);
        btnProfile.setManaged(true);

        // Документы – всем
        btnDocuments.setVisible(true);
        btnDocuments.setManaged(true);

        // Остатки и прогноз – всем
        btnStock.setVisible(true);
        btnStock.setManaged(true);
        btnForecast.setVisible(true);
        btnForecast.setManaged(true);

        // Главная – всем
        btnDashboard.setVisible(true);
        btnDashboard.setManaged(true);
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg);
        alert.setHeaderText("Ошибка");
        alert.showAndWait();
    }
}