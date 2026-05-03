package by.bsuir.warehouse.common.model;

import java.io.Serializable;
import java.util.Map;

/**
 * Запрос на проведение инвентаризации.
 * Используется и клиентом, и сервером.
 */
public class InventoryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private Warehouse warehouse;
    private Map<Integer, Integer> actualQuantities;

    public InventoryRequest() {}

    public InventoryRequest(Warehouse warehouse, Map<Integer, Integer> actualQuantities) {
        this.warehouse = warehouse;
        this.actualQuantities = actualQuantities;
    }

    public Warehouse getWarehouse() { return warehouse; }
    public void setWarehouse(Warehouse warehouse) { this.warehouse = warehouse; }

    public Map<Integer, Integer> getActualQuantities() { return actualQuantities; }
    public void setActualQuantities(Map<Integer, Integer> actualQuantities) { this.actualQuantities = actualQuantities; }
}