package by.bsuir.warehouse.server.dao.impl;

import by.bsuir.warehouse.common.model.Stock;
import by.bsuir.warehouse.server.config.DBConnection;
import by.bsuir.warehouse.server.dao.StockDAO;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

public class StockDAOImpl implements StockDAO {

    private static final Logger log = Logger.getLogger(StockDAOImpl.class.getName());

    private Connection conn() { return DBConnection.getInstance().getConnection(); }

    @Override
    public List<Stock> findAll() {
        return query(
            "SELECT s.warehouse_id, s.product_id, s.quantity, " +
            "p.name AS product_name, p.article AS product_article, p.unit AS product_unit, " +
            "p.min_stock_level, w.name AS warehouse_name " +
            "FROM stock s " +
            "JOIN product p ON s.product_id = p.product_id " +
            "JOIN warehouse w ON s.warehouse_id = w.warehouse_id " +
            "ORDER BY w.name, p.name", null);
    }

    @Override
    public List<Stock> findByWarehouse(int warehouseId) {
        return query(
            "SELECT s.warehouse_id, s.product_id, s.quantity, " +
            "p.name AS product_name, p.article AS product_article, p.unit AS product_unit, " +
            "p.min_stock_level, w.name AS warehouse_name " +
            "FROM stock s " +
            "JOIN product p ON s.product_id = p.product_id " +
            "JOIN warehouse w ON s.warehouse_id = w.warehouse_id " +
            "WHERE s.warehouse_id = ? ORDER BY p.name", warehouseId);
    }

    @Override
    public Optional<Stock> findByWarehouseAndProduct(int warehouseId, int productId) {
        String sql = "SELECT s.warehouse_id, s.product_id, s.quantity, " +
                     "p.name AS product_name, p.article AS product_article, p.unit AS product_unit, " +
                     "p.min_stock_level, w.name AS warehouse_name " +
                     "FROM stock s " +
                     "JOIN product p ON s.product_id = p.product_id " +
                     "JOIN warehouse w ON s.warehouse_id = w.warehouse_id " +
                     "WHERE s.warehouse_id=? AND s.product_id=?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, warehouseId);
            ps.setInt(2, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            log.severe("findByWarehouseAndProduct: " + e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public void increaseStock(Connection conn, int warehouseId, int productId, int quantity) {
        String sql = "INSERT INTO stock (warehouse_id, product_id, quantity) VALUES (?,?,?) " +
                     "ON DUPLICATE KEY UPDATE quantity = quantity + ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, warehouseId);
            ps.setInt(2, productId);
            ps.setInt(3, quantity);
            ps.setInt(4, quantity);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("increaseStock failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void decreaseStock(Connection conn, int warehouseId, int productId, int quantity) {
        // Сначала проверяем что хватает товара
        String checkSql = "SELECT quantity FROM stock WHERE warehouse_id=? AND product_id=? FOR UPDATE";
        try (PreparedStatement check = conn.prepareStatement(checkSql)) {
            check.setInt(1, warehouseId);
            check.setInt(2, productId);
            try (ResultSet rs = check.executeQuery()) {
                if (!rs.next() || rs.getInt("quantity") < quantity) {
                    throw new RuntimeException(
                        "Недостаточно товара на складе (склад=" + warehouseId +
                        ", товар=" + productId + ", нужно=" + quantity + ")");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("decreaseStock check failed: " + e.getMessage(), e);
        }

        String sql = "UPDATE stock SET quantity = quantity - ? " +
                     "WHERE warehouse_id=? AND product_id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, quantity);
            ps.setInt(2, warehouseId);
            ps.setInt(3, productId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("decreaseStock update failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void setStock(Connection conn, int warehouseId, int productId, int quantity) {
        String sql = "INSERT INTO stock (warehouse_id, product_id, quantity) VALUES (?,?,?) " +
                     "ON DUPLICATE KEY UPDATE quantity = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, warehouseId);
            ps.setInt(2, productId);
            ps.setInt(3, quantity);
            ps.setInt(4, quantity);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("setStock failed: " + e.getMessage(), e);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private List<Stock> query(String sql, Integer warehouseId) {
        List<Stock> list = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            if (warehouseId != null) ps.setInt(1, warehouseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        } catch (SQLException e) {
            log.severe("stock query: " + e.getMessage());
        }
        return list;
    }

    private Stock map(ResultSet rs) throws SQLException {
        Stock s = new Stock();
        s.setWarehouseId(rs.getInt("warehouse_id"));
        s.setProductId(rs.getInt("product_id"));
        s.setQuantity(rs.getInt("quantity"));
        s.setProductName(rs.getString("product_name"));
        s.setProductArticle(rs.getString("product_article"));
        s.setProductUnit(rs.getString("product_unit"));
        s.setMinStockLevel(rs.getInt("min_stock_level"));
        s.setWarehouseName(rs.getString("warehouse_name"));
        return s;
    }
}
