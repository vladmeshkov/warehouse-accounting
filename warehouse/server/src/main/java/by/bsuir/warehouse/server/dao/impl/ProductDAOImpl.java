package by.bsuir.warehouse.server.dao.impl;

import by.bsuir.warehouse.common.model.Product;
import by.bsuir.warehouse.server.config.DBConnection;
import by.bsuir.warehouse.server.dao.ProductDAO;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

public class ProductDAOImpl implements ProductDAO {

    private static final Logger log = Logger.getLogger(ProductDAOImpl.class.getName());

    private Connection conn() {
        return DBConnection.getInstance().getConnection();
    }

    @Override
    public List<Product> findAll() {
        List<Product> list = new ArrayList<>();
        String sql = "SELECT product_id, article, name, category, unit, " +
                     "purchase_price, selling_price, min_stock_level, description " +
                     "FROM product ORDER BY name";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) {
            log.severe("findAll products: " + e.getMessage());
        }
        return list;
    }

    @Override
    public Optional<Product> findById(int id) {
        String sql = "SELECT product_id, article, name, category, unit, " +
                     "purchase_price, selling_price, min_stock_level, description " +
                     "FROM product WHERE product_id = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            log.severe("findById product: " + e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public Optional<Product> findByArticle(String article) {
        String sql = "SELECT product_id, article, name, category, unit, " +
                     "purchase_price, selling_price, min_stock_level, description " +
                     "FROM product WHERE article = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, article);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            log.severe("findByArticle: " + e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public List<Product> findByCategory(String category) {
        List<Product> list = new ArrayList<>();
        String sql = "SELECT product_id, article, name, category, unit, " +
                     "purchase_price, selling_price, min_stock_level, description " +
                     "FROM product WHERE category = ? ORDER BY name";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, category);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        } catch (SQLException e) {
            log.severe("findByCategory: " + e.getMessage());
        }
        return list;
    }

    @Override
    public int create(Product p) {
        String sql = "INSERT INTO product (article, name, category, unit, " +
                     "purchase_price, selling_price, min_stock_level, description) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn().prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, p.getArticle());
            ps.setString(2, p.getName());
            ps.setString(3, p.getCategory());
            ps.setString(4, p.getUnit());
            setBigDecimal(ps, 5, p.getPurchasePrice());
            setBigDecimal(ps, 6, p.getSellingPrice());
            ps.setInt(7, p.getMinStockLevel());
            ps.setString(8, p.getDescription());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        } catch (SQLException e) {
            log.severe("create product: " + e.getMessage());
        }
        return -1;
    }

    @Override
    public boolean update(Product p) {
        String sql = "UPDATE product SET article=?, name=?, category=?, unit=?, " +
                     "purchase_price=?, selling_price=?, min_stock_level=?, description=? " +
                     "WHERE product_id=?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, p.getArticle());
            ps.setString(2, p.getName());
            ps.setString(3, p.getCategory());
            ps.setString(4, p.getUnit());
            setBigDecimal(ps, 5, p.getPurchasePrice());
            setBigDecimal(ps, 6, p.getSellingPrice());
            ps.setInt(7, p.getMinStockLevel());
            ps.setString(8, p.getDescription());
            ps.setInt(9, p.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.severe("update product: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean delete(int id) {
        try (PreparedStatement ps = conn().prepareStatement(
                "DELETE FROM product WHERE product_id=?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.severe("delete product: " + e.getMessage());
            return false;
        }
    }

    // ── Маппинг ResultSet → Product ───────────────────────────────────────

    private Product map(ResultSet rs) throws SQLException {
        Product p = new Product();
        p.setId(rs.getInt("product_id"));
        p.setArticle(rs.getString("article"));
        p.setName(rs.getString("name"));
        p.setCategory(rs.getString("category"));
        p.setUnit(rs.getString("unit"));
        p.setPurchasePrice(rs.getBigDecimal("purchase_price"));
        p.setSellingPrice(rs.getBigDecimal("selling_price"));
        p.setMinStockLevel(rs.getInt("min_stock_level"));
        p.setDescription(rs.getString("description"));
        return p;
    }

    private void setBigDecimal(PreparedStatement ps, int idx, BigDecimal v)
            throws SQLException {
        if (v != null) ps.setBigDecimal(idx, v);
        else           ps.setNull(idx, Types.DECIMAL);
    }
}
