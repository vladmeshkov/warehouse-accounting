package by.bsuir.warehouse.common.model;

import java.io.Serializable;
import java.util.List;

/**
 * Результат инвентаризации (общий для клиента и сервера).
 */
public class InventoryResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private Document document;
    private List<DiscrepancyEntry> surpluses;
    private List<DiscrepancyEntry> shortages;
    private int totalChecked;
    private int totalDiscrepancies;

    public InventoryResult() {}

    public InventoryResult(Document document,
                           List<DiscrepancyEntry> surpluses,
                           List<DiscrepancyEntry> shortages,
                           int totalChecked, int totalDiscrepancies) {
        this.document = document;
        this.surpluses = surpluses;
        this.shortages = shortages;
        this.totalChecked = totalChecked;
        this.totalDiscrepancies = totalDiscrepancies;
    }

    public Document getDocument() { return document; }
    public List<DiscrepancyEntry> getSurpluses() { return surpluses; }
    public List<DiscrepancyEntry> getShortages() { return shortages; }
    public int getTotalChecked() { return totalChecked; }
    public int getTotalDiscrepancies() { return totalDiscrepancies; }

    public boolean hasDiscrepancies() { return totalDiscrepancies > 0; }
}