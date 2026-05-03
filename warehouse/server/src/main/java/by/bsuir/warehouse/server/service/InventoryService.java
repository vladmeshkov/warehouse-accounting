package by.bsuir.warehouse.server.service;

import by.bsuir.warehouse.common.model.*;
import by.bsuir.warehouse.server.config.DBConnection;
import by.bsuir.warehouse.server.dao.*;
import by.bsuir.warehouse.server.dao.impl.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Основной сервис бизнес-логики складского учёта.
 *
 * Реализует алгоритмы:
 *   processIncomeDocument  — оформление приходной накладной
 *   processOutcomeDocument — оформление расходной накладной
 *   processTransfer        — внутреннее перемещение
 *   processInventory       — инвентаризация с формированием акта расхождений
 *
 * Все операции с остатками выполняются в транзакциях БД.
 */
public class InventoryService {

    private static final Logger log = Logger.getLogger(InventoryService.class.getName());
    private static final DateTimeFormatter NUM_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final AtomicInteger SEQ = new AtomicInteger(1);

    private final DocumentDAO documentDAO = new DocumentDAOImpl();
    private final StockDAO    stockDAO    = new StockDAOImpl();
    private final ForecastDAO forecastDAO = new ForecastDAOImpl();

    // ── 1. Приходная накладная ────────────────────────────────────────────

    /**
     * Оформляет поступление товаров на склад от поставщика.
     */
    public Document processIncomeDocument(Warehouse warehouseTo, Supplier supplier,
                                          List<DocumentItem> items, User responsible) {
        validate(warehouseTo, items);

        Document doc = buildDocument(DocumentType.INCOME, responsible);
        doc.setWarehouseTo(warehouseTo);
        doc.setSupplier(supplier);
        doc.setItems(items);

        Connection conn = DBConnection.getInstance().getConnection();
        try {
            conn.setAutoCommit(false);

            int docId = documentDAO.create(conn, doc);
            doc.setId(docId);

            for (DocumentItem item : items) {
                int qty = item.getQuantity().intValue();
                stockDAO.increaseStock(conn, warehouseTo.getId(),
                        item.getProduct().getId(), qty);
            }

            conn.commit();
            log.info("Приход оформлен: " + doc.getDocumentNumber()
                    + ", склад=" + warehouseTo.getName());

            updateConsumptionForIncome(items);
            return doc;

        } catch (Exception e) {
            rollback(conn);
            throw new RuntimeException("Ошибка оформления прихода: " + e.getMessage(), e);
        } finally {
            resetAutoCommit(conn);
        }
    }

    // ── 2. Расходная накладная ────────────────────────────────────────────

    /**
     * Оформляет отгрузку товаров со склада покупателю.
     */
    public Document processOutcomeDocument(Warehouse warehouseFrom, Customer customer,
                                           List<DocumentItem> items, User responsible) {
        validate(warehouseFrom, items);

        Document doc = buildDocument(DocumentType.OUTCOME, responsible);
        doc.setWarehouseFrom(warehouseFrom);
        doc.setCustomer(customer);
        doc.setItems(items);

        Connection conn = DBConnection.getInstance().getConnection();
        try {
            conn.setAutoCommit(false);

            int docId = documentDAO.create(conn, doc);
            doc.setId(docId);

            for (DocumentItem item : items) {
                int qty = item.getQuantity().intValue();
                stockDAO.decreaseStock(conn, warehouseFrom.getId(),
                        item.getProduct().getId(), qty);
            }

            conn.commit();
            log.info("Расход оформлен: " + doc.getDocumentNumber()
                    + ", склад=" + warehouseFrom.getName());

            // Обновляем историю потребления для прогнозирования
            for (DocumentItem item : items) {
                forecastDAO.upsertConsumptionHistory(
                        item.getProduct().getId(),
                        LocalDate.now(),
                        item.getQuantity().doubleValue());
            }
            return doc;

        } catch (Exception e) {
            rollback(conn);
            throw new RuntimeException("Ошибка оформления расхода: " + e.getMessage(), e);
        } finally {
            resetAutoCommit(conn);
        }
    }

    // ── 3. Внутреннее перемещение ─────────────────────────────────────────

    /**
     * Перемещает товары между складами.
     */
    public Document processTransfer(Warehouse warehouseFrom, Warehouse warehouseTo,
                                    List<DocumentItem> items, User responsible) {
        if (warehouseFrom == null || warehouseTo == null)
            throw new IllegalArgumentException("Склады отправки и получения обязательны");
        if (warehouseFrom.getId() == warehouseTo.getId())
            throw new IllegalArgumentException("Склад отправки и получения не могут совпадать");
        validate(warehouseFrom, items);

        Document doc = buildDocument(DocumentType.TRANSFER, responsible);
        doc.setWarehouseFrom(warehouseFrom);
        doc.setWarehouseTo(warehouseTo);
        doc.setItems(items);

        Connection conn = DBConnection.getInstance().getConnection();
        try {
            conn.setAutoCommit(false);
            int docId = documentDAO.create(conn, doc);
            doc.setId(docId);

            for (DocumentItem item : items) {
                int qty = item.getQuantity().intValue();
                stockDAO.decreaseStock(conn, warehouseFrom.getId(),
                        item.getProduct().getId(), qty);
                stockDAO.increaseStock(conn, warehouseTo.getId(),
                        item.getProduct().getId(), qty);
            }

            conn.commit();
            log.info("Перемещение оформлено: " + doc.getDocumentNumber());
            return doc;

        } catch (Exception e) {
            rollback(conn);
            throw new RuntimeException("Ошибка перемещения: " + e.getMessage(), e);
        } finally {
            resetAutoCommit(conn);
        }
    }

