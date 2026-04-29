package by.bsuir.warehouse.server.dao.impl;

import by.bsuir.warehouse.common.model.Role;
import by.bsuir.warehouse.common.model.User;
import by.bsuir.warehouse.server.config.DBConnection;
import by.bsuir.warehouse.server.dao.UserDAO;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

public class UserDAOImpl implements UserDAO {

    private static final Logger log = Logger.getLogger(UserDAOImpl.class.getName());

    private Connection conn() {
        return DBConnection.getInstance().getConnection();
    }

    @Override
    public List<User> findAll() {
        List<User> list = new ArrayList<>();
        String sql = "SELECT u.user_id, u.username, u.password_hash, u.full_name, " +
                     "u.phone, u.email, u.created_at, u.is_active, " +
                     "r.role_id, r.role_name " +
                     "FROM user u JOIN role r ON u.role_id = r.role_id " +
                     "ORDER BY u.full_name";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) {
            log.severe("findAll users: " + e.getMessage());
        }
        return list;
    }

    @Override
    public Optional<User> findById(int id) {
        String sql = "SELECT u.user_id, u.username, u.password_hash, u.full_name, " +
                     "u.phone, u.email, u.created_at, u.is_active, " +
                     "r.role_id, r.role_name " +
                     "FROM user u JOIN role r ON u.role_id = r.role_id " +
                     "WHERE u.user_id = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            log.severe("findById user: " + e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public Optional<User> findByUsername(String username) {
        String sql = "SELECT u.user_id, u.username, u.password_hash, u.full_name, " +
                     "u.phone, u.email, u.created_at, u.is_active, " +
                     "r.role_id, r.role_name " +
                     "FROM user u JOIN role r ON u.role_id = r.role_id " +
                     "WHERE u.username = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            log.severe("findByUsername: " + e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public List<Role> findAllRoles() {
        List<Role> list = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT role_id, role_name FROM role ORDER BY role_id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Role(rs.getInt("role_id"), rs.getString("role_name")));
            }
        } catch (SQLException e) {
            log.severe("findAllRoles: " + e.getMessage());
        }
        return list;
    }

    @Override
    public int create(User u) {
        String sql = "INSERT INTO user (username, password_hash, role_id, full_name, " +
                     "phone, email, is_active) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn().prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, u.getUsername());
            ps.setString(2, u.getPasswordHash());
            ps.setInt(3, u.getRole().getId());
            ps.setString(4, u.getFullName());
            ps.setString(5, u.getPhone());
            ps.setString(6, u.getEmail());
            ps.setBoolean(7, u.isActive());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        } catch (SQLException e) {
            log.severe("create user: " + e.getMessage());
        }
        return -1;
    }

    @Override
    public boolean update(User u) {
        String sql = "UPDATE user SET username=?, role_id=?, full_name=?, " +
                     "phone=?, email=? WHERE user_id=?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, u.getUsername());
            ps.setInt(2, u.getRole().getId());
            ps.setString(3, u.getFullName());
            ps.setString(4, u.getPhone());
            ps.setString(5, u.getEmail());
            ps.setInt(6, u.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.severe("update user: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean setActive(int userId, boolean active) {
        try (PreparedStatement ps = conn().prepareStatement(
                "UPDATE user SET is_active=? WHERE user_id=?")) {
            ps.setBoolean(1, active);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.severe("setActive user: " + e.getMessage());
            return false;
        }
    }

    private User map(ResultSet rs) throws SQLException {
        Role role = new Role(rs.getInt("role_id"), rs.getString("role_name"));
        User u = new User();
        u.setId(rs.getInt("user_id"));
        u.setUsername(rs.getString("username"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setRole(role);
        u.setFullName(rs.getString("full_name"));
        u.setPhone(rs.getString("phone"));
        u.setEmail(rs.getString("email"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) u.setCreatedAt(ts.toLocalDateTime());
        u.setActive(rs.getBoolean("is_active"));
        return u;
    }
}
