package by.bsuir.warehouse.server.service;

import by.bsuir.warehouse.common.model.ConsumptionHistory;
import by.bsuir.warehouse.common.model.DeficitForecast;
import by.bsuir.warehouse.server.config.ServerConfig;
import by.bsuir.warehouse.server.dao.ForecastDAO;
import by.bsuir.warehouse.server.dao.StockDAO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Модульные тесты для ForecastService.
 *
 * Тестируют:
 *   1. Формулу экспоненциального сглаживания S(t) = α*X(t) + (1-α)*S(t-1)
 *   2. Расчёт daysUntilDeficit
 *   3. Граничные случаи: нулевой остаток, отсутствие истории, нулевой расход
 *   4. Определение критических и дефицитных прогнозов
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ForecastService — тесты модуля прогнозирования")
class ForecastServiceTest {

    @Mock private ForecastDAO forecastDAO;
    @Mock private StockDAO    stockDAO;

    // ForecastService создаём вручную с подменёнными DAO через рефлексию
    private ForecastService forecastService;

    @BeforeEach
    void setUp() throws Exception {
        forecastService = new ForecastService();
        // Подменяем DAO через reflection (поля private final)
        setField(forecastService, "forecastDAO", forecastDAO);
        setField(forecastService, "stockDAO", stockDAO);
    }

    // ── Тесты формулы экспоненциального сглаживания ───────────────────────

    @Test
    @DisplayName("Экспоненциальное сглаживание: один элемент возвращает сам элемент")
    void smoothing_singleElement_returnsElement() {
        List<ConsumptionHistory> history = List.of(historyEntry(10.0));
        double result = forecastService.computeExponentialSmoothing(history, 0.3);
        assertEquals(10.0, result, 0.001,
                "При одном элементе начальное значение = элемент, результат = элемент");
    }

    @Test
    @DisplayName("Экспоненциальное сглаживание: пустая история возвращает 0")
    void smoothing_emptyHistory_returnsZero() {
        double result = forecastService.computeExponentialSmoothing(new ArrayList<>(), 0.3);
        assertEquals(0.0, result, 0.001);
    }

    @Test
    @DisplayName("Экспоненциальное сглаживание: стабильный расход остаётся стабильным")
    void smoothing_constantConsumption_remainsStable() {
        // Если расход каждый день = 5, сглаженное значение тоже = 5
        List<ConsumptionHistory> history = new ArrayList<>();
        for (int i = 0; i < 30; i++) history.add(historyEntry(5.0));
        double result = forecastService.computeExponentialSmoothing(history, 0.3);
        assertEquals(5.0, result, 0.01,
                "При постоянном расходе 5 сглаженное значение = 5");
    }

    @Test
    @DisplayName("Экспоненциальное сглаживание: растущий расход — прогноз < реального (инертность)")
    void smoothing_increasingConsumption_lagsBehindActual() {
        // Расход растёт с 1 до 30 — из-за инертности прогноз ниже последнего факта
        List<ConsumptionHistory> history = new ArrayList<>();
        for (int i = 1; i <= 30; i++) history.add(historyEntry(i));
        double result = forecastService.computeExponentialSmoothing(history, 0.3);
        // Последний фактический = 30, сглаженное должно быть меньше
        assertTrue(result < 30.0, "Сглаженное значение должно быть меньше последнего факта");
        assertTrue(result > 1.0,  "Сглаженное значение должно быть больше первого факта");
    }

    @Test
    @DisplayName("Экспоненциальное сглаживание: α=1 возвращает последний элемент")
    void smoothing_alphaOne_returnsLastElement() {
        // При α=1 прогноз = последнее наблюдение
        List<ConsumptionHistory> history = List.of(
                historyEntry(10.0), historyEntry(20.0), historyEntry(7.0));
        double result = forecastService.computeExponentialSmoothing(history, 1.0);
        assertEquals(7.0, result, 0.001,
                "При α=1 сглаженное = последнее наблюдение");
    }

