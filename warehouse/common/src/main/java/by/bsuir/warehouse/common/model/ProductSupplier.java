package by.bsuir.warehouse.common.model;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Связь товар–поставщик с ценой поставки и сроком доставки.
 * Составной PK: (productId, supplierId).
 */
public class ProductSupplier implements Serializable {

    private static final long serialVersionUID = 1L;

    private int productId;
    private int supplierId;
    private BigDecimal supplyPrice;
    private int deliveryDays;

    // Денормализованные поля
    private String supplierName;
    private String productName;

    public ProductSupplier() {}

    public ProductSupplier(int productId, int supplierId,
                           BigDecimal supplyPrice, int deliveryDays) {
        this.productId    = productId;
        this.supplierId   = supplierId;
        this.supplyPrice  = supplyPrice;
        this.deliveryDays = deliveryDays;
    }

    public int getProductId()                 { return productId; }
    public void setProductId(int v)           { this.productId = v; }

    public int getSupplierId()                { return supplierId; }
    public void setSupplierId(int v)          { this.supplierId = v; }

    public BigDecimal getSupplyPrice()        { return supplyPrice; }
    public void setSupplyPrice(BigDecimal v)  { this.supplyPrice = v; }

    public int getDeliveryDays()              { return deliveryDays; }
    public void setDeliveryDays(int v)        { this.deliveryDays = v; }

    public String getSupplierName()           { return supplierName; }
    public void setSupplierName(String v)     { this.supplierName = v; }

    public String getProductName()            { return productName; }
    public void setProductName(String v)      { this.productName = v; }
}
