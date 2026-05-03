package by.bsuir.warehouse.client.controller;

import by.bsuir.warehouse.common.model.Document;

import java.io.Serializable;

// ═══════════════════════════════════════════════════════════════════════════════
// InventoryResult — локальный DTO для разбора ответа сервера
// ═══════════════════════════════════════════════════════════════════════════════
class InventoryResult implements Serializable {
    public Document document;
    public int totalChecked;
    public int totalDiscrepancies;
}
