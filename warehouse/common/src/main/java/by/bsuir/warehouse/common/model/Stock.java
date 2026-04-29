package by.bsuir.warehouse.common.model;

import java.io.Serializable;

/**
 * Остаток товара на конкретном складе.
 * Первичный ключ — составной: (warehouseId, productId).
 */
public class Stock implements Serializable {

    private static final long serialVersionUID = 1L;

    private int warehouseId;
    private int productId;
    private int quantity;

    // Денормализованные поля — для удобства отображения в клиенте
    private String productName;
    private String productArticle;
    private String productUnit;
    private String warehouseName;
    private int minStockLevel;

    public Stock() {}

    public Stock(int warehouseId, int productId, int quantity) {
        this.warehouseId = warehouseId;
        this.productId   = productId;
        this.quantity    = quantity;
    }

    public int getWarehouseId()              { return warehouseId; }
    public void setWarehouseId(int v)        { this.warehouseId = v; }

    public int getProductId()                { return productId; }
    public void setProductId(int v)          { this.productId = v; }

    public int getQuantity()                 { return quantity; }
    public void setQuantity(int v)           { this.quantity = v; }

    public String getProductName()           { return productName; }
    public void setProductName(String v)     { this.productName = v; }

    public String getProductArticle()        { return productArticle; }
    public void setProductArticle(String v)  { this.productArticle = v; }

    public String getProductUnit()           { return productUnit; }
    public void setProductUnit(String v)     { this.productUnit = v; }

    public String getWarehouseName()         { return warehouseName; }
    public void setWarehouseName(String v)   { this.warehouseName = v; }

    public int getMinStockLevel()            { return minStockLevel; }
    public void setMinStockLevel(int v)      { this.minStockLevel = v; }

    /** Возвращает true, если остаток ниже или равен минимальному порогу */
    public boolean isBelowMinLevel() {
        return quantity <= minStockLevel;
    }
}
