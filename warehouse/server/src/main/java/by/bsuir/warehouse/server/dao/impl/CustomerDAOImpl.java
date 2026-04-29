package by.bsuir.warehouse.server.dao.impl;

import by.bsuir.warehouse.common.model.Customer;
import by.bsuir.warehouse.server.config.DBConnection;
import by.bsuir.warehouse.server.dao.CustomerDAO;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

public class CustomerDAOImpl implements CustomerDAO {

    private static final Logger log = Logger.getLogger(CustomerDAOImpl.class.getName());

    private Connection conn() { return DBConnection.getInstance().getConnection(); }

    @Override
    public List<Customer> findAll() {
        List<Customer> list = new ArrayList<>();
        String sql = "SELECT customer_id, name, contact_person, phone, email, address " +
                     "FROM customer ORDER BY name";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) {
            log.severe("findAll customers: " + e.getMessage());
        }
        return list;
    }

    @Override
    public Optional<Customer> findById(int id) {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT customer_id, name, contact_person, phone, email, address " +
                "FROM customer WHERE customer_id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            log.severe("findById customer: " + e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public int create(Customer c) {
        String sql = "INSERT INTO customer (name, contact_person, phone, email, address) " +
                     "VALUES (?,?,?,?,?)";
        try (PreparedStatement ps = conn().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, c.getName());
            ps.setString(2, c.getContactPerson());
            ps.setString(3, c.getPhone());
            ps.setString(4, c.getEmail());
            ps.setString(5, c.getAddress());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        } catch (SQLException e) {
            log.severe("create customer: " + e.getMessage());
        }
        return -1;
    }

    @Override
    public boolean update(Customer c) {
        String sql = "UPDATE customer SET name=?, contact_person=?, phone=?, " +
                     "email=?, address=? WHERE customer_id=?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, c.getName());
            ps.setString(2, c.getContactPerson());
            ps.setString(3, c.getPhone());
            ps.setString(4, c.getEmail());
            ps.setString(5, c.getAddress());
            ps.setInt(6, c.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.severe("update customer: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean delete(int id) {
        try (PreparedStatement ps = conn().prepareStatement(
                "DELETE FROM customer WHERE customer_id=?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.severe("delete customer: " + e.getMessage());
            return false;
        }
    }

    private Customer map(ResultSet rs) throws SQLException {
        Customer c = new Customer();
        c.setId(rs.getInt("customer_id"));
        c.setName(rs.getString("name"));
        c.setContactPerson(rs.getString("contact_person"));
        c.setPhone(rs.getString("phone"));
        c.setEmail(rs.getString("email"));
        c.setAddress(rs.getString("address"));
        return c;
    }
}
