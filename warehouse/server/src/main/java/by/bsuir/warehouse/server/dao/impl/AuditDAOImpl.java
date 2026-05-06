package by.bsuir.warehouse.server.dao.impl;

import by.bsuir.warehouse.common.model.AuditEntry;
import by.bsuir.warehouse.server.config.DBConnection;
import by.bsuir.warehouse.server.dao.AuditDAO;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class AuditDAOImpl implements AuditDAO {

    private static final Logger log = Logger.getLogger(AuditDAOImpl.class.getName());

    private Connection conn() { return DBConnection.getInstance().getConnection(); }

    @Override
    public void logAction(int userId, String action, String details) {
        String sql = "INSERT INTO audit_log (user_id, action_type, details) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, action);
            ps.setString(3, details);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.severe("Ошибка записи аудита: " + e.getMessage());
        }
    }

    @Override
    public List<AuditEntry> getEntries(String actionFilter) {
        List<AuditEntry> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT a.id, a.user_id, a.action_time, a.action_type, a.details, u.full_name " +
                        "FROM audit_log a JOIN user u ON a.user_id = u.user_id ");
        if (actionFilter != null && !actionFilter.isEmpty() && !"ALL".equalsIgnoreCase(actionFilter)) {
            sql.append("WHERE a.action_type = ? ");
        }
        sql.append("ORDER BY a.action_time DESC LIMIT 500");

        try (PreparedStatement ps = conn().prepareStatement(sql.toString())) {
            if (actionFilter != null && !actionFilter.isEmpty() && !"ALL".equalsIgnoreCase(actionFilter)) {
                ps.setString(1, actionFilter);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AuditEntry entry = new AuditEntry();
                    entry.setId(rs.getInt("id"));
                    entry.setUserId(rs.getInt("user_id"));
                    entry.setTimestamp(rs.getTimestamp("action_time").toLocalDateTime());
                    entry.setAction(rs.getString("action_type"));
                    entry.setDetails(rs.getString("details"));
                    entry.setUserFullName(rs.getString("full_name"));
                    list.add(entry);
                }
            }
        } catch (SQLException e) {
            log.severe("Ошибка чтения аудита: " + e.getMessage());
        }
        return list;
    }
}