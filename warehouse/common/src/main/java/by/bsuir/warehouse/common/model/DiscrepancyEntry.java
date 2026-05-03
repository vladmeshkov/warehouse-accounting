package by.bsuir.warehouse.common.model;

import java.io.Serializable;

/**
 * Позиция расхождения при инвентаризации (общий для клиента и сервера).
 */
public class DiscrepancyEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private int productId;
    private int accounting;   // учётный остаток
    private int actual;       // фактический остаток
    private int diff;         // разница (+ излишек, - недостача)
    private String productName;
    private String productArticle;

    public DiscrepancyEntry() {}

    public DiscrepancyEntry(int productId, int accounting, int actual, int diff) {
        this.productId = productId;
        this.accounting = accounting;
        this.actual = actual;
        this.diff = diff;
    }

    public int getProductId() { return productId; }
    public int getAccounting() { return accounting; }
    public int getActual() { return actual; }
    public int getDiff() { return diff; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getProductArticle() { return productArticle; }
    public void setProductArticle(String productArticle) { this.productArticle = productArticle; }

    public boolean isSurplus() { return diff > 0; }
    public boolean isShortage() { return diff < 0; }
}