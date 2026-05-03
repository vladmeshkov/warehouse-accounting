package by.bsuir.warehouse.server.dao;

import by.bsuir.warehouse.common.model.RegistrationStatus;
import by.bsuir.warehouse.common.model.Role;
import by.bsuir.warehouse.common.model.User;
import java.util.List;
import java.util.Optional;

public interface UserDAO {
    List<User> findAll();
    Optional<User> findById(int id);
    Optional<User> findByUsername(String username);
    List<Role> findAllRoles();
    int create(User user);
    boolean update(User user);
    boolean setActive(int userId, boolean active);
    int register(User user);
    List<User> findPendingUsers();
    boolean setRegistrationStatus(int userId, RegistrationStatus status);
    boolean delete(int userId);
}