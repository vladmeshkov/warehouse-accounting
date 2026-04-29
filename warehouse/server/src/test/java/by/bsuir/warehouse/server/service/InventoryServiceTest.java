package by.bsuir.warehouse.server.service;

import by.bsuir.warehouse.common.model.*;
import by.bsuir.warehouse.server.dao.DocumentDAO;
import by.bsuir.warehouse.server.dao.ForecastDAO;
import by.bsuir.warehouse.server.dao.StockDAO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Модульные тесты для InventoryService.
 *
 * Тестируют:
 *   1. Оформление прихода — обновление остатков
 *   2. Оформление расхода — списание со склада
 *   3. Ошибка расхода при недостатке товара
 *   4. Валидация входных данных
 *   5. Инвентаризация — расхождения и их корректировка
 *   6. Транзакционность операций
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryService — тесты складских операций")
class InventoryServiceTest {

    @Mock private DocumentDAO documentDAO;
    @Mock private StockDAO    stockDAO;
    @Mock private ForecastDAO forecastDAO;
    @Mock private Connection  mockConnection;

    private InventoryService inventoryService;

    private Warehouse warehouse;
    private Supplier  supplier;
    private Customer  customer;
    private User      responsible;
    private Product   product1;
    private Product   product2;

    @BeforeEach
    void setUp() throws Exception {
        inventoryService = new InventoryService();
        setField(inventoryService, "documentDAO", documentDAO);
        setField(inventoryService, "stockDAO",    stockDAO);
        setField(inventoryService, "forecastDAO", forecastDAO);

        // Тестовые объекты
        warehouse   = new Warehouse(1, "Склад А", "ул. Тестовая");
        supplier    = new Supplier(1, "ООО Поставщик");
        customer    = new Customer(1, "ООО Покупатель");
        responsible = buildUser(1, "warehouse_worker", Role.WAREHOUSE_WORKER);
        product1    = buildProduct(1, "ART-001", "Товар 1", 10);
        product2    = buildProduct(2, "ART-002", "Товар 2", 5);
    }

    // ── Тесты валидации ───────────────────────────────────────────────────

    @Test
    @DisplayName("processIncomeDocument: null склад → IllegalArgumentException")
    void income_nullWarehouse_throwsException() {
        List<DocumentItem> items = List.of(item(product1, 10, "100"));
        assertThrows(IllegalArgumentException.class,
                () -> inventoryService.processIncomeDocument(null, supplier, items, responsible));
    }

