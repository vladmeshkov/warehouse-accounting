package by.bsuir.warehouse.server.dao;

import by.bsuir.warehouse.common.model.Customer;
import java.util.List;
import java.util.Optional;

public interface CustomerDAO {
    List<Customer> findAll();
    Optional<Customer> findById(int id);
    int create(Customer customer);
    boolean update(Customer customer);
    boolean delete(int id);
}
