package by.bsuir.warehouse.common.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Прогноз дефицита для конкретного товара на конкретном складе.
 * Рассчитывается ForecastService методом экспоненциального сглаживания (α=0.3).
 */
public class DeficitForecast extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private int warehouseId;
    private int productId;
    private LocalDate forecastDate;
    private BigDecimal avgDailyConsumption;  // прогнозный среднесуточный расход
    private int daysUntilDeficit;            // дней до достижения min_stock_level
    private LocalDate estimatedDeficitDate;  // расчётная дата дефицита
    private LocalDateTime lastCalculated;

    // Денормализованные поля для удобства отображения
    private String productName;
    private String productArticle;
    private String warehouseName;
    private int currentStock;
    private int minStockLevel;

    public DeficitForecast() {}

    // ---- getters / setters ----

    public int getWarehouseId()                      { return warehouseId; }
    public void setWarehouseId(int v)                { this.warehouseId = v; }

    public int getProductId()                        { return productId; }
    public void setProductId(int v)                  { this.productId = v; }

    public LocalDate getForecastDate()               { return forecastDate; }
    public void setForecastDate(LocalDate v)         { this.forecastDate = v; }

    public BigDecimal getAvgDailyConsumption()       { return avgDailyConsumption; }
    public void setAvgDailyConsumption(BigDecimal v) { this.avgDailyConsumption = v; }

    public int getDaysUntilDeficit()                 { return daysUntilDeficit; }
    public void setDaysUntilDeficit(int v)           { this.daysUntilDeficit = v; }

    public LocalDate getEstimatedDeficitDate()       { return estimatedDeficitDate; }
    public void setEstimatedDeficitDate(LocalDate v) { this.estimatedDeficitDate = v; }

    public LocalDateTime getLastCalculated()         { return lastCalculated; }
    public void setLastCalculated(LocalDateTime v)   { this.lastCalculated = v; }

    public String getProductName()                   { return productName; }
    public void setProductName(String v)             { this.productName = v; }

    public String getProductArticle()                { return productArticle; }
    public void setProductArticle(String v)          { this.productArticle = v; }

    public String getWarehouseName()                 { return warehouseName; }
    public void setWarehouseName(String v)           { this.warehouseName = v; }

    public int getCurrentStock()                     { return currentStock; }
    public void setCurrentStock(int v)               { this.currentStock = v; }

    public int getMinStockLevel()                    { return minStockLevel; }
    public void setMinStockLevel(int v)              { this.minStockLevel = v; }

    /** true — дефицит наступит в ближайшие 7 дней (критический уровень) */
    public boolean isCritical() {
        return daysUntilDeficit >= 0 && daysUntilDeficit <= 7;
    }

    /** true — дефицит уже наступил (текущий остаток ≤ минимального) */
    public boolean isDeficitNow() {
        return daysUntilDeficit < 0;
    }
}