    // ── 4. Инвентаризация ─────────────────────────────────────────────────

    /**
     * Обрабатывает результаты инвентаризации.
     * Возвращает общий InventoryResult из common.
     */
    public InventoryResult processInventory(Warehouse warehouse,
                                            Map<Integer, Integer> actualQuantities,
                                            User responsible) {
        if (warehouse == null) throw new IllegalArgumentException("Склад обязателен");
        if (actualQuantities == null || actualQuantities.isEmpty())
            throw new IllegalArgumentException("Нет данных для инвентаризации");

        // Получаем учётные остатки
        List<Stock> currentStocks = stockDAO.findByWarehouse(warehouse.getId());
        Map<Integer, Integer> accountingQty = new HashMap<>();
        for (Stock s : currentStocks) {
            accountingQty.put(s.getProductId(), s.getQuantity());
        }

        // Ищем расхождения
        List<DiscrepancyEntry> surpluses  = new ArrayList<>();
        List<DiscrepancyEntry> shortages  = new ArrayList<>();

        for (Map.Entry<Integer, Integer> entry : actualQuantities.entrySet()) {
            int productId = entry.getKey();
            int actual    = entry.getValue();
            int accounting = accountingQty.getOrDefault(productId, 0);
            int diff = actual - accounting;

            DiscrepancyEntry e = new DiscrepancyEntry(productId, accounting, actual, diff);
            if (diff > 0) {
                surpluses.add(e);
            } else if (diff < 0) {
                shortages.add(e);
            }
        }

        if (surpluses.isEmpty() && shortages.isEmpty()) {
            log.info("Инвентаризация: расхождений не выявлено. Склад=" + warehouse.getName());
            return new InventoryResult(null, surpluses, shortages,
                    actualQuantities.size(), 0);
        }

        // Формируем документ инвентаризации
        Document doc = buildDocument(DocumentType.INVENTORY, responsible);
        doc.setWarehouseTo(warehouse);

        List<DocumentItem> allItems = new ArrayList<>();
        for (DiscrepancyEntry e : surpluses)  allItems.add(toItem(e));
        for (DiscrepancyEntry e : shortages)  allItems.add(toItem(e));
        doc.setItems(allItems);

        Connection conn = DBConnection.getInstance().getConnection();
        try {
            conn.setAutoCommit(false);
            int docId = documentDAO.create(conn, doc);
            doc.setId(docId);

            // Корректируем остатки
            for (DiscrepancyEntry e : surpluses) {
                stockDAO.setStock(conn, warehouse.getId(), e.getProductId(), e.getActual());
            }
            for (DiscrepancyEntry e : shortages) {
                stockDAO.setStock(conn, warehouse.getId(), e.getProductId(), e.getActual());
            }

            conn.commit();
            int totalDiscrepancies = surpluses.size() + shortages.size();
            log.info("Инвентаризация проведена: " + doc.getDocumentNumber()
                    + ", расхождений=" + totalDiscrepancies);

            return new InventoryResult(doc, surpluses, shortages,
                    actualQuantities.size(), totalDiscrepancies);

        } catch (Exception e) {
            rollback(conn);
            throw new RuntimeException("Ошибка инвентаризации: " + e.getMessage(), e);
        } finally {
            resetAutoCommit(conn);
        }
    }

    // ── Вспомогательные методы ────────────────────────────────────────────

    private void validate(Warehouse warehouse, List<DocumentItem> items) {
        if (warehouse == null)
            throw new IllegalArgumentException("Склад не выбран");
        if (items == null || items.isEmpty())
            throw new IllegalArgumentException("Список товаров пуст");
        for (DocumentItem item : items) {
            if (item.getProduct() == null)
                throw new IllegalArgumentException("Товар не выбран в позиции документа");
            if (item.getQuantity() == null
                    || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0)
                throw new IllegalArgumentException(
                        "Количество должно быть положительным: "
                                + (item.getProduct() != null ? item.getProduct().getName() : ""));
        }
    }

    private Document buildDocument(DocumentType type, User responsible) {
        String number = type.name().substring(0, 3) + "-"
                + LocalDateTime.now().format(NUM_FMT)
                + "-" + SEQ.getAndIncrement();
        Document doc = new Document(type, number, responsible);
        doc.setDocumentDate(LocalDateTime.now());
        return doc;
    }

    private DocumentItem toItem(DiscrepancyEntry e) {
        DocumentItem item = new DocumentItem();
        Product p = new Product();
        p.setId(e.getProductId());
        item.setProduct(p);
        item.setQuantity(BigDecimal.valueOf(Math.abs(e.getDiff())));
        return item;
    }

    private void updateConsumptionForIncome(List<DocumentItem> items) {
        // Приход не увеличивает расход — ничего не делаем
    }

    private void rollback(Connection conn) {
        try { if (conn != null) conn.rollback(); }
        catch (SQLException ex) { log.severe("Rollback failed: " + ex.getMessage()); }
    }

    private void resetAutoCommit(Connection conn) {
        try { if (conn != null) conn.setAutoCommit(true); }
        catch (SQLException ex) { log.warning("Reset autocommit failed: " + ex.getMessage()); }
    }

    // Внутренние классы DiscrepancyEntry и InventoryResult удалены – используем общие из common.model
}