package by.bsuir.warehouse.common.model;

/**
 * Склад — место хранения товарно-материальных ценностей.
 */
public class Warehouse extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private String name;
    private String address;
    private User responsibleUser;   // материально-ответственное лицо

    public Warehouse() {}

    public Warehouse(int id, String name, String address) {
        super(id);
        this.name    = name;
        this.address = address;
    }

    public String getName()                      { return name; }
    public void setName(String v)                { this.name = v; }

    public String getAddress()                   { return address; }
    public void setAddress(String v)             { this.address = v; }

    public User getResponsibleUser()             { return responsibleUser; }
    public void setResponsibleUser(User user)    { this.responsibleUser = user; }

    @Override
    public String toString() { return name; }
}
