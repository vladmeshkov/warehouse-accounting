package by.bsuir.warehouse.server.service;

import by.bsuir.warehouse.common.model.ConsumptionHistory;
import by.bsuir.warehouse.common.model.DeficitForecast;
import by.bsuir.warehouse.common.model.Stock;
import by.bsuir.warehouse.server.config.ServerConfig;
import by.bsuir.warehouse.server.dao.ForecastDAO;
import by.bsuir.warehouse.server.dao.StockDAO;
import by.bsuir.warehouse.server.dao.impl.ForecastDAOImpl;
import by.bsuir.warehouse.server.dao.impl.StockDAOImpl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Сервис прогнозирования дефицита товарных запасов.
 *
 * Алгоритм соответствует схеме на рисунке 3.5 и формуле из раздела 3.5
 * пояснительной записки:
 *
 *   S(t) = α × X(t) + (1 − α) × S(t−1)
 *
 *   где:
 *     S(t)   — сглаженный среднесуточный расход на день t
 *     X(t)   — фактический расход за день t
 *     S(t−1) — сглаженное значение за предыдущий день
 *     α      — коэффициент сглаживания (по умолчанию 0.3 из config.properties)
 *
 * Прогнозируемая дата дефицита:
 *   daysUntilDeficit = (currentStock − minStockLevel) / S(t)
 *
 * Автоматический пересчёт запускается ScheduledExecutorService ежедневно
 * в час, указанный в forecast.schedule.hour конфигурации.
 */
public class ForecastService {

    private static final Logger log = Logger.getLogger(ForecastService.class.getName());

    private final ForecastDAO forecastDAO;
    private final StockDAO    stockDAO;
    private final ServerConfig config;
    private final ScheduledExecutorService scheduler;

