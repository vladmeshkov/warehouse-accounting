package by.bsuir.warehouse.common.util;

import by.bsuir.warehouse.common.model.*;
import by.bsuir.warehouse.common.protocol.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты модели данных и протокола обмена.
 */
@DisplayName("Model & Protocol — тесты сущностей и протокола")
class ModelProtocolTest {

    // Stock

    @Test
    @DisplayName("Stock.isBelowMinLevel(): qty <= min → true")
    void stock_belowMin_isTrue() {
        Stock s = new Stock(1, 1, 5);
        s.setMinStockLevel(5);
        assertTrue(s.isBelowMinLevel(), "Остаток равен минимуму → критический");
    }

    @Test
    @DisplayName("Stock.isBelowMinLevel(): qty > min → false")
    void stock_aboveMin_isFalse() {
        Stock s = new Stock(1, 1, 10);
        s.setMinStockLevel(5);
        assertFalse(s.isBelowMinLevel());
    }

    @Test
    @DisplayName("Stock.isBelowMinLevel(): qty = 0, min = 0 → true")
    void stock_zeroQuantityZeroMin_isTrue() {
        Stock s = new Stock(1, 1, 0);
        s.setMinStockLevel(0);
        assertTrue(s.isBelowMinLevel());
    }

    // DocumentItem

    @Test
    @DisplayName("DocumentItem.getTotal(): price * quantity корректно")
    void documentItem_getTotal_correctCalculation() {
        Product p = new Product(1, "A", "Товар", "Кат", "шт.", 0);
        DocumentItem item = new DocumentItem(p, new BigDecimal("5"), new BigDecimal("100.50"));
        assertEquals(new BigDecimal("502.50"), item.getTotal());
    }

    @Test
    @DisplayName("DocumentItem.getTotal(): null price → 0")
    void documentItem_nullPrice_totalIsZero() {
        Product p = new Product(1, "A", "Товар", "Кат", "шт.", 0);
        DocumentItem item = new DocumentItem(p, new BigDecimal("5"), null);
        assertEquals(BigDecimal.ZERO, item.getTotal());
    }

    @Test
    @DisplayName("DocumentItem.getTotal(): null quantity → 0")
    void documentItem_nullQuantity_totalIsZero() {
        Product p = new Product(1, "A", "Товар", "Кат", "шт.", 0);
        DocumentItem item = new DocumentItem(p, null, new BigDecimal("100"));
        assertEquals(BigDecimal.ZERO, item.getTotal());
    }

    // DeficitForecast

    @Test
    @DisplayName("DeficitForecast: MAX_VALUE дни → не критический")
    void deficitForecast_maxValueDays_notCritical() {
        DeficitForecast f = new DeficitForecast();
        f.setDaysUntilDeficit(Integer.MAX_VALUE);
        assertFalse(f.isCritical());
        assertFalse(f.isDeficitNow());
    }

    // Request Builder

    @Test
    @DisplayName("Request.Builder: все параметры устанавливаются корректно")
    void requestBuilder_allParams_correct() {
        Product payload = new Product(1, "ART", "Product", "Cat", "шт.", 10);
        Request req = new Request.Builder(Action.CREATE_PRODUCT)
                .token("my-token")
                .param("key1", "value1")
                .param("id", 42)
                .payload(payload)
                .build();

        assertEquals(Action.CREATE_PRODUCT, req.getAction());
        assertEquals("my-token",  req.getToken());
        assertEquals("value1",    req.getParam("key1"));
        assertEquals("42",        req.getParam("id"));
        assertEquals(42,          req.getIntParam("id"));
        assertSame(payload,       req.getPayload());
    }

    @Test
    @DisplayName("Request.getIntParam: несуществующий ключ → 0")
    void request_missingIntParam_returnsZero() {
        Request req = new Request.Builder(Action.GET_ALL_PRODUCTS).build();
        assertEquals(0, req.getIntParam("nonexistent"));
    }

