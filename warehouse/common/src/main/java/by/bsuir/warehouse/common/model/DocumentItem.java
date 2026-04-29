package by.bsuir.warehouse.common.model;

import java.math.BigDecimal;

/**
 * Позиция (строка) документа движения.
 */
public class DocumentItem extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private int documentId;
    private Product product;
    private BigDecimal quantity;
    private BigDecimal price;

    public DocumentItem() {}

    public DocumentItem(Product product, BigDecimal quantity, BigDecimal price) {
        this.product  = product;
        this.quantity = quantity;
        this.price    = price;
    }

    public int getDocumentId()               { return documentId; }
    public void setDocumentId(int v)         { this.documentId = v; }

    public Product getProduct()              { return product; }
    public void setProduct(Product v)        { this.product = v; }

    public BigDecimal getQuantity()          { return quantity; }
    public void setQuantity(BigDecimal v)    { this.quantity = v; }

    public BigDecimal getPrice()             { return price; }
    public void setPrice(BigDecimal v)       { this.price = v; }

    /** Стоимость позиции = цена × количество */
    public BigDecimal getTotal() {
        if (price == null || quantity == null) return BigDecimal.ZERO;
        return price.multiply(quantity);
    }
}
