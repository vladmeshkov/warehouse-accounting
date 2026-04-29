package by.bsuir.warehouse.server.dao.impl;

import by.bsuir.warehouse.common.model.Warehouse;
import by.bsuir.warehouse.server.config.DBConnection;
import by.bsuir.warehouse.server.dao.WarehouseDAO;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

public class WarehouseDAOImpl implements WarehouseDAO {

    private static final Logger log = Logger.getLogger(WarehouseDAOImpl.class.getName());

    private Connection conn() { return DBConnection.getInstance().getConnection(); }

    @Override
    public List<Warehouse> findAll() {
        List<Warehouse> list = new ArrayList<>();
        String sql = "SELECT warehouse_id, name, address, responsible_user_id " +
                     "FROM warehouse ORDER BY name";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) {
            log.severe("findAll warehouses: " + e.getMessage());
        }
        return list;
    }

    @Override
    public Optional<Warehouse> findById(int id) {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT warehouse_id, name, address, responsible_user_id " +
                "FROM warehouse WHERE warehouse_id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            log.severe("findById warehouse: " + e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public int create(Warehouse w) {
        try (PreparedStatement ps = conn().prepareStatement(
                "INSERT INTO warehouse (name, address, responsible_user_id) VALUES (?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, w.getName());
            ps.setString(2, w.getAddress());
            if (w.getResponsibleUser() != null)
                ps.setInt(3, w.getResponsibleUser().getId());
            else
                ps.setNull(3, Types.INTEGER);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        } catch (SQLException e) {
            log.severe("create warehouse: " + e.getMessage());
        }
        return -1;
    }

    @Override
    public boolean update(Warehouse w) {
        try (PreparedStatement ps = conn().prepareStatement(
                "UPDATE warehouse SET name=?, address=?, responsible_user_id=? " +
                "WHERE warehouse_id=?")) {
            ps.setString(1, w.getName());
            ps.setString(2, w.getAddress());
            if (w.getResponsibleUser() != null)
                ps.setInt(3, w.getResponsibleUser().getId());
            else
                ps.setNull(3, Types.INTEGER);
            ps.setInt(4, w.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.severe("update warehouse: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean delete(int id) {
        try (PreparedStatement ps = conn().prepareStatement(
                "DELETE FROM warehouse WHERE warehouse_id=?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.severe("delete warehouse: " + e.getMessage());
            return false;
        }
    }

    private Warehouse map(ResultSet rs) throws SQLException {
        Warehouse w = new Warehouse();
        w.setId(rs.getInt("warehouse_id"));
        w.setName(rs.getString("name"));
        w.setAddress(rs.getString("address"));
        return w;
    }
}
