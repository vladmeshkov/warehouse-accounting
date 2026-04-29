package by.bsuir.warehouse.common.model;

/**
 * Покупатель / клиент.
 */
public class Customer extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private String name;
    private String contactPerson;
    private String phone;
    private String email;
    private String address;

    public Customer() {}

    public Customer(int id, String name) {
        super(id);
        this.name = name;
    }

    public String getName()                   { return name; }
    public void setName(String v)             { this.name = v; }

    public String getContactPerson()          { return contactPerson; }
    public void setContactPerson(String v)    { this.contactPerson = v; }

    public String getPhone()                  { return phone; }
    public void setPhone(String v)            { this.phone = v; }

    public String getEmail()                  { return email; }
    public void setEmail(String v)            { this.email = v; }

    public String getAddress()                { return address; }
    public void setAddress(String v)          { this.address = v; }

    @Override
    public String toString() { return name; }
}
