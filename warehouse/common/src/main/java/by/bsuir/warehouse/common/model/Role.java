package by.bsuir.warehouse.common.model;

/**
 * Роль пользователя в системе.
 * Определяет набор доступных операций согласно должностным обязанностям.
 */
public class Role extends BaseEntity {

    private static final long serialVersionUID = 1L;

    // Константы имён ролей — используются при проверке прав на сервере
    public static final String ADMIN            = "Администратор";
    public static final String PURCHASE_MANAGER = "Менеджер по закупкам";
    public static final String WAREHOUSE_WORKER = "Кладовщик";
    public static final String SALES_MANAGER    = "Менеджер по продажам";
    public static final String ACCOUNTANT       = "Бухгалтер";

    private String roleName;

    public Role() {}

    public Role(int id, String roleName) {
        super(id);
        this.roleName = roleName;
    }

    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }

    @Override
    public String toString() {
        return roleName;
    }
}
