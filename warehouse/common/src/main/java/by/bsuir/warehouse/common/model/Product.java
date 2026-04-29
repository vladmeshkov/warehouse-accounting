package by.bsuir.warehouse.common.model;

import java.math.BigDecimal;

/**
 * Товар (позиция номенклатуры).
 * Содержит все характеристики товарной единицы, включая минимальный
 * пороговый остаток для модуля прогнозирования дефицита.
 */
public class Product extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private String article;       // уникальный артикул
    private String name;
    private String category;
    private String unit;          // шт., кг, л, ...
    private BigDecimal purchasePrice;
    private BigDecimal sellingPrice;
    private int minStockLevel;    // пороговый остаток для предупреждения
    private String description;

    public Product() {}

    public Product(int id, String article, String name, String category,
                   String unit, int minStockLevel) {
        super(id);
        this.article       = article;
        this.name          = name;
        this.category      = category;
        this.unit          = unit;
        this.minStockLevel = minStockLevel;
    }

    // ---- getters / setters ----

    public String getArticle()                   { return article; }
    public void setArticle(String v)             { this.article = v; }

    public String getName()                      { return name; }
    public void setName(String v)                { this.name = v; }

    public String getCategory()                  { return category; }
    public void setCategory(String v)            { this.category = v; }

    public String getUnit()                      { return unit; }
    public void setUnit(String v)                { this.unit = v; }

    public BigDecimal getPurchasePrice()         { return purchasePrice; }
    public void setPurchasePrice(BigDecimal v)   { this.purchasePrice = v; }

    public BigDecimal getSellingPrice()          { return sellingPrice; }
    public void setSellingPrice(BigDecimal v)    { this.sellingPrice = v; }

    public int getMinStockLevel()                { return minStockLevel; }
    public void setMinStockLevel(int v)          { this.minStockLevel = v; }

    public String getDescription()               { return description; }
    public void setDescription(String v)         { this.description = v; }

    @Override
    public String toString() {
        return "[" + article + "] " + name;
    }
}