    public ForecastService() {
        this.forecastDAO = new ForecastDAOImpl();
        this.stockDAO    = new StockDAOImpl();
        this.config      = ServerConfig.getInstance();
        this.scheduler   = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "forecast-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    // ── Запуск планировщика ───────────────────────────────────────────────

    /**
     * Запускает ежедневный автоматический пересчёт прогнозов.
     * Первый запуск — через вычисленную задержку до нужного часа.
     */
    public void startScheduler() {
        long initialDelaySec = computeInitialDelay();
        long periodSec = TimeUnit.DAYS.toSeconds(1);
        scheduler.scheduleAtFixedRate(
                this::recalculateAll,
                initialDelaySec, periodSec, TimeUnit.SECONDS);
        log.info("Планировщик прогнозирования запущен. " +
                 "Первый запуск через " + (initialDelaySec / 60) + " мин.");
    }

    public void stopScheduler() {
        scheduler.shutdownNow();
    }

    // ── Публичный API ─────────────────────────────────────────────────────

    /** Пересчитывает прогнозы для всех пар склад-товар */
    public void recalculateAll() {
        log.info("Начало пересчёта прогнозов дефицита...");
        List<Stock> allStocks = stockDAO.findAll();
        int count = 0;
        for (Stock stock : allStocks) {
            try {
                calculateDeficitForecast(stock.getWarehouseId(),
                                         stock.getProductId(),
                                         stock.getQuantity(),
                                         stock.getMinStockLevel());
                count++;
            } catch (Exception e) {
                log.warning("Ошибка расчёта прогноза для product="
                        + stock.getProductId() + ": " + e.getMessage());
            }
        }
        log.info("Пересчёт прогнозов завершён. Обработано позиций: " + count);
    }

    /** Пересчитывает прогноз для конкретной пары склад-товар */
    public DeficitForecast recalculateForProduct(int warehouseId, int productId) {
        Stock stock = stockDAO
                .findByWarehouseAndProduct(warehouseId, productId)
                .orElseThrow(() -> new RuntimeException(
                        "Остаток не найден: склад=" + warehouseId + ", товар=" + productId));
        return calculateDeficitForecast(warehouseId, productId,
                stock.getQuantity(), stock.getMinStockLevel());
    }

    /** Возвращает последние прогнозы по всем позициям */
    public List<DeficitForecast> getLatestForecasts() {
        return forecastDAO.findLatestForecasts();
    }

    /** Возвращает прогнозы по конкретному складу */
    public List<DeficitForecast> getForecastsByWarehouse(int warehouseId) {
        return forecastDAO.findForecastsByWarehouse(warehouseId);
    }

    // ── Основной алгоритм прогнозирования ────────────────────────────────

    /**
     * Рассчитывает прогноз дефицита методом экспоненциального сглаживания.
     *
     * Алгоритм (по схеме рис. 3.5):
     * 1. Получить текущий остаток. Если 0 — дефицит уже наступил.
     * 2. Запросить историю расхода за последние N дней.
     * 3. Если данных достаточно — рассчитать сглаженный расход.
     *    Иначе — использовать расход по умолчанию.
     * 4. Вычислить daysUntilDeficit = (stock - minLevel) / avgConsumption.
     * 5. Если < 0 — немедленное предупреждение.
     * 6. Если < criticalDays — создать уведомление.
     * 7. Сохранить прогноз в БД.
     */
    DeficitForecast calculateDeficitForecast(int warehouseId, int productId,
                                             int currentStock, int minStockLevel) {
        double alpha       = config.getForecastAlpha();
        int    minHistory  = config.getForecastMinHistoryDays();
        int    windowDays  = config.getForecastHistoryWindowDays();
        int    criticalDays= config.getForecastCriticalDays();

        DeficitForecast forecast = new DeficitForecast();
        forecast.setWarehouseId(warehouseId);
        forecast.setProductId(productId);
        forecast.setForecastDate(LocalDate.now());
        forecast.setCurrentStock(currentStock);
        forecast.setMinStockLevel(minStockLevel);

        // Шаг 1: Если остаток = 0 — дефицит уже наступил
        if (currentStock <= 0) {
            forecast.setAvgDailyConsumption(BigDecimal.ZERO);
            forecast.setDaysUntilDeficit(-1);
            forecast.setEstimatedDeficitDate(LocalDate.now());
            forecastDAO.upsertForecast(forecast);
            return forecast;
        }

        // Шаг 2: Получаем историю расхода
        List<ConsumptionHistory> history = forecastDAO.findHistory(productId, windowDays);

        double avgConsumption;

        if (history.size() < minHistory) {
            // Шаг 3а: Недостаточно данных — используем простое среднее или 0
            if (history.isEmpty()) {
                avgConsumption = 0.0;
            } else {
                double total = history.stream()
                        .mapToDouble(h -> h.getTotalQuantity().doubleValue())
                        .sum();
                avgConsumption = total / history.size();
            }
        } else {
            // Шаг 3б: Экспоненциальное сглаживание
            avgConsumption = computeExponentialSmoothing(history, alpha);
        }

        // Шаг 4: Количество дней до дефицита
        int daysUntilDeficit;
        LocalDate estimatedDate;

        if (avgConsumption <= 0.0) {
            // Расход нулевой — дефицит не прогнозируется
            daysUntilDeficit = Integer.MAX_VALUE;
            estimatedDate = null;
        } else {
            double daysDouble = (currentStock - minStockLevel) / avgConsumption;
            daysUntilDeficit = (int) Math.floor(daysDouble);
            estimatedDate = LocalDate.now().plusDays(Math.max(0, daysUntilDeficit));
        }

        forecast.setAvgDailyConsumption(
                BigDecimal.valueOf(avgConsumption).setScale(3, RoundingMode.HALF_UP));
        forecast.setDaysUntilDeficit(daysUntilDeficit);
        forecast.setEstimatedDeficitDate(estimatedDate);

        // Шаг 5-6: Логирование предупреждений
        if (daysUntilDeficit < 0) {
            log.warning("ДЕФИЦИТ НАСТУПИЛ: склад=" + warehouseId
                    + ", товар=" + productId + ", остаток=" + currentStock);
        } else if (daysUntilDeficit <= criticalDays) {
            log.warning("КРИТИЧЕСКИЙ ОСТАТОК: склад=" + warehouseId
                    + ", товар=" + productId
                    + ", дней до дефицита=" + daysUntilDeficit
                    + ", дата=" + estimatedDate);
        }

        // Шаг 7: Сохраняем прогноз
        forecastDAO.upsertForecast(forecast);
        return forecast;
    }

    /**
     * Вычисляет сглаженный среднесуточный расход методом экспоненциального сглаживания.
     *
     * Рекуррентная формула: S(t) = α × X(t) + (1 − α) × S(t−1)
     *
     * Начальное значение S(0) = среднее арифметическое по всей истории.
     */
    double computeExponentialSmoothing(List<ConsumptionHistory> history, double alpha) {
        if (history.isEmpty()) return 0.0;

        // Начальное значение — среднее за весь период
        double initialAvg = history.stream()
                .mapToDouble(h -> h.getTotalQuantity().doubleValue())
                .average()
                .orElse(0.0);

        double smoothed = initialAvg;
        for (ConsumptionHistory h : history) {
            double xt = h.getTotalQuantity().doubleValue();
            smoothed = alpha * xt + (1.0 - alpha) * smoothed;
        }
        return smoothed;
    }

    /** Возвращает список критических прогнозов (дефицит ≤ criticalDays) */
    public List<DeficitForecast> getCriticalForecasts() {
        int criticalDays = config.getForecastCriticalDays();
        List<DeficitForecast> all = forecastDAO.findLatestForecasts();
        List<DeficitForecast> critical = new ArrayList<>();
        for (DeficitForecast f : all) {
            if (f.getDaysUntilDeficit() <= criticalDays) {
                critical.add(f);
            }
        }
        return critical;
    }

    // ── Вспомогательные методы ────────────────────────────────────────────

    private long computeInitialDelay() {
        int targetHour = config.getForecastScheduleHour();
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime next = now.toLocalDate().atTime(targetHour, 0);
        if (!next.isAfter(now)) next = next.plusDays(1);
        return java.time.Duration.between(now, next).getSeconds();
    }
}
