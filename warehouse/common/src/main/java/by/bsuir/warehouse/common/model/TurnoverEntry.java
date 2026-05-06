package by.bsuir.warehouse.common.model;

import java.io.Serializable;
import java.math.BigDecimal;

public class TurnoverEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private int productId;
    private String article;
    private String productName;
    private String unit;
    private BigDecimal incomeQty;
    private BigDecimal outcomeQty;
    private int currentStock;

    public TurnoverEntry() {}

    // Геттеры и сеттеры
    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public String getArticle() { return article; }
    public void setArticle(String article) { this.article = article; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public BigDecimal getIncomeQty() { return incomeQty; }
    public void setIncomeQty(BigDecimal incomeQty) { this.incomeQty = incomeQty; }

    public BigDecimal getOutcomeQty() { return outcomeQty; }
    public void setOutcomeQty(BigDecimal outcomeQty) { this.outcomeQty = outcomeQty; }

    public int getCurrentStock() { return currentStock; }
    public void setCurrentStock(int currentStock) { this.currentStock = currentStock; }

    /** Коэффициент оборачиваемости = расход / средний остаток (упрощённо) */
    public String getTurnoverRatio() {
        if (outcomeQty == null || outcomeQty.compareTo(BigDecimal.ZERO) == 0) return "0.00";
        double avgStock = currentStock + (incomeQty != null ? incomeQty.doubleValue() : 0.0);
        if (avgStock == 0) return "0.00";
        double ratio = outcomeQty.doubleValue() / avgStock;
        return String.format("%.2f", ratio);
    }
}