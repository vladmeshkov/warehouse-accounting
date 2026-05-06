package by.bsuir.warehouse.server.network;

import by.bsuir.warehouse.common.model.*;
import by.bsuir.warehouse.common.protocol.*;
import by.bsuir.warehouse.common.util.PasswordUtil;
import by.bsuir.warehouse.server.config.DBConnection;
import by.bsuir.warehouse.server.config.ServerConfig;
import by.bsuir.warehouse.server.dao.*;
import by.bsuir.warehouse.server.dao.impl.*;
import by.bsuir.warehouse.server.service.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;

import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class RequestDispatcher {

    private static final Logger log = Logger.getLogger(RequestDispatcher.class.getName());

    private final AuthService      authService;
    private final InventoryService inventoryService;
    private final ForecastService  forecastService;
    private final ProductDAO       productDAO;
    private final WarehouseDAO     warehouseDAO;
    private final UserDAO          userDAO;
    private final SupplierDAO      supplierDAO;
    private final CustomerDAO      customerDAO;
    private final DocumentDAO      documentDAO;
    private final AuditDAO         auditDAO = new AuditDAOImpl();

    public RequestDispatcher(AuthService authService, ForecastService forecastService) {
        this.authService      = authService;
        this.inventoryService = new InventoryService();
        this.forecastService  = forecastService;
        this.productDAO       = new ProductDAOImpl();
        this.warehouseDAO     = new WarehouseDAOImpl();
        this.userDAO          = new UserDAOImpl();
        this.supplierDAO      = new SupplierDAOImpl();
        this.customerDAO      = new CustomerDAOImpl();
        this.documentDAO      = new DocumentDAOImpl();
    }

    public Response dispatch(Request request) {
        try {
            if (request.getAction() == Action.LOGIN) {
                return handleLogin(request);
            }
            if (request.getAction() == Action.REGISTER) {
                return handleRegister(request);
            }
            if (request.getAction() == Action.GET_ALL_ROLES) {
                return Response.ok((Serializable) new ArrayList<>(userDAO.findAllRoles()));
            }

            User user = authService.authenticate(request.getToken());

            return switch (request.getAction()) {
                case LOGOUT -> handleLogout(request, user);

                case GET_ALL_USERS     -> handleGetAllUsers(user);
                case GET_USER_BY_ID    -> handleGetUserById(request, user);
                case CREATE_USER       -> handleCreateUser(request, user);
                case UPDATE_USER       -> handleUpdateUser(request, user);
                case DEACTIVATE_USER   -> handleDeactivateUser(request, user);
                case DELETE_USER       -> handleDeleteUser(request, user);

                case GET_PENDING_USERS -> handleGetPendingUsers(user);
                case APPROVE_USER      -> handleApproveUser(request, user);
                case REJECT_USER       -> handleRejectUser(request, user);

                case GET_MY_PROFILE    -> handleMyProfile(user);
                case UPDATE_MY_PROFILE -> handleUpdateProfile(request, user);
                case VERIFY_PASSWORD   -> handleVerifyPassword(request, user);

                case GET_ALL_PRODUCTS  -> Response.ok((Serializable) new ArrayList<>(productDAO.findAll()));
                case GET_PRODUCT_BY_ID -> Response.ok(productDAO.findById(request.getIntParam("id")).orElse(null));
                case CREATE_PRODUCT    -> handleCreateProduct(request, user);
                case UPDATE_PRODUCT    -> handleUpdateProduct(request, user);
                case DELETE_PRODUCT    -> handleDeleteProduct(request, user);

                case GET_ALL_WAREHOUSES  -> Response.ok((Serializable) new ArrayList<>(warehouseDAO.findAll()));
                case GET_WAREHOUSE_BY_ID -> Response.ok(warehouseDAO.findById(request.getIntParam("id")).orElse(null));
                case CREATE_WAREHOUSE    -> handleCreateWarehouse(request, user);
                case UPDATE_WAREHOUSE    -> handleUpdateWarehouse(request, user);
                case DELETE_WAREHOUSE    -> handleDeleteWarehouse(request, user);

                case GET_STOCK_ALL          -> Response.ok((Serializable) new ArrayList<>(new StockDAOImpl().findAll()));
                case GET_STOCK_BY_WAREHOUSE -> Response.ok((Serializable) new ArrayList<>(
                        new StockDAOImpl().findByWarehouse(request.getIntParam("warehouseId"))));

                case GET_ALL_SUPPLIERS -> Response.ok((Serializable) new ArrayList<>(supplierDAO.findAll()));
                case CREATE_SUPPLIER   -> handleCreateSupplier(request, user);
                case UPDATE_SUPPLIER   -> handleUpdateSupplier(request, user);
                case DELETE_SUPPLIER   -> handleDeleteSupplier(request, user);

                case GET_ALL_CUSTOMERS -> Response.ok((Serializable) new ArrayList<>(customerDAO.findAll()));
                case CREATE_CUSTOMER   -> handleCreateCustomer(request, user);
                case UPDATE_CUSTOMER   -> handleUpdateCustomer(request, user);
                case DELETE_CUSTOMER   -> handleDeleteCustomer(request, user);

                case PROCESS_INCOME    -> handleProcessIncome(request, user);
                case PROCESS_OUTCOME   -> handleProcessOutcome(request, user);
                case PROCESS_TRANSFER  -> handleProcessTransfer(request, user);
                case PROCESS_INVENTORY -> handleProcessInventory(request, user);
                case GET_DOCUMENTS     -> Response.ok((Serializable) new ArrayList<>(documentDAO.findAll()));
                case GET_DOCUMENT_BY_ID-> Response.ok(documentDAO.findById(request.getIntParam("id")).orElse(null));

                case GET_FORECAST_ALL          -> Response.ok((Serializable) new ArrayList<>(forecastService.getLatestForecasts()));
                case GET_FORECAST_BY_WAREHOUSE -> Response.ok((Serializable) new ArrayList<>(
                        forecastService.getForecastsByWarehouse(request.getIntParam("warehouseId"))));
                case RECALCULATE_FORECAST      -> handleRecalculate(user);

                case GET_REPORT_TURNOVER -> handleTurnoverReport(user);
                case GET_SALES_REPORT    -> handleSalesReport(request, user);
                case GET_AUDIT_LOG       -> handleGetAuditLog(request, user);
                case BACKUP_DATABASE     -> handleBackupDatabase(user);
                case EXPORT_DOCUMENTS_CSV -> handleExportExcel(request, user);
                case GET_ANALYTICS       -> handleGetAnalytics(user);

                default -> Response.error("Неизвестная команда: " + request.getAction());
            };

        } catch (SecurityException e) {
            return Response.error("Доступ запрещён: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return Response.error("Ошибка валидации: " + e.getMessage());
        } catch (Exception e) {
            log.severe("Ошибка обработки запроса " + request.getAction() + ": " + e.getMessage());
            return Response.error("Внутренняя ошибка сервера: " + e.getMessage());
        } finally {
            AuthService.clearCurrentUser();
        }
    }

    private Response handleLogin(Request req) {
        String username = req.getParam("username");
        String password = req.getParam("password");
        String token = authService.login(username, password);
        User user = authService.authenticate(token);
        auditDAO.logAction(user.getId(), "LOGIN", "Пользователь " + username + " вошёл в систему");
        return Response.withToken(token, user);
    }

    private Response handleRegister(Request req) {
        User newUser = (User) req.getPayload();
        if (newUser.getRole() != null && Role.ADMIN.equals(newUser.getRole().getRoleName())) {
            return Response.error("Регистрация с ролью 'Администратор' запрещена.");
        }
        try {
            authService.register(newUser);
            return Response.ok("Заявка на регистрацию отправлена.");
        } catch (Exception e) {
            return Response.error(e.getMessage());
        }
    }

    private Response handleLogout(Request req, User user) {
        auditDAO.logAction(user.getId(), "LOGOUT", "Пользователь " + user.getUsername() + " вышел из системы");
        authService.logout(req.getToken());
        return Response.ok("Выход выполнен");
    }

    private Response handleGetAllUsers(User user) {
        authService.requireRole(user, Role.ADMIN);
        return Response.ok((Serializable) new ArrayList<>(userDAO.findAll()));
    }

    private Response handleGetUserById(Request req, User user) {
        authService.requireRole(user, Role.ADMIN);
        return Response.ok(userDAO.findById(req.getIntParam("id")).orElse(null));
    }

    private Response handleCreateUser(Request req, User user) {
        authService.requireRole(user, Role.ADMIN);
        User newUser = (User) req.getPayload();
        int id = userDAO.create(newUser);
        newUser.setId(id);
        auditDAO.logAction(user.getId(), "CREATE_USER", "Создан пользователь " + newUser.getUsername());
        return Response.ok("Пользователь создан", newUser);
    }

    private Response handleUpdateUser(Request req, User user) {
        authService.requireRole(user, Role.ADMIN);
        User updatedUser = (User) req.getPayload();
        if (updatedUser.getId() == user.getId() &&
                !user.getRole().getRoleName().equals(updatedUser.getRole().getRoleName())) {
            return Response.error("Нельзя изменить собственную роль.");
        }
        boolean ok = userDAO.update(updatedUser);
        if (ok) {
            auditDAO.logAction(user.getId(), "UPDATE_USER", "Изменён пользователь ID=" + updatedUser.getId());
            return Response.ok("Пользователь обновлён");
        } else {
            return Response.error("Не удалось обновить пользователя");
        }
    }

    private Response handleDeactivateUser(Request req, User user) {
        authService.requireRole(user, Role.ADMIN);
        int targetId = req.getIntParam("id");
        if (targetId == user.getId()) {
            return Response.error("Нельзя заблокировать самого себя.");
        }
        boolean active = Boolean.parseBoolean(req.getParam("active"));
        boolean ok = userDAO.setActive(targetId, active);
        if (ok) {
            String action = active ? "Активирован" : "Заблокирован";
            auditDAO.logAction(user.getId(), "BLOCK_USER", action + " пользователь ID=" + targetId);
            return Response.ok(active ? "Пользователь активирован" : "Пользователь заблокирован");
        } else {
            return Response.error("Не удалось изменить статус пользователя");
        }
    }

    private Response handleDeleteUser(Request req, User user) {
        authService.requireRole(user, Role.ADMIN);
        int targetId = req.getIntParam("id");
        if (targetId == user.getId()) {
            return Response.error("Нельзя удалить самого себя.");
        }
        boolean ok = userDAO.delete(targetId);
        if (ok) {
            auditDAO.logAction(user.getId(), "DELETE_USER", "Удалён пользователь ID=" + targetId);
            return Response.ok("Пользователь удалён");
        } else {
            return Response.error("Не удалось удалить пользователя");
        }
    }

    private Response handleGetPendingUsers(User user) {
        authService.requireRole(user, Role.ADMIN);
        return Response.ok((Serializable) new ArrayList<>(userDAO.findPendingUsers()));
    }

    private Response handleApproveUser(Request req, User user) {
        authService.requireRole(user, Role.ADMIN);
        int userId = req.getIntParam("id");
        boolean ok = userDAO.setRegistrationStatus(userId, RegistrationStatus.APPROVED);
        if (ok) {
            auditDAO.logAction(user.getId(), "APPROVE_USER", "Одобрена регистрация пользователя ID=" + userId);
            return Response.ok("Пользователь одобрен");
        } else {
            return Response.error("Не удалось одобрить пользователя");
        }
    }

    private Response handleRejectUser(Request req, User user) {
        authService.requireRole(user, Role.ADMIN);
        int userId = req.getIntParam("id");
        boolean ok = userDAO.setRegistrationStatus(userId, RegistrationStatus.REJECTED);
        if (ok) {
            auditDAO.logAction(user.getId(), "REJECT_USER", "Отклонена регистрация пользователя ID=" + userId);
            return Response.ok("Заявка отклонена");
        } else {
            return Response.error("Не удалось отклонить заявку");
        }
    }

    private Response handleMyProfile(User user) {
        User fresh = userDAO.findById(user.getId()).orElse(user);
        UserProfile.UserStats stats = loadUserStats(user.getId());
        UserProfile profile = new UserProfile(fresh, stats);
        return Response.ok(profile);
    }

    private Response handleUpdateProfile(Request req, User currentUser) {
        User updated = (User) req.getPayload();
        updated.setId(currentUser.getId());
        updated.setUsername(currentUser.getUsername());
        updated.setRole(currentUser.getRole());
        updated.setActive(currentUser.isActive());

        if (updated.getPasswordHash() == null || updated.getPasswordHash().isEmpty()) {
            updated.setPasswordHash(currentUser.getPasswordHash());
        }

        boolean ok = userDAO.update(updated);
        if (ok) {
            return Response.ok("Профиль обновлён");
        } else {
            return Response.error("Не удалось обновить профиль");
        }
    }

    private Response handleVerifyPassword(Request req, User user) {
        String oldPassword = req.getParam("password");
        if (oldPassword == null || oldPassword.isEmpty()) {
            return Response.error("Старый пароль не передан");
        }
        User fresh = userDAO.findById(user.getId()).orElse(user);
        if (!PasswordUtil.verify(oldPassword, fresh.getPasswordHash())) {
            return Response.error("Неверный старый пароль");
        }
        return Response.ok("OK");
    }

    private UserProfile.UserStats loadUserStats(int userId) {
        int income = 0, outcome = 0, transfer = 0, inventory = 0;
        String sql = "SELECT document_type, COUNT(*) AS cnt FROM document WHERE responsible_user_id=? GROUP BY document_type";
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String type = rs.getString("document_type");
                    int cnt = rs.getInt("cnt");
                    switch (type) {
                        case "INCOME" -> income = cnt;
                        case "OUTCOME" -> outcome = cnt;
                        case "TRANSFER" -> transfer = cnt;
                        case "INVENTORY" -> inventory = cnt;
                    }
                }
            }
        } catch (SQLException e) {
            log.warning("Ошибка загрузки статистики: " + e.getMessage());
        }
        return new UserProfile.UserStats(income, outcome, transfer, inventory, 0);
    }

    private Response handleCreateProduct(Request req, User user) {
        authService.requireRole(user, Role.ADMIN, Role.PURCHASE_MANAGER);
        Product p = (Product) req.getPayload();
        int id = productDAO.create(p);
        p.setId(id);
        auditDAO.logAction(user.getId(), "CREATE_PRODUCT", "Добавлен товар " + p.getName());
        return Response.ok("Товар добавлен", p);
    }

    private Response handleUpdateProduct(Request req, User user) {
        authService.requireRole(user, Role.ADMIN, Role.PURCHASE_MANAGER);
        Product p = (Product) req.getPayload();
        boolean ok = productDAO.update(p);
        if (ok) {
            auditDAO.logAction(user.getId(), "UPDATE_PRODUCT", "Изменён товар " + p.getName());
            return Response.ok("Товар обновлён");
        } else {
            return Response.error("Не удалось обновить товар");
        }
    }

    private Response handleDeleteProduct(Request req, User user) {
        authService.requireRole(user, Role.ADMIN);
        int id = req.getIntParam("id");
        boolean ok = productDAO.delete(id);
        if (ok) {
            auditDAO.logAction(user.getId(), "DELETE_PRODUCT", "Удалён товар ID=" + id);
            return Response.ok("Товар удалён");
        } else {
            return Response.error("Не удалось удалить товар");
        }
    }

    private Response handleCreateWarehouse(Request req, User user) {
        authService.requireRole(user, Role.ADMIN);
        Warehouse w = (Warehouse) req.getPayload();
        int id = warehouseDAO.create(w);
        w.setId(id);
        auditDAO.logAction(user.getId(), "CREATE_WAREHOUSE", "Добавлен склад " + w.getName());
        return Response.ok("Склад создан", w);
    }

    private Response handleUpdateWarehouse(Request req, User user) {
        authService.requireRole(user, Role.ADMIN);
        Warehouse w = (Warehouse) req.getPayload();
        boolean ok = warehouseDAO.update(w);
        if (ok) {
            auditDAO.logAction(user.getId(), "UPDATE_WAREHOUSE", "Изменён склад " + w.getName());
            return Response.ok("Склад обновлён");
        } else {
            return Response.error("Не удалось обновить склад");
        }
    }

    private Response handleDeleteWarehouse(Request req, User user) {
        authService.requireRole(user, Role.ADMIN);
        int id = req.getIntParam("id");
        boolean ok = warehouseDAO.delete(id);
        if (ok) {
            auditDAO.logAction(user.getId(), "DELETE_WAREHOUSE", "Удалён склад ID=" + id);
            return Response.ok("Склад удалён");
        } else {
            return Response.error("Не удалось удалить склад");
        }
    }

    private Response handleCreateSupplier(Request req, User user) {
        authService.requireRole(user, Role.ADMIN, Role.PURCHASE_MANAGER);
        Supplier s = (Supplier) req.getPayload();
        int id = supplierDAO.create(s);
        s.setId(id);
        auditDAO.logAction(user.getId(), "CREATE_SUPPLIER", "Добавлен поставщик " + s.getName());
        return Response.ok("Поставщик добавлен", s);
    }

    private Response handleUpdateSupplier(Request req, User user) {
        authService.requireRole(user, Role.ADMIN, Role.PURCHASE_MANAGER);
        Supplier s = (Supplier) req.getPayload();
        boolean ok = supplierDAO.update(s);
        if (ok) {
            auditDAO.logAction(user.getId(), "UPDATE_SUPPLIER", "Изменён поставщик " + s.getName());
            return Response.ok("Поставщик обновлён");
        } else {
            return Response.error("Не удалось обновить поставщика");
        }
    }

    private Response handleDeleteSupplier(Request req, User user) {
        authService.requireRole(user, Role.ADMIN);
        int id = req.getIntParam("id");
        boolean ok = supplierDAO.delete(id);
        if (ok) {
            auditDAO.logAction(user.getId(), "DELETE_SUPPLIER", "Удалён поставщик ID=" + id);
            return Response.ok("Поставщик удалён");
        } else {
            return Response.error("Не удалось удалить поставщика");
        }
    }

    private Response handleCreateCustomer(Request req, User user) {
        authService.requireRole(user, Role.ADMIN, Role.SALES_MANAGER);
        Customer c = (Customer) req.getPayload();
        int id = customerDAO.create(c);
        c.setId(id);
        auditDAO.logAction(user.getId(), "CREATE_CUSTOMER", "Добавлен покупатель " + c.getName());
        return Response.ok("Покупатель добавлен", c);
    }

    private Response handleUpdateCustomer(Request req, User user) {
        authService.requireRole(user, Role.ADMIN, Role.SALES_MANAGER);
        Customer c = (Customer) req.getPayload();
        boolean ok = customerDAO.update(c);
        if (ok) {
            auditDAO.logAction(user.getId(), "UPDATE_CUSTOMER", "Изменён покупатель " + c.getName());
            return Response.ok("Покупатель обновлён");
        } else {
            return Response.error("Не удалось обновить покупателя");
        }
    }

    private Response handleDeleteCustomer(Request req, User user) {
        authService.requireRole(user, Role.ADMIN);
        int id = req.getIntParam("id");
        boolean ok = customerDAO.delete(id);
        if (ok) {
            auditDAO.logAction(user.getId(), "DELETE_CUSTOMER", "Удалён покупатель ID=" + id);
            return Response.ok("Покупатель удалён");
        } else {
            return Response.error("Не удалось удалить покупателя");
        }
    }

    @SuppressWarnings("unchecked")
    private Response handleProcessIncome(Request req, User user) {
        authService.requireRole(user, Role.WAREHOUSE_WORKER, Role.PURCHASE_MANAGER, Role.ADMIN);
        Document doc = (Document) req.getPayload();
        Document result = inventoryService.processIncomeDocument(
                doc.getWarehouseTo(), doc.getSupplier(), doc.getItems(), user);
        auditDAO.logAction(user.getId(), "INCOME", "Приходная накладная " + result.getDocumentNumber());
        return Response.ok("Приходная накладная оформлена: " + result.getDocumentNumber(), result);
    }

    @SuppressWarnings("unchecked")
    private Response handleProcessOutcome(Request req, User user) {
        authService.requireRole(user, Role.WAREHOUSE_WORKER, Role.SALES_MANAGER, Role.ADMIN);
        Document doc = (Document) req.getPayload();
        Document result = inventoryService.processOutcomeDocument(
                doc.getWarehouseFrom(), doc.getCustomer(), doc.getItems(), user);
        auditDAO.logAction(user.getId(), "OUTCOME", "Расходная накладная " + result.getDocumentNumber());
        return Response.ok("Расходная накладная оформлена: " + result.getDocumentNumber(), result);
    }

    @SuppressWarnings("unchecked")
    private Response handleProcessTransfer(Request req, User user) {
        authService.requireRole(user, Role.WAREHOUSE_WORKER, Role.ADMIN);
        Document doc = (Document) req.getPayload();
        Document result = inventoryService.processTransfer(
                doc.getWarehouseFrom(), doc.getWarehouseTo(), doc.getItems(), user);
        auditDAO.logAction(user.getId(), "TRANSFER", "Перемещение " + result.getDocumentNumber());
        return Response.ok("Перемещение оформлено: " + result.getDocumentNumber(), result);
    }

    @SuppressWarnings("unchecked")
    private Response handleProcessInventory(Request req, User user) {
        authService.requireRole(user, Role.WAREHOUSE_WORKER, Role.ADMIN);
        InventoryRequest invReq = (InventoryRequest) req.getPayload();
        InventoryResult result = inventoryService.processInventory(
                invReq.getWarehouse(), invReq.getActualQuantities(), user);
        auditDAO.logAction(user.getId(), "INVENTORY", "Инвентаризация склада " + invReq.getWarehouse().getName());
        return Response.ok("Инвентаризация проведена", result);
    }

    private Response handleRecalculate(User user) {
        authService.requireRole(user, Role.ADMIN, Role.PURCHASE_MANAGER);
        forecastService.recalculateAll();
        auditDAO.logAction(user.getId(), "RECALCULATE", "Пересчёт прогнозов дефицита");
        return Response.ok("Прогнозы пересчитаны");
    }

    private Response handleTurnoverReport(User user) {
        authService.requireRole(user, Role.ADMIN, Role.PURCHASE_MANAGER);
        List<TurnoverEntry> report = new ArrayList<>();
        String sql = """
            SELECT 
                p.product_id,
                p.article,
                p.name,
                p.unit,
                COALESCE(inc.total_qty, 0) AS income_qty,
                COALESCE(outc.total_qty, 0) AS outcome_qty,
                COALESCE(st.quantity, 0) AS current_stock
            FROM product p
            LEFT JOIN (
                SELECT di.product_id, SUM(di.quantity) AS total_qty
                FROM document_item di
                JOIN document d ON di.document_id = d.document_id
                WHERE d.document_type = 'INCOME'
                  AND d.document_date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
                GROUP BY di.product_id
            ) inc ON p.product_id = inc.product_id
            LEFT JOIN (
                SELECT di.product_id, SUM(di.quantity) AS total_qty
                FROM document_item di
                JOIN document d ON di.document_id = d.document_id
                WHERE d.document_type = 'OUTCOME'
                  AND d.document_date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
                GROUP BY di.product_id
            ) outc ON p.product_id = outc.product_id
            LEFT JOIN (
                SELECT product_id, SUM(quantity) AS quantity
                FROM stock
                GROUP BY product_id
            ) st ON p.product_id = st.product_id
            ORDER BY p.name
        """;

        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                TurnoverEntry entry = new TurnoverEntry();
                entry.setProductId(rs.getInt("product_id"));
                entry.setArticle(rs.getString("article"));
                entry.setProductName(rs.getString("name"));
                entry.setUnit(rs.getString("unit"));
                entry.setIncomeQty(rs.getBigDecimal("income_qty"));
                entry.setOutcomeQty(rs.getBigDecimal("outcome_qty"));
                entry.setCurrentStock(rs.getInt("current_stock"));
                report.add(entry);
            }
        } catch (SQLException e) {
            log.severe("Ошибка формирования отчёта: " + e.getMessage());
            return Response.error("Не удалось сформировать отчёт");
        }

        return Response.ok((Serializable) new ArrayList<>(report));
    }

    private Response handleSalesReport(Request req, User user) {
        authService.requireRole(user, Role.ADMIN, Role.ACCOUNTANT);
        String dateFrom = req.getParam("dateFrom");
        String dateTo = req.getParam("dateTo");
        if (dateFrom == null || dateTo == null || dateFrom.isEmpty() || dateTo.isEmpty()) {
            return Response.error("Не указан период (dateFrom и dateTo)");
        }

        List<SalesReportEntry> report = new ArrayList<>();
        String sql = """
            SELECT p.article, p.name,
                   SUM(di.quantity) AS total_qty,
                   AVG(di.price) AS avg_price,
                   SUM(di.quantity * di.price) AS total_cost
            FROM document_item di
            JOIN document d ON di.document_id = d.document_id
            JOIN product p ON di.product_id = p.product_id
            WHERE d.document_type = 'OUTCOME'
              AND d.document_date >= ?
              AND d.document_date < ? + INTERVAL 1 DAY
            GROUP BY p.product_id, p.article, p.name
            ORDER BY total_cost DESC
        """;

        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, dateFrom);
            ps.setString(2, dateTo);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SalesReportEntry entry = new SalesReportEntry();
                    entry.setArticle(rs.getString("article"));
                    entry.setProductName(rs.getString("name"));
                    entry.setTotalQuantity(rs.getBigDecimal("total_qty"));
                    entry.setAvgPrice(rs.getBigDecimal("avg_price"));
                    entry.setTotalCost(rs.getBigDecimal("total_cost"));
                    report.add(entry);
                }
            }
        } catch (SQLException e) {
            log.severe("Ошибка отчёта по продажам: " + e.getMessage());
            return Response.error("Не удалось сформировать отчёт");
        }

        if (report.isEmpty()) {
            return Response.ok("За выбранный период продаж нет", new ArrayList<SalesReportEntry>());
        }

        return Response.ok((Serializable) new ArrayList<>(report));
    }

    private Response handleGetAuditLog(Request req, User user) {
        authService.requireRole(user, Role.ADMIN);
        String actionFilter = req.getParam("actionType");
        List<AuditEntry> entries = auditDAO.getEntries(actionFilter);
        return Response.ok((Serializable) new ArrayList<>(entries));
    }

    private Response handleBackupDatabase(User user) {
        authService.requireRole(user, Role.ADMIN);
        try {
            String dbName = "warehouse_accounting";
            String dbUser = ServerConfig.getInstance().getDbUsername();
            String dbPass = ServerConfig.getInstance().getDbPassword();
            String dumpPath = ServerConfig.getInstance().getMysqldumpPath();

            java.io.File dumpFile = new java.io.File(dumpPath);
            if (!dumpFile.exists()) {
                String msg = "Файл mysqldump не найден по пути: " + dumpPath;
                log.severe(msg);
                return Response.error(msg);
            }

            String backupDir = System.getProperty("user.dir") + "/backups";
            new java.io.File(backupDir).mkdirs();
            String backupFileName = "backup_" + java.time.LocalDate.now() + ".sql";
            String backupPath = backupDir + "/" + backupFileName;

            ProcessBuilder pb = new ProcessBuilder(
                    dumpPath,
                    "-u" + dbUser,
                    "-p" + dbPass,
                    dbName,
                    "-r" + backupPath
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            java.io.InputStream is = process.getInputStream();
            java.util.Scanner scanner = new java.util.Scanner(is).useDelimiter("\\A");
            String output = scanner.hasNext() ? scanner.next() : "";

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                log.info("Резервная копия создана: " + backupPath);
                auditDAO.logAction(user.getId(), "BACKUP", "Создана резервная копия БД: " + backupFileName);
                return Response.ok("Резервная копия создана: " + backupFileName);
            } else {
                log.severe("mysqldump ошибка: " + output);
                return Response.error("Ошибка mysqldump: " + output);
            }
        } catch (Exception e) {
            log.severe("Ошибка резервного копирования: " + e.getMessage());
            return Response.error("Не удалось создать копию: " + e.getMessage());
        }
    }

    private Response handleExportExcel(Request req, User user) {
        authService.requireRole(user, Role.ADMIN, Role.ACCOUNTANT);
        String typeFilter = req.getParam("type");
        List<Document> documents;
        if (typeFilter != null && !typeFilter.isEmpty() && !"ALL".equalsIgnoreCase(typeFilter)) {
            documents = documentDAO.findByType(DocumentType.valueOf(typeFilter));
        } else {
            documents = documentDAO.findAll();
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Реестр документов");

            XSSFCellStyle headerStyle = workbook.createCellStyle();
            XSSFFont headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 12);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            XSSFCellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            String[] headers = {"ID", "Тип", "Номер", "Дата", "Склад отправитель",
                    "Склад получатель", "Поставщик", "Покупатель", "Ответственный", "Комментарий"};
            XSSFRow headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                XSSFCell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
            int rowNum = 1;
            for (Document doc : documents) {
                XSSFRow row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(doc.getId());
                row.createCell(1).setCellValue(doc.getDocumentType().getDisplayName());
                row.createCell(2).setCellValue(doc.getDocumentNumber());
                row.createCell(3).setCellValue(doc.getDocumentDate() != null ? doc.getDocumentDate().format(dtf) : "");
                row.createCell(4).setCellValue(doc.getWarehouseFrom() != null ? doc.getWarehouseFrom().getName() : "");
                row.createCell(5).setCellValue(doc.getWarehouseTo() != null ? doc.getWarehouseTo().getName() : "");
                row.createCell(6).setCellValue(doc.getSupplier() != null ? doc.getSupplier().getName() : "");
                row.createCell(7).setCellValue(doc.getCustomer() != null ? doc.getCustomer().getName() : "");
                row.createCell(8).setCellValue(doc.getResponsibleUser() != null ? doc.getResponsibleUser().getFullName() : "");
                row.createCell(9).setCellValue(doc.getComment() != null ? doc.getComment() : "");
                for (int c = 0; c < headers.length; c++) {
                    row.getCell(c).setCellStyle(dataStyle);
                }
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, sheet.getColumnWidth(i) + 1500);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return Response.okFile(baos.toByteArray(), "documents.xlsx");
        } catch (Exception e) {
            log.severe("Ошибка генерации Excel: " + e.getMessage());
            return Response.error("Не удалось создать Excel-файл");
        }
    }

    private Response handleGetAnalytics(User user) {
        String role = user.getRole().getRoleName();
        List<AnalysisChart> charts = new ArrayList<>();

        if (Role.ADMIN.equals(role)) {
            charts.add(productsByWarehouseChart());
            charts.add(userActivityChart());
        } else if (Role.PURCHASE_MANAGER.equals(role)) {
            charts.add(dailyIncomeChart());
            charts.add(topSalesChart()); // новое название
        } else if (Role.WAREHOUSE_WORKER.equals(role)) {
            charts.add(operationTypesPieChart(user.getId()));
            charts.add(warehouseLoadChart());
        } else if (Role.SALES_MANAGER.equals(role)) {
            charts.add(salesByCustomerChart());
            charts.add(dailySalesChart());
        } else if (Role.ACCOUNTANT.equals(role)) {
            charts.add(salesByCategoryChart());
            charts.add(financialFlowChart());
        }

        return Response.ok((Serializable) new ArrayList<>(charts));
    }

    private AnalysisChart productsByWarehouseChart() {
        List<ChartDataPoint> points = new ArrayList<>();
        String sql = "SELECT w.name, SUM(s.quantity) AS total FROM stock s JOIN warehouse w ON s.warehouse_id = w.warehouse_id GROUP BY w.warehouse_id, w.name";
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) points.add(new ChartDataPoint(rs.getString("name"), rs.getDouble("total")));
        } catch (SQLException e) { log.severe("productsByWarehouseChart: " + e.getMessage()); }
        return new AnalysisChart("Товары по складам", "PIE", points);
    }

    private AnalysisChart userActivityChart() {
        List<ChartDataPoint> points = new ArrayList<>();
        String sql = "SELECT u.full_name, COUNT(*) AS cnt FROM document d JOIN user u ON d.responsible_user_id = u.user_id GROUP BY u.user_id, u.full_name ORDER BY cnt DESC LIMIT 10";
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) points.add(new ChartDataPoint(rs.getString("full_name"), rs.getInt("cnt")));
        } catch (SQLException e) { log.severe("userActivityChart: " + e.getMessage()); }
        return new AnalysisChart("Активность пользователей", "BAR", points);
    }

    private AnalysisChart dailyIncomeChart() {
        List<ChartDataPoint> points = new ArrayList<>();
        String sql = "SELECT DATE(d.document_date) AS dt, SUM(di.quantity) AS total FROM document d JOIN document_item di ON d.document_id = di.document_id WHERE d.document_type='INCOME' AND d.document_date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) GROUP BY dt ORDER BY dt";
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) points.add(new ChartDataPoint(rs.getString("dt"), rs.getDouble("total")));
        } catch (SQLException e) { log.severe("dailyIncomeChart: " + e.getMessage()); }
        return new AnalysisChart("Динамика приходов", "LINE", points);
    }

    private AnalysisChart topSalesChart() {
        List<ChartDataPoint> points = new ArrayList<>();
        String sql = "SELECT p.name, COALESCE(SUM(di.quantity), 0) AS outcome FROM document_item di JOIN document d ON di.document_id = d.document_id JOIN product p ON di.product_id = p.product_id WHERE d.document_type='OUTCOME' AND d.document_date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) GROUP BY p.product_id, p.name ORDER BY outcome DESC LIMIT 10";
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) points.add(new ChartDataPoint(rs.getString("name"), rs.getDouble("outcome")));
        } catch (SQLException e) { log.severe("topSalesChart: " + e.getMessage()); }
        return new AnalysisChart("Топ-10 по продажам", "BAR", points);
    }

    private AnalysisChart operationTypesPieChart(int userId) {
        List<ChartDataPoint> points = new ArrayList<>();
        String sql = "SELECT d.document_type, COUNT(*) AS cnt FROM document d WHERE d.responsible_user_id=? GROUP BY d.document_type";
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String type = switch (rs.getString("document_type")) {
                        case "INCOME" -> "Приход";
                        case "OUTCOME" -> "Расход";
                        case "TRANSFER" -> "Перемещение";
                        case "INVENTORY" -> "Инвентаризация";
                        default -> "Прочее";
                    };
                    points.add(new ChartDataPoint(type, rs.getInt("cnt")));
                }
            }
        } catch (SQLException e) { log.severe("operationTypesPieChart: " + e.getMessage()); }
        return new AnalysisChart("Типы операций", "PIE", points);
    }

    private AnalysisChart warehouseLoadChart() {
        List<ChartDataPoint> points = new ArrayList<>();
        String sql = "SELECT w.name, COALESCE(SUM(s.quantity), 0) AS total FROM warehouse w LEFT JOIN stock s ON w.warehouse_id = s.warehouse_id GROUP BY w.warehouse_id, w.name";
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) points.add(new ChartDataPoint(rs.getString("name"), rs.getDouble("total")));
        } catch (SQLException e) { log.severe("warehouseLoadChart: " + e.getMessage()); }
        return new AnalysisChart("Загруженность складов", "BAR", points);
    }

    private AnalysisChart salesByCustomerChart() {
        List<ChartDataPoint> points = new ArrayList<>();
        String sql = "SELECT c.name, COUNT(*) AS cnt FROM document d JOIN customer c ON d.customer_id = c.customer_id WHERE d.document_type='OUTCOME' GROUP BY c.customer_id, c.name ORDER BY cnt DESC LIMIT 10";
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) points.add(new ChartDataPoint(rs.getString("name"), rs.getInt("cnt")));
        } catch (SQLException e) { log.severe("salesByCustomerChart: " + e.getMessage()); }
        return new AnalysisChart("Продажи по клиентам", "BAR", points);
    }

    private AnalysisChart dailySalesChart() {
        List<ChartDataPoint> points = new ArrayList<>();
        String sql = "SELECT DATE(d.document_date) AS dt, SUM(di.quantity) AS total FROM document d JOIN document_item di ON d.document_id = di.document_id WHERE d.document_type='OUTCOME' AND d.document_date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) GROUP BY dt ORDER BY dt";
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) points.add(new ChartDataPoint(rs.getString("dt"), rs.getDouble("total")));
        } catch (SQLException e) { log.severe("dailySalesChart: " + e.getMessage()); }
        return new AnalysisChart("Динамика продаж", "LINE", points);
    }

    private AnalysisChart salesByCategoryChart() {
        List<ChartDataPoint> points = new ArrayList<>();
        String sql = "SELECT p.category, SUM(di.quantity * COALESCE(di.price,0)) AS total FROM document_item di JOIN document d ON di.document_id = d.document_id JOIN product p ON di.product_id = p.product_id WHERE d.document_type='OUTCOME' AND p.category IS NOT NULL GROUP BY p.category ORDER BY total DESC";
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) points.add(new ChartDataPoint(rs.getString("category"), rs.getDouble("total")));
        } catch (SQLException e) { log.severe("salesByCategoryChart: " + e.getMessage()); }
        return new AnalysisChart("Стоимость по категориям", "PIE", points);
    }

    private AnalysisChart financialFlowChart() {
        List<ChartDataPoint> incomePoints = new ArrayList<>();
        List<ChartDataPoint> outcomePoints = new ArrayList<>();
        String sql = "SELECT DATE(d.document_date) AS dt, d.document_type, SUM(di.quantity * COALESCE(di.price,0)) AS total FROM document d JOIN document_item di ON d.document_id = di.document_id WHERE d.document_type IN ('INCOME','OUTCOME') AND d.document_date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) GROUP BY dt, d.document_type ORDER BY dt";
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String type = rs.getString("document_type");
                String date = rs.getString("dt");
                double total = rs.getDouble("total");
                if ("INCOME".equals(type)) incomePoints.add(new ChartDataPoint(date, total));
                else outcomePoints.add(new ChartDataPoint(date, total));
            }
        } catch (SQLException e) { log.severe("financialFlowChart: " + e.getMessage()); }
        AnalysisChart chart = new AnalysisChart("Финансовые потоки", "LINE", incomePoints);
        chart.setSecondSeries(outcomePoints);
        return chart;
    }
}