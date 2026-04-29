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
     * Алгоритм соответствует схеме на рисунке 3.4 пояснительной записки.
     *
     * @param warehouseTo  склад назначения
     * @param supplier     поставщик
     * @param items        список позиций {товар, количество, цена}
     * @param responsible  пользователь, оформляющий документ
     * @return             созданный документ с присвоенным ID
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

            // Обновляем историю потребления (не влияет на транзакцию)
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
     * Проверяет достаточность остатков перед списанием.
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
                // decreaseStock бросает исключение если недостаточно товара
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
     * Алгоритм соответствует схеме на рисунке 3.6 пояснительной записки.
     *
     * @param warehouse       проверяемый склад
     * @param actualQuantities карта {productId → фактическое количество}
     * @param responsible      пользователь, проводящий инвентаризацию
     * @return InventoryResult с актом расхождений
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

            if (diff > 0) {
                surpluses.add(new DiscrepancyEntry(productId, accounting, actual, diff));
            } else if (diff < 0) {
                shortages.add(new DiscrepancyEntry(productId, accounting, actual, diff));
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
                stockDAO.setStock(conn, warehouse.getId(), e.productId, e.actual);
            }
            for (DiscrepancyEntry e : shortages) {
                stockDAO.setStock(conn, warehouse.getId(), e.productId, e.actual);
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
        p.setId(e.productId);
        item.setProduct(p);
        item.setQuantity(BigDecimal.valueOf(Math.abs(e.diff)));
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

    // ── Вложенные классы результатов ──────────────────────────────────────

    /** Позиция расхождения при инвентаризации */
    public static class DiscrepancyEntry implements java.io.Serializable {
        public final int productId;
        public final int accounting; // учётный остаток
        public final int actual;     // фактический остаток
        public final int diff;       // разница (+ излишек, - недостача)
        // Заполняется контроллером из Product
        public String productName;
        public String productArticle;

        public DiscrepancyEntry(int productId, int accounting, int actual, int diff) {
            this.productId  = productId;
            this.accounting = accounting;
            this.actual     = actual;
            this.diff       = diff;
        }

        public boolean isSurplus()  { return diff > 0; }
        public boolean isShortage() { return diff < 0; }
    }

    /** Результат инвентаризации */
    public static class InventoryResult implements java.io.Serializable {
        public final Document document;
        public final List<DiscrepancyEntry> surpluses;
        public final List<DiscrepancyEntry> shortages;
        public final int totalChecked;
        public final int totalDiscrepancies;

        public InventoryResult(Document document,
                               List<DiscrepancyEntry> surpluses,
                               List<DiscrepancyEntry> shortages,
                               int totalChecked, int totalDiscrepancies) {
            this.document           = document;
            this.surpluses          = surpluses;
            this.shortages          = shortages;
            this.totalChecked       = totalChecked;
            this.totalDiscrepancies = totalDiscrepancies;
        }

        public boolean hasDiscrepancies() { return totalDiscrepancies > 0; }
    }
}
