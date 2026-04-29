package by.bsuir.warehouse.server.dao.impl;

import by.bsuir.warehouse.common.model.ConsumptionHistory;
import by.bsuir.warehouse.common.model.DeficitForecast;
import by.bsuir.warehouse.server.config.DBConnection;
import by.bsuir.warehouse.server.dao.ForecastDAO;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class ForecastDAOImpl implements ForecastDAO {

    private static final Logger log = Logger.getLogger(ForecastDAOImpl.class.getName());

    private Connection conn() { return DBConnection.getInstance().getConnection(); }

    @Override
    public List<ConsumptionHistory> findHistory(int productId, int days) {
        List<ConsumptionHistory> list = new ArrayList<>();
        String sql = "SELECT history_id, product_id, consumption_date, total_quantity " +
                     "FROM consumption_history " +
                     "WHERE product_id=? AND consumption_date >= DATE_SUB(CURDATE(), INTERVAL ? DAY) " +
                     "ORDER BY consumption_date ASC";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setInt(2, days);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ConsumptionHistory h = new ConsumptionHistory();
                    h.setId(rs.getInt("history_id"));
                    h.setProductId(rs.getInt("product_id"));
                    h.setConsumptionDate(rs.getDate("consumption_date").toLocalDate());
                    h.setTotalQuantity(rs.getBigDecimal("total_quantity"));
                    list.add(h);
                }
            }
        } catch (SQLException e) {
            log.severe("findHistory: " + e.getMessage());
        }
        return list;
    }

    @Override
    public void upsertConsumptionHistory(int productId, LocalDate date, double quantity) {
        String sql = "INSERT INTO consumption_history (product_id, consumption_date, total_quantity) " +
                     "VALUES (?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE total_quantity = total_quantity + ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setDate(2, Date.valueOf(date));
            ps.setDouble(3, quantity);
            ps.setDouble(4, quantity);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.severe("upsertConsumptionHistory: " + e.getMessage());
        }
    }

    @Override
    public List<DeficitForecast> findLatestForecasts() {
        String sql = "SELECT f.forecast_id, f.warehouse_id, f.product_id, f.forecast_date, " +
                     "f.avg_daily_consumption, f.days_until_deficit, " +
                     "f.estimated_deficit_date, f.last_calculated, " +
                     "p.name AS product_name, p.article AS product_article, " +
                     "w.name AS warehouse_name, " +
                     "COALESCE(s.quantity, 0) AS current_stock, " +
                     "p.min_stock_level " +
                     "FROM deficit_forecast f " +
                     "JOIN product p ON f.product_id = p.product_id " +
                     "JOIN warehouse w ON f.warehouse_id = w.warehouse_id " +
                     "LEFT JOIN stock s ON s.product_id = f.product_id " +
                     "                 AND s.warehouse_id = f.warehouse_id " +
                     "WHERE (f.warehouse_id, f.product_id, f.forecast_date) IN ( " +
                     "  SELECT warehouse_id, product_id, MAX(forecast_date) " +
                     "  FROM deficit_forecast GROUP BY warehouse_id, product_id " +
                     ") " +
                     "ORDER BY f.days_until_deficit ASC";
        return queryForecasts(sql, null);
    }

    @Override
    public List<DeficitForecast> findForecastsByWarehouse(int warehouseId) {
        String sql = "SELECT f.forecast_id, f.warehouse_id, f.product_id, f.forecast_date, " +
                     "f.avg_daily_consumption, f.days_until_deficit, " +
                     "f.estimated_deficit_date, f.last_calculated, " +
                     "p.name AS product_name, p.article AS product_article, " +
                     "w.name AS warehouse_name, " +
                     "COALESCE(s.quantity, 0) AS current_stock, " +
                     "p.min_stock_level " +
                     "FROM deficit_forecast f " +
                     "JOIN product p ON f.product_id = p.product_id " +
                     "JOIN warehouse w ON f.warehouse_id = w.warehouse_id " +
                     "LEFT JOIN stock s ON s.product_id = f.product_id " +
                     "                 AND s.warehouse_id = f.warehouse_id " +
                     "WHERE f.warehouse_id=? " +
                     "AND (f.warehouse_id, f.product_id, f.forecast_date) IN ( " +
                     "  SELECT warehouse_id, product_id, MAX(forecast_date) " +
                     "  FROM deficit_forecast GROUP BY warehouse_id, product_id " +
                     ") " +
                     "ORDER BY f.days_until_deficit ASC";
        return queryForecasts(sql, warehouseId);
    }

    @Override
    public void upsertForecast(DeficitForecast f) {
        String sql = "INSERT INTO deficit_forecast " +
                     "(warehouse_id, product_id, forecast_date, avg_daily_consumption, " +
                     " days_until_deficit, estimated_deficit_date) " +
                     "VALUES (?,?,?,?,?,?) " +
                     "ON DUPLICATE KEY UPDATE " +
                     "avg_daily_consumption=?, days_until_deficit=?, estimated_deficit_date=?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, f.getWarehouseId());
            ps.setInt(2, f.getProductId());
            ps.setDate(3, Date.valueOf(f.getForecastDate()));
            ps.setBigDecimal(4, f.getAvgDailyConsumption());
            ps.setInt(5, f.getDaysUntilDeficit());
            if (f.getEstimatedDeficitDate() != null)
                ps.setDate(6, Date.valueOf(f.getEstimatedDeficitDate()));
            else ps.setNull(6, Types.DATE);
            // ON DUPLICATE KEY UPDATE values
            ps.setBigDecimal(7, f.getAvgDailyConsumption());
            ps.setInt(8, f.getDaysUntilDeficit());
            if (f.getEstimatedDeficitDate() != null)
                ps.setDate(9, Date.valueOf(f.getEstimatedDeficitDate()));
            else ps.setNull(9, Types.DATE);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.severe("upsertForecast: " + e.getMessage());
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private List<DeficitForecast> queryForecasts(String sql, Integer warehouseId) {
        List<DeficitForecast> list = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            if (warehouseId != null) ps.setInt(1, warehouseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapForecast(rs));
            }
        } catch (SQLException e) {
            log.severe("queryForecasts: " + e.getMessage());
        }
        return list;
    }

    private DeficitForecast mapForecast(ResultSet rs) throws SQLException {
        DeficitForecast f = new DeficitForecast();
        f.setId(rs.getInt("forecast_id"));
        f.setWarehouseId(rs.getInt("warehouse_id"));
        f.setProductId(rs.getInt("product_id"));
        f.setForecastDate(rs.getDate("forecast_date").toLocalDate());
        f.setAvgDailyConsumption(rs.getBigDecimal("avg_daily_consumption"));
        f.setDaysUntilDeficit(rs.getInt("days_until_deficit"));
        Date defDate = rs.getDate("estimated_deficit_date");
        if (defDate != null) f.setEstimatedDeficitDate(defDate.toLocalDate());
        Timestamp lc = rs.getTimestamp("last_calculated");
        if (lc != null) f.setLastCalculated(lc.toLocalDateTime());
        f.setProductName(rs.getString("product_name"));
        f.setProductArticle(rs.getString("product_article"));
        f.setWarehouseName(rs.getString("warehouse_name"));
        f.setCurrentStock(rs.getInt("current_stock"));
        f.setMinStockLevel(rs.getInt("min_stock_level"));
        return f;
    }
}
