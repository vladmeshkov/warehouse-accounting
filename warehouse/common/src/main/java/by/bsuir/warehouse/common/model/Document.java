package by.bsuir.warehouse.common.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Документ движения товаров (заголовок).
 * Содержит список позиций DocumentItem.
 */
public class Document extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private DocumentType documentType;
    private String documentNumber;
    private LocalDateTime documentDate;
    private Warehouse warehouseFrom;   // склад-отправитель (OUTCOME, TRANSFER)
    private Warehouse warehouseTo;     // склад-получатель (INCOME, TRANSFER)
    private Supplier supplier;         // для INCOME
    private Customer customer;         // для OUTCOME
    private User responsibleUser;
    private String comment;
    private List<DocumentItem> items = new ArrayList<>();

    public Document() {}

    public Document(DocumentType documentType, String documentNumber,
                    User responsibleUser) {
        this.documentType   = documentType;
        this.documentNumber = documentNumber;
        this.responsibleUser = responsibleUser;
        this.documentDate   = LocalDateTime.now();
    }

    public void addItem(DocumentItem item) {
        items.add(item);
    }

    // ---- getters / setters ----

    public DocumentType getDocumentType()           { return documentType; }
    public void setDocumentType(DocumentType v)     { this.documentType = v; }

    public String getDocumentNumber()               { return documentNumber; }
    public void setDocumentNumber(String v)         { this.documentNumber = v; }

    public LocalDateTime getDocumentDate()          { return documentDate; }
    public void setDocumentDate(LocalDateTime v)    { this.documentDate = v; }

    public Warehouse getWarehouseFrom()             { return warehouseFrom; }
    public void setWarehouseFrom(Warehouse v)       { this.warehouseFrom = v; }

    public Warehouse getWarehouseTo()               { return warehouseTo; }
    public void setWarehouseTo(Warehouse v)         { this.warehouseTo = v; }

    public Supplier getSupplier()                   { return supplier; }
    public void setSupplier(Supplier v)             { this.supplier = v; }

    public Customer getCustomer()                   { return customer; }
    public void setCustomer(Customer v)             { this.customer = v; }

    public User getResponsibleUser()                { return responsibleUser; }
    public void setResponsibleUser(User v)          { this.responsibleUser = v; }

    public String getComment()                      { return comment; }
    public void setComment(String v)                { this.comment = v; }

    public List<DocumentItem> getItems()            { return items; }
    public void setItems(List<DocumentItem> items)  { this.items = items; }

    @Override
    public String toString() {
        return documentType.getDisplayName() + " №" + documentNumber;
    }
}