    @Test
    @DisplayName("Экспоненциальное сглаживание: α=0 возвращает начальное среднее")
    void smoothing_alphaZero_returnsInitialAverage() {
        // При α=0 прогноз не меняется и равен начальному среднему
        List<ConsumptionHistory> history = List.of(
                historyEntry(10.0), historyEntry(20.0), historyEntry(30.0));
        double initialAvg = (10.0 + 20.0 + 30.0) / 3.0; // = 20.0
        double result = forecastService.computeExponentialSmoothing(history, 0.0);
        assertEquals(initialAvg, result, 0.001,
                "При α=0 сглаженное = начальное среднее и не меняется");
    }

    @Test
    @DisplayName("Экспоненциальное сглаживание: корректная формула для 3 элементов")
    void smoothing_threeElements_correctFormula() {
        // Ручной расчёт:
        // history = [4, 6, 8], α=0.3
        // initialAvg = (4+6+8)/3 = 6.0
        // S1 = 0.3*4 + 0.7*6 = 1.2 + 4.2 = 5.4
        // S2 = 0.3*6 + 0.7*5.4 = 1.8 + 3.78 = 5.58
        // S3 = 0.3*8 + 0.7*5.58 = 2.4 + 3.906 = 6.306
        List<ConsumptionHistory> history = List.of(
                historyEntry(4.0), historyEntry(6.0), historyEntry(8.0));
        double result = forecastService.computeExponentialSmoothing(history, 0.3);
        assertEquals(6.306, result, 0.001, "Формула S(t) = α*X(t) + (1-α)*S(t-1) корректна");
    }

    // ── Тесты расчёта прогноза дефицита ──────────────────────────────────

    @Test
    @DisplayName("calculateDeficitForecast: нулевой остаток → дефицит уже наступил")
    void forecast_zeroStock_deficitNow() {
        when(forecastDAO.findHistory(anyInt(), anyInt())).thenReturn(new ArrayList<>());
        doNothing().when(forecastDAO).upsertForecast(any());

        DeficitForecast result = forecastService.calculateDeficitForecast(1, 1, 0, 5);

        assertEquals(-1, result.getDaysUntilDeficit(),
                "При нулевом остатке дaysUntilDeficit = -1");
        assertTrue(result.isDeficitNow());
        assertFalse(result.isCritical());
    }

    @Test
    @DisplayName("calculateDeficitForecast: нет истории расхода → дни = MAX_VALUE")
    void forecast_noHistory_daysMaxValue() {
        when(forecastDAO.findHistory(anyInt(), anyInt())).thenReturn(new ArrayList<>());
        doNothing().when(forecastDAO).upsertForecast(any());

        DeficitForecast result = forecastService.calculateDeficitForecast(1, 1, 100, 10);

        assertEquals(Integer.MAX_VALUE, result.getDaysUntilDeficit(),
                "При нулевом расходе дней до дефицита = MAX_VALUE");
    }

    @Test
    @DisplayName("calculateDeficitForecast: корректный расчёт дней при достаточной истории")
    void forecast_sufficientHistory_correctDays() {
        // 7+ дней истории, расход = 5 ед/день, остаток = 60, минимум = 10
        // ожидаем: (60-10) / 5 = 10 дней
        List<ConsumptionHistory> history = new ArrayList<>();
        for (int i = 0; i < 10; i++) history.add(historyEntry(5.0));
        when(forecastDAO.findHistory(anyInt(), anyInt())).thenReturn(history);
        doNothing().when(forecastDAO).upsertForecast(any());

        DeficitForecast result = forecastService.calculateDeficitForecast(1, 1, 60, 10);

        assertTrue(result.getDaysUntilDeficit() > 0,
                "Должны быть положительные дни до дефицита");
        assertNotNull(result.getEstimatedDeficitDate(),
                "Дата дефицита должна быть установлена");
        assertTrue(result.getEstimatedDeficitDate().isAfter(LocalDate.now()),
                "Дата дефицита должна быть в будущем");
    }

    @Test
    @DisplayName("calculateDeficitForecast: критический прогноз (≤7 дней)")
    void forecast_criticalRange_isCritical() {
        // Расход 9 ед/день, остаток = 70, минимум = 10
        // (70-10) / 9 ≈ 6.6 → 6 дней → критический
        List<ConsumptionHistory> history = new ArrayList<>();
        for (int i = 0; i < 10; i++) history.add(historyEntry(9.0));
        when(forecastDAO.findHistory(anyInt(), anyInt())).thenReturn(history);
        doNothing().when(forecastDAO).upsertForecast(any());

        DeficitForecast result = forecastService.calculateDeficitForecast(1, 1, 70, 10);

        assertTrue(result.getDaysUntilDeficit() <= 7, "Прогноз должен быть критическим");
        assertTrue(result.isCritical(), "isCritical() должен вернуть true");
        assertFalse(result.isDeficitNow(), "Дефицит ещё не наступил");
    }

