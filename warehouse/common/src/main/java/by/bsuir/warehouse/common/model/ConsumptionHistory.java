package by.bsuir.warehouse.common.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Агрегированный суточный расход товара.
 * Используется модулем прогнозирования дефицита (ForecastService).
 */
public class ConsumptionHistory extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private int productId;
    private LocalDate consumptionDate;
    private BigDecimal totalQuantity;

    public ConsumptionHistory() {}

    public ConsumptionHistory(int productId, LocalDate consumptionDate,
                              BigDecimal totalQuantity) {
        this.productId       = productId;
        this.consumptionDate = consumptionDate;
        this.totalQuantity   = totalQuantity;
    }

    public int getProductId()                    { return productId; }
    public void setProductId(int v)              { this.productId = v; }

    public LocalDate getConsumptionDate()        { return consumptionDate; }
    public void setConsumptionDate(LocalDate v)  { this.consumptionDate = v; }

    public BigDecimal getTotalQuantity()         { return totalQuantity; }
    public void setTotalQuantity(BigDecimal v)   { this.totalQuantity = v; }
}
