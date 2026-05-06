package by.bsuir.warehouse.server.dao;

import by.bsuir.warehouse.common.model.AuditEntry;

import java.util.List;

public interface AuditDAO {
    void logAction(int userId, String action, String details);
    List<AuditEntry> getEntries(String actionFilter);
}