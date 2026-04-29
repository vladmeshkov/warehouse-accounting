package by.bsuir.warehouse.server.dao;

import by.bsuir.warehouse.common.model.Supplier;
import java.util.List;
import java.util.Optional;

public interface SupplierDAO {
    List<Supplier> findAll();
    Optional<Supplier> findById(int id);
    int create(Supplier supplier);
    boolean update(Supplier supplier);
    boolean delete(int id);
}