    @Test
    @DisplayName("calculateDeficitForecast: недостаточно истории — используется простое среднее")
    void forecast_insufficientHistory_usesSimpleAverage() {
        // 3 дня истории (< 7 минимума) → используем простое среднее
        List<ConsumptionHistory> history = List.of(
                historyEntry(3.0), historyEntry(5.0), historyEntry(7.0));
        when(forecastDAO.findHistory(anyInt(), anyInt())).thenReturn(history);
        doNothing().when(forecastDAO).upsertForecast(any());

        DeficitForecast result = forecastService.calculateDeficitForecast(1, 1, 50, 5);

        // avg = (3+5+7)/3 = 5.0, days = (50-5)/5 = 9
        assertNotNull(result);
        assertTrue(result.getDaysUntilDeficit() > 0);
    }

    @Test
    @DisplayName("calculateDeficitForecast: остаток уже ниже минимума → отрицательные дни")
    void forecast_stockBelowMin_negativeDays() {
        List<ConsumptionHistory> history = new ArrayList<>();
        for (int i = 0; i < 10; i++) history.add(historyEntry(5.0));
        when(forecastDAO.findHistory(anyInt(), anyInt())).thenReturn(history);
        doNothing().when(forecastDAO).upsertForecast(any());

        // Остаток = 3, минимум = 10 → (3-10)/5 = -1.4 → -2 дня (floor)
        DeficitForecast result = forecastService.calculateDeficitForecast(1, 1, 3, 10);

        assertTrue(result.getDaysUntilDeficit() < 0,
                "Остаток ниже минимума → отрицательные дни");
    }

    @Test
    @DisplayName("calculateDeficitForecast: прогноз сохраняется в DAO")
    void forecast_alwaysSavesToDAO() {
        when(forecastDAO.findHistory(anyInt(), anyInt())).thenReturn(new ArrayList<>());
        doNothing().when(forecastDAO).upsertForecast(any());

        forecastService.calculateDeficitForecast(1, 1, 50, 5);

        verify(forecastDAO, times(1)).upsertForecast(any(DeficitForecast.class));
    }

    // ── Тесты модели DeficitForecast ──────────────────────────────────────

    @Test
    @DisplayName("DeficitForecast.isCritical(): дни = 0 → критический")
    void deficitForecast_zeroDays_isCritical() {
        DeficitForecast f = new DeficitForecast();
        f.setDaysUntilDeficit(0);
        assertTrue(f.isCritical());
    }

    @Test
    @DisplayName("DeficitForecast.isCritical(): дни = 7 → критический (граница)")
    void deficitForecast_sevenDays_isCritical() {
        DeficitForecast f = new DeficitForecast();
        f.setDaysUntilDeficit(7);
        assertTrue(f.isCritical());
    }

    @Test
    @DisplayName("DeficitForecast.isCritical(): дни = 8 → не критический")
    void deficitForecast_eightDays_notCritical() {
        DeficitForecast f = new DeficitForecast();
        f.setDaysUntilDeficit(8);
        assertFalse(f.isCritical());
    }

    @Test
    @DisplayName("DeficitForecast.isDeficitNow(): дни = -1 → дефицит сейчас")
    void deficitForecast_negativeDays_isDeficitNow() {
        DeficitForecast f = new DeficitForecast();
        f.setDaysUntilDeficit(-1);
        assertTrue(f.isDeficitNow());
        assertFalse(f.isCritical());
    }

    // ── Вспомогательные методы ────────────────────────────────────────────

    private ConsumptionHistory historyEntry(double quantity) {
        ConsumptionHistory h = new ConsumptionHistory();
        h.setProductId(1);
        h.setConsumptionDate(LocalDate.now());
        h.setTotalQuantity(BigDecimal.valueOf(quantity));
        return h;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private java.lang.reflect.Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        try { return clazz.getDeclaredField(name); }
        catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null) return findField(clazz.getSuperclass(), name);
            throw e;
        }
    }
}