    @Test
    @DisplayName("Request.getParam: несуществующий ключ → null")
    void request_missingParam_returnsNull() {
        Request req = new Request.Builder(Action.GET_ALL_PRODUCTS).build();
        assertNull(req.getParam("nonexistent"));
    }

    // Response factory methods

    @Test
    @DisplayName("Response.ok(data): success=true, data установлен")
    void response_okWithData_correct() {
        Product p = new Product();
        Response r = Response.ok(p);
        assertTrue(r.isSuccess());
        assertEquals("OK", r.getMessage());
        assertSame(p, r.getData());
        assertNull(r.getToken());
    }

    @Test
    @DisplayName("Response.error(msg): success=false, message установлен")
    void response_error_correct() {
        Response r = Response.error("Ошибка доступа");
        assertFalse(r.isSuccess());
        assertEquals("Ошибка доступа", r.getMessage());
        assertNull(r.getData());
    }

    @Test
    @DisplayName("Response.withToken: токен и данные установлены")
    void response_withToken_correct() {
        User user = new User();
        Response r = Response.withToken("abc-token-123", user);
        assertTrue(r.isSuccess());
        assertEquals("abc-token-123", r.getToken());
        assertSame(user, r.getData());
    }

    // ── User.hasRole ──────────────────────────────────────────────────────

    @Test
    @DisplayName("User.hasRole: совпадающая роль → true")
    void user_hasRole_correctRole_true() {
        User u = new User();
        u.setRole(new Role(1, Role.ADMIN));
        assertTrue(u.hasRole(Role.ADMIN));
    }

    @Test
    @DisplayName("User.hasRole: несовпадающая роль → false")
    void user_hasRole_wrongRole_false() {
        User u = new User();
        u.setRole(new Role(2, Role.WAREHOUSE_WORKER));
        assertFalse(u.hasRole(Role.ADMIN));
    }

    @Test
    @DisplayName("User.hasRole: null роль → false")
    void user_hasRole_nullRole_false() {
        User u = new User();
        u.setRole(null);
        assertFalse(u.hasRole(Role.ADMIN));
    }

    // ── ValidationUtil ────────────────────────────────────────────────────

    @Test
    @DisplayName("ValidationUtil.isBlank: null → true")
    void validation_nullIsBlank() {
        assertTrue(ValidationUtil.isBlank(null));
    }

    @Test
    @DisplayName("ValidationUtil.isBlank: пробелы → true")
    void validation_spacesAreBlank() {
        assertTrue(ValidationUtil.isBlank("   "));
    }

    @Test
    @DisplayName("ValidationUtil.isBlank: непустая строка → false")
    void validation_nonEmptyNotBlank() {
        assertFalse(ValidationUtil.isBlank("hello"));
    }

    @Test
    @DisplayName("ValidationUtil.isPositive: положительное значение → true")
    void validation_positiveValue_isPositive() {
        assertTrue(ValidationUtil.isPositive(new BigDecimal("0.01")));
    }

    @Test
    @DisplayName("ValidationUtil.isPositive: ноль → false")
    void validation_zeroNotPositive() {
        assertFalse(ValidationUtil.isPositive(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("ValidationUtil.isValidEmail: корректный email → true")
    void validation_validEmail_true() {
        assertTrue(ValidationUtil.isValidEmail("user@example.com"));
    }

    @Test
    @DisplayName("ValidationUtil.isValidEmail: некорректный email → false")
    void validation_invalidEmail_false() {
        assertFalse(ValidationUtil.isValidEmail("not-an-email"));
    }

    @Test
    @DisplayName("ValidationUtil.isValidEmail: пустой email → true (необязательное поле)")
    void validation_emptyEmail_true() {
        assertTrue(ValidationUtil.isValidEmail(""));
        assertTrue(ValidationUtil.isValidEmail(null));
    }
}
