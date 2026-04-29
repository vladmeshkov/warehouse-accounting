package by.bsuir.warehouse.server.network;

import by.bsuir.warehouse.common.model.*;
import by.bsuir.warehouse.common.protocol.*;
import by.bsuir.warehouse.server.dao.*;
import by.bsuir.warehouse.server.dao.impl.*;
import by.bsuir.warehouse.server.service.*;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Диспетчер запросов — реализует паттерн Factory Method.
 *
 * Маршрутизирует входящий Request к нужному обработчику на основе Action.
 * Проверяет права доступа (роли) перед выполнением каждой операции.
 * Возвращает Response с результатом или описанием ошибки.
 */
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

    /**
     * Обрабатывает запрос и возвращает ответ.
     * LOGIN не требует токена. Все остальные запросы — требуют.
     */
    public Response dispatch(Request request) {
        try {
            // LOGIN — особый случай, без токена
            if (request.getAction() == Action.LOGIN) {
                return handleLogin(request);
            }

            // Все остальные запросы требуют аутентификации
            User user = authService.authenticate(request.getToken());

            return switch (request.getAction()) {
                case LOGOUT -> handleLogout(request);

                // ── Пользователи ─────────────────────────────────────────
                case GET_ALL_USERS   -> handleGetAllUsers(user);
                case GET_USER_BY_ID  -> handleGetUserById(request, user);
                case CREATE_USER     -> handleCreateUser(request, user);
                case UPDATE_USER     -> handleUpdateUser(request, user);
                case DEACTIVATE_USER -> handleDeactivateUser(request, user);
                case GET_ALL_ROLES   -> Response.ok((Serializable) new ArrayList<>(userDAO.findAllRoles()));

                // ── Товары ────────────────────────────────────────────────
                case GET_ALL_PRODUCTS  -> Response.ok((Serializable) new ArrayList<>(productDAO.findAll()));
                case GET_PRODUCT_BY_ID -> Response.ok(productDAO.findById(request.getIntParam("id")).orElse(null));
                case CREATE_PRODUCT    -> handleCreateProduct(request, user);
                case UPDATE_PRODUCT    -> handleUpdateProduct(request, user);
                case DELETE_PRODUCT    -> handleDeleteProduct(request, user);

                // ── Склады ────────────────────────────────────────────────
                case GET_ALL_WAREHOUSES  -> Response.ok((Serializable) new ArrayList<>(warehouseDAO.findAll()));
                case GET_WAREHOUSE_BY_ID -> Response.ok(warehouseDAO.findById(request.getIntParam("id")).orElse(null));
                case CREATE_WAREHOUSE    -> handleCreateWarehouse(request, user);
                case UPDATE_WAREHOUSE    -> handleUpdateWarehouse(request, user);
                case DELETE_WAREHOUSE    -> handleDeleteWarehouse(request, user);

                // ── Остатки ───────────────────────────────────────────────
                case GET_STOCK_ALL          -> Response.ok((Serializable) new ArrayList<>(new StockDAOImpl().findAll()));
                case GET_STOCK_BY_WAREHOUSE -> Response.ok((Serializable) new ArrayList<>(
                        new StockDAOImpl().findByWarehouse(request.getIntParam("warehouseId"))));

                // ── Поставщики ────────────────────────────────────────────
                case GET_ALL_SUPPLIERS -> Response.ok((Serializable) new ArrayList<>(supplierDAO.findAll()));
                case CREATE_SUPPLIER   -> handleCreateSupplier(request, user);
                case UPDATE_SUPPLIER   -> handleUpdateSupplier(request, user);
                case DELETE_SUPPLIER   -> handleDeleteSupplier(request, user);

                // ── Покупатели ────────────────────────────────────────────
                case GET_ALL_CUSTOMERS -> Response.ok((Serializable) new ArrayList<>(customerDAO.findAll()));
                case CREATE_CUSTOMER   -> handleCreateCustomer(request, user);
                case UPDATE_CUSTOMER   -> handleUpdateCustomer(request, user);
                case DELETE_CUSTOMER   -> handleDeleteCustomer(request, user);

                // ── Документы ─────────────────────────────────────────────
                case PROCESS_INCOME    -> handleProcessIncome(request, user);
                case PROCESS_OUTCOME   -> handleProcessOutcome(request, user);
                case PROCESS_TRANSFER  -> handleProcessTransfer(request, user);
                case PROCESS_INVENTORY -> handleProcessInventory(request, user);
                case GET_DOCUMENTS     -> Response.ok((Serializable) new ArrayList<>(documentDAO.findAll()));
                case GET_DOCUMENT_BY_ID-> Response.ok(documentDAO.findById(request.getIntParam("id")).orElse(null));

                // ── Прогнозирование ───────────────────────────────────────
                case GET_FORECAST_ALL          -> Response.ok((Serializable) new ArrayList<>(forecastService.getLatestForecasts()));
                case GET_FORECAST_BY_WAREHOUSE -> Response.ok((Serializable) new ArrayList<>(
                        forecastService.getForecastsByWarehouse(request.getIntParam("warehouseId"))));
                case RECALCULATE_FORECAST      -> handleRecalculate(user);

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

    // ── Обработчики ───────────────────────────────────────────────────────

    private Response handleLogin(Request req) {
        String username = req.getParam("username");
        String password = req.getParam("password");
        String token = authService.login(username, password);
        User user = authService.authenticate(token);
        return Response.withToken(token, user);
    }

    private Response handleLogout(Request req) {
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
        return Response.ok("Пользователь создан", newUser);
    }

    private Response handleUpdateUser(Request req, User user) {
        authService.requireRole(user, Role.ADMIN);
        User u = (User) req.getPayload();
        return userDAO.update(u)
                ? Response.ok("Пользователь обновлён")
                : Response.error("Не удалось обновить пользователя");
    }

    private Response handleDeactivateUser(Request req, User user) {
        authService.requireRole(user, Role.ADMIN);
        int targetId = req.getIntParam("id");
        boolean active = Boolean.parseBoolean(req.getParam("active"));
        return userDAO.setActive(targetId, active)
                ? Response.ok(active ? "Пользователь активирован" : "Пользователь заблокирован")
                : Response.error("Не удалось изменить статус пользователя");
    }

    private Response handleCreateProduct(Request req, User user) {
        authService.requireRole(user, Role.ADMIN, Role.PURCHASE_MANAGER);
        Product p = (Product) req.getPayload();
        int id = productDAO.create(p);
        p.setId(id);
        return Response.ok("Товар добавлен", p);
    }

    private Response handleUpdateProduct(Request req, User user) {
        authService.requireRole(user, Role.ADMIN, Role.PURCHASE_MANAGER);
        Product p = (Product) req.getPayload();
        return productDAO.update(p)
                ? Response.ok("Товар обновлён")
                : Response.error("Не удалось обновить товар");
    }

    private Response handleDeleteProduct(Request req, User user) {
        authService.requireRole(user, Role.ADMIN);
        return productDAO.delete(req.getIntParam("id"))
                ? Response.ok("Товар удалён")
                : Response.error("Не удалось удалить товар");
    }

    private Response handleCreateWarehouse(Request req, User user) {
        authService.requireRole(user, Role.ADMIN);
        Warehouse w = (Warehouse) req.getPayload();
        int id = warehouseDAO.create(w);
        w.setId(id);
        return Response.ok("Склад создан", w);
    }

    private Response handleUpdateWarehouse(Request req, User user) {
        authService.requireRole(user, Role.ADMIN);
        Warehouse w = (Warehouse) req.getPayload();
        return warehouseDAO.update(w)
                ? Response.ok("Склад обновлён")
                : Response.error("Не удалось обновить склад");
    }

    private Response handleDeleteWarehouse(Request req, User user) {
        authService.requireRole(user, Role.ADMIN);
        return warehouseDAO.delete(req.getIntParam("id"))
                ? Response.ok("Склад удалён")
                : Response.error("Не удалось удалить склад");
    }

    private Response handleCreateSupplier(Request req, User user) {
        authService.requireRole(user, Role.ADMIN, Role.PURCHASE_MANAGER);
        Supplier s = (Supplier) req.getPayload();
        int id = supplierDAO.create(s);
        s.setId(id);
        return Response.ok("Поставщик добавлен", s);
    }

    private Response handleUpdateSupplier(Request req, User user) {
        authService.requireRole(user, Role.ADMIN, Role.PURCHASE_MANAGER);
        return supplierDAO.update((Supplier) req.getPayload())
                ? Response.ok("Поставщик обновлён")
                : Response.error("Не удалось обновить поставщика");
    }

    private Response handleDeleteSupplier(Request req, User user) {
        authService.requireRole(user, Role.ADMIN);
        return supplierDAO.delete(req.getIntParam("id"))
                ? Response.ok("Поставщик удалён")
                : Response.error("Не удалось удалить поставщика");
    }

    private Response handleCreateCustomer(Request req, User user) {
        authService.requireRole(user, Role.ADMIN, Role.SALES_MANAGER);
        Customer c = (Customer) req.getPayload();
        int id = customerDAO.create(c);
        c.setId(id);
        return Response.ok("Покупатель добавлен", c);
    }

    private Response handleUpdateCustomer(Request req, User user) {
        authService.requireRole(user, Role.ADMIN, Role.SALES_MANAGER);
        return customerDAO.update((Customer) req.getPayload())
                ? Response.ok("Покупатель обновлён")
                : Response.error("Не удалось обновить покупателя");
    }

    private Response handleDeleteCustomer(Request req, User user) {
        authService.requireRole(user, Role.ADMIN);
        return customerDAO.delete(req.getIntParam("id"))
                ? Response.ok("Покупатель удалён")
                : Response.error("Не удалось удалить покупателя");
    }

    @SuppressWarnings("unchecked")
    private Response handleProcessIncome(Request req, User user) {
        authService.requireRole(user, Role.WAREHOUSE_WORKER, Role.PURCHASE_MANAGER, Role.ADMIN);
        Document doc = (Document) req.getPayload();
        Document result = inventoryService.processIncomeDocument(
                doc.getWarehouseTo(), doc.getSupplier(), doc.getItems(), user);
        return Response.ok("Приходная накладная оформлена: " + result.getDocumentNumber(), result);
    }

    @SuppressWarnings("unchecked")
    private Response handleProcessOutcome(Request req, User user) {
        authService.requireRole(user, Role.WAREHOUSE_WORKER, Role.SALES_MANAGER, Role.ADMIN);
        Document doc = (Document) req.getPayload();
        Document result = inventoryService.processOutcomeDocument(
                doc.getWarehouseFrom(), doc.getCustomer(), doc.getItems(), user);
        return Response.ok("Расходная накладная оформлена: " + result.getDocumentNumber(), result);
    }

    @SuppressWarnings("unchecked")
    private Response handleProcessTransfer(Request req, User user) {
        authService.requireRole(user, Role.WAREHOUSE_WORKER, Role.ADMIN);
        Document doc = (Document) req.getPayload();
        Document result = inventoryService.processTransfer(
                doc.getWarehouseFrom(), doc.getWarehouseTo(), doc.getItems(), user);
        return Response.ok("Перемещение оформлено: " + result.getDocumentNumber(), result);
    }

    @SuppressWarnings("unchecked")
    private Response handleProcessInventory(Request req, User user) {
        authService.requireRole(user, Role.WAREHOUSE_WORKER, Role.ADMIN);
        // payload: InventoryRequest содержит склад и карту фактических остатков
        InventoryRequest invReq = (InventoryRequest) req.getPayload();
        InventoryService.InventoryResult result = inventoryService.processInventory(
                invReq.warehouse, invReq.actualQuantities, user);
        return Response.ok("Инвентаризация проведена", result);
    }

    private Response handleRecalculate(User user) {
        authService.requireRole(user, Role.ADMIN, Role.PURCHASE_MANAGER);
        forecastService.recalculateAll();
        return Response.ok("Прогнозы пересчитаны");
    }

    // ── Вспомогательный класс для запроса инвентаризации ─────────────────

    public static class InventoryRequest implements java.io.Serializable {
        public Warehouse warehouse;
        public Map<Integer, Integer> actualQuantities;

        public InventoryRequest(Warehouse warehouse, Map<Integer, Integer> actualQuantities) {
            this.warehouse        = warehouse;
            this.actualQuantities = actualQuantities;
        }
    }
}
