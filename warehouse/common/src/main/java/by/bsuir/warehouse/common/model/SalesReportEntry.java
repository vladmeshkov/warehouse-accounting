package by.bsuir.warehouse.common.model;

import java.io.Serializable;
import java.math.BigDecimal;

public class SalesReportEntry implements Serializable {
    private static final long serialVersionUID = 1L;

    private String productName;
    private String article;
    private BigDecimal totalQuantity;
    private BigDecimal avgPrice;
    private BigDecimal totalCost;

    public SalesReportEntry() {}

    public SalesReportEntry(String productName, String article, BigDecimal totalQuantity,
                            BigDecimal avgPrice, BigDecimal totalCost) {
        this.productName = productName;
        this.article = article;
        this.totalQuantity = totalQuantity;
        this.avgPrice = avgPrice;
        this.totalCost = totalCost;
    }

    // Геттеры и сеттеры
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getArticle() { return article; }
    public void setArticle(String article) { this.article = article; }

    public BigDecimal getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(BigDecimal totalQuantity) { this.totalQuantity = totalQuantity; }

    public BigDecimal getAvgPrice() { return avgPrice; }
    public void setAvgPrice(BigDecimal avgPrice) { this.avgPrice = avgPrice; }

    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }
}