package by.bsuir.warehouse.common.model;

/**
 * Тип документа движения товаров.
 */
public enum DocumentType {
    INCOME("Приходная накладная"),
    OUTCOME("Расходная накладная"),
    TRANSFER("Внутреннее перемещение"),
    INVENTORY("Инвентаризация");

    private final String displayName;

    DocumentType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }

    @Override
    public String toString() { return displayName; }
}
