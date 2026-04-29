package by.bsuir.warehouse.server.dao.impl;

import by.bsuir.warehouse.common.model.Supplier;
import by.bsuir.warehouse.server.config.DBConnection;
import by.bsuir.warehouse.server.dao.SupplierDAO;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

public class SupplierDAOImpl implements SupplierDAO {

    private static final Logger log = Logger.getLogger(SupplierDAOImpl.class.getName());

    private Connection conn() { return DBConnection.getInstance().getConnection(); }

    @Override
    public List<Supplier> findAll() {
        List<Supplier> list = new ArrayList<>();
        String sql = "SELECT supplier_id, name, contact_person, phone, email, address, inn " +
                     "FROM supplier ORDER BY name";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) {
            log.severe("findAll suppliers: " + e.getMessage());
        }
        return list;
    }

    @Override
    public Optional<Supplier> findById(int id) {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT supplier_id, name, contact_person, phone, email, address, inn " +
                "FROM supplier WHERE supplier_id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            log.severe("findById supplier: " + e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public int create(Supplier s) {
        String sql = "INSERT INTO supplier (name, contact_person, phone, email, address, inn) " +
                     "VALUES (?,?,?,?,?,?)";
        try (PreparedStatement ps = conn().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, s.getName());
            ps.setString(2, s.getContactPerson());
            ps.setString(3, s.getPhone());
            ps.setString(4, s.getEmail());
            ps.setString(5, s.getAddress());
            ps.setString(6, s.getInn());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        } catch (SQLException e) {
            log.severe("create supplier: " + e.getMessage());
        }
        return -1;
    }

    @Override
    public boolean update(Supplier s) {
        String sql = "UPDATE supplier SET name=?, contact_person=?, phone=?, " +
                     "email=?, address=?, inn=? WHERE supplier_id=?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, s.getName());
            ps.setString(2, s.getContactPerson());
            ps.setString(3, s.getPhone());
            ps.setString(4, s.getEmail());
            ps.setString(5, s.getAddress());
            ps.setString(6, s.getInn());
            ps.setInt(7, s.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.severe("update supplier: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean delete(int id) {
        try (PreparedStatement ps = conn().prepareStatement(
                "DELETE FROM supplier WHERE supplier_id=?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.severe("delete supplier: " + e.getMessage());
            return false;
        }
    }

    private Supplier map(ResultSet rs) throws SQLException {
        Supplier s = new Supplier();
        s.setId(rs.getInt("supplier_id"));
        s.setName(rs.getString("name"));
        s.setContactPerson(rs.getString("contact_person"));
        s.setPhone(rs.getString("phone"));
        s.setEmail(rs.getString("email"));
        s.setAddress(rs.getString("address"));
        s.setInn(rs.getString("inn"));
        return s;
    }
}