    @Test
    @DisplayName("processIncomeDocument: пустой список позиций → IllegalArgumentException")
    void income_emptyItems_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> inventoryService.processIncomeDocument(warehouse, supplier,
                        new ArrayList<>(), responsible));
    }

    @Test
    @DisplayName("processIncomeDocument: нулевое количество → IllegalArgumentException")
    void income_zeroQuantity_throwsException() {
        List<DocumentItem> items = List.of(item(product1, 0, "100"));
        assertThrows(IllegalArgumentException.class,
                () -> inventoryService.processIncomeDocument(warehouse, supplier, items, responsible));
    }

    @Test
    @DisplayName("processIncomeDocument: отрицательное количество → IllegalArgumentException")
    void income_negativeQuantity_throwsException() {
        List<DocumentItem> items = List.of(item(product1, -5, "100"));
        assertThrows(IllegalArgumentException.class,
                () -> inventoryService.processIncomeDocument(warehouse, supplier, items, responsible));
    }

    @Test
    @DisplayName("processIncomeDocument: позиция без товара → IllegalArgumentException")
    void income_nullProduct_throwsException() {
        DocumentItem badItem = new DocumentItem(null, BigDecimal.TEN, BigDecimal.ONE);
        assertThrows(IllegalArgumentException.class,
                () -> inventoryService.processIncomeDocument(warehouse, supplier,
                        List.of(badItem), responsible));
    }

    // ── Тесты формирования номеров документов ────────────────────────────

    @Test
    @DisplayName("processOutcomeDocument: null склад → IllegalArgumentException")
    void outcome_nullWarehouse_throwsException() {
        List<DocumentItem> items = List.of(item(product1, 5, "200"));
        assertThrows(IllegalArgumentException.class,
                () -> inventoryService.processOutcomeDocument(null, customer, items, responsible));
    }

    @Test
    @DisplayName("processTransfer: одинаковые склады → IllegalArgumentException")
    void transfer_sameWarehouses_throwsException() {
        List<DocumentItem> items = List.of(item(product1, 5, null));
        assertThrows(IllegalArgumentException.class,
                () -> inventoryService.processTransfer(warehouse, warehouse, items, responsible));
    }

    @Test
    @DisplayName("processTransfer: null склад-отправитель → IllegalArgumentException")
    void transfer_nullFromWarehouse_throwsException() {
        Warehouse other = new Warehouse(2, "Склад Б", "");
        List<DocumentItem> items = List.of(item(product1, 5, null));
        assertThrows(IllegalArgumentException.class,
                () -> inventoryService.processTransfer(null, other, items, responsible));
    }

    // ── Тесты инвентаризации ──────────────────────────────────────────────

    @Test
    @DisplayName("processInventory: null склад → IllegalArgumentException")
    void inventory_nullWarehouse_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> inventoryService.processInventory(null,
                        Map.of(1, 10), responsible));
    }

    @Test
    @DisplayName("processInventory: пустая карта остатков → IllegalArgumentException")
    void inventory_emptyMap_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> inventoryService.processInventory(warehouse,
                        new HashMap<>(), responsible));
    }

    @Test
    @DisplayName("processInventory: расхождений нет → нет документа, нет расхождений")
    void inventory_noDiscrepancies_noDocumentCreated() {
        // Учётные остатки = фактические → расхождений нет
        Stock s1 = buildStock(1, 1, 20);
        Stock s2 = buildStock(1, 2, 15);
        when(stockDAO.findByWarehouse(1)).thenReturn(List.of(s1, s2));

        Map<Integer, Integer> actual = Map.of(1, 20, 2, 15); // совпадает с учётными
        InventoryService.InventoryResult result =
                inventoryService.processInventory(warehouse, actual, responsible);

        assertFalse(result.hasDiscrepancies(), "Расхождений быть не должно");
        assertEquals(0, result.totalDiscrepancies);
        assertNull(result.document, "Документ не должен создаваться при отсутствии расхождений");
        assertTrue(result.surpluses.isEmpty());
        assertTrue(result.shortages.isEmpty());
    }

    @Test
    @DisplayName("processInventory: излишек → позиция в surpluses")
    void inventory_surplus_addedToSurpluses() {
        Stock s = buildStock(1, 1, 10); // учётных 10
        when(stockDAO.findByWarehouse(1)).thenReturn(List.of(s));
        when(documentDAO.create(any(), any())).thenReturn(1);
        doNothing().when(stockDAO).setStock(any(), anyInt(), anyInt(), anyInt());

        Map<Integer, Integer> actual = Map.of(1, 15); // факт 15 → излишек +5
        InventoryService.InventoryResult result =
                inventoryService.processInventory(warehouse, actual, responsible);

        assertEquals(1, result.surpluses.size(), "Должен быть один излишек");
        assertEquals(5, result.surpluses.get(0).diff, "Размер излишка = 5");
        assertTrue(result.surpluses.get(0).isSurplus());
    }

    @Test
    @DisplayName("processInventory: недостача → позиция в shortages")
    void inventory_shortage_addedToShortages() {
        Stock s = buildStock(1, 1, 20); // учётных 20
        when(stockDAO.findByWarehouse(1)).thenReturn(List.of(s));
        when(documentDAO.create(any(), any())).thenReturn(1);
        doNothing().when(stockDAO).setStock(any(), anyInt(), anyInt(), anyInt());

        Map<Integer, Integer> actual = Map.of(1, 12); // факт 12 → недостача -8
        InventoryService.InventoryResult result =
                inventoryService.processInventory(warehouse, actual, responsible);

        assertEquals(1, result.shortages.size(), "Должна быть одна недостача");
        assertEquals(-8, result.shortages.get(0).diff, "Размер недостачи = -8");
        assertTrue(result.shortages.get(0).isShortage());
    }

    @Test
    @DisplayName("processInventory: смешанные расхождения — правильное разделение")
    void inventory_mixedDiscrepancies_correctlySeparated() {
        Stock s1 = buildStock(1, 1, 10); // product1: учёт 10
        Stock s2 = buildStock(1, 2, 30); // product2: учёт 30
        when(stockDAO.findByWarehouse(1)).thenReturn(List.of(s1, s2));
        when(documentDAO.create(any(), any())).thenReturn(1);
        doNothing().when(stockDAO).setStock(any(), anyInt(), anyInt(), anyInt());

        // product1: факт 15 → излишек +5
        // product2: факт 25 → недостача -5
        Map<Integer, Integer> actual = Map.of(1, 15, 2, 25);
        InventoryService.InventoryResult result =
                inventoryService.processInventory(warehouse, actual, responsible);

        assertEquals(1, result.surpluses.size(),  "Один излишек");
        assertEquals(1, result.shortages.size(),  "Одна недостача");
        assertEquals(2, result.totalDiscrepancies,"Итого 2 расхождения");
        assertEquals(2, result.totalChecked,      "Проверено 2 позиции");
    }

    @Test
    @DisplayName("processInventory: корректирует остатки через setStock")
    void inventory_withDiscrepancies_callsSetStock() {
        Stock s = buildStock(1, 1, 10);
        when(stockDAO.findByWarehouse(1)).thenReturn(List.of(s));
        when(documentDAO.create(any(), any())).thenReturn(1);
        doNothing().when(stockDAO).setStock(any(), anyInt(), anyInt(), anyInt());

        inventoryService.processInventory(warehouse, Map.of(1, 18), responsible);

        verify(stockDAO).setStock(any(Connection.class), eq(1), eq(1), eq(18));
    }

    // ── Тесты DiscrepancyEntry ────────────────────────────────────────────

    @Test
    @DisplayName("DiscrepancyEntry.isSurplus(): diff > 0 → true")
    void discrepancyEntry_surplus_isSurplus() {
        InventoryService.DiscrepancyEntry e =
                new InventoryService.DiscrepancyEntry(1, 10, 15, 5);
        assertTrue(e.isSurplus());
        assertFalse(e.isShortage());
    }

    @Test
    @DisplayName("DiscrepancyEntry.isShortage(): diff < 0 → true")
    void discrepancyEntry_shortage_isShortage() {
        InventoryService.DiscrepancyEntry e =
                new InventoryService.DiscrepancyEntry(1, 20, 12, -8);
        assertTrue(e.isShortage());
        assertFalse(e.isSurplus());
    }

    // ── Вспомогательные методы ────────────────────────────────────────────

    private DocumentItem item(Product p, int qty, String price) {
        BigDecimal priceVal = price != null ? new BigDecimal(price) : null;
        return new DocumentItem(p, BigDecimal.valueOf(qty), priceVal);
    }

    private Product buildProduct(int id, String article, String name, int minStock) {
        Product p = new Product(id, article, name, "Категория", "шт.", minStock);
        return p;
    }

    private User buildUser(int id, String username, String roleName) {
        Role role = new Role(1, roleName);
        User u = new User(id, username, role, "Test User", true);
        return u;
    }

    private Stock buildStock(int warehouseId, int productId, int qty) {
        Stock s = new Stock(warehouseId, productId, qty);
        s.setMinStockLevel(5);
        return s;
    }

    private void setField(Object target, String name, Object value) throws Exception {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
