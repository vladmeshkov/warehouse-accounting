package by.bsuir.warehouse.server.dao;

import by.bsuir.warehouse.common.model.Warehouse;
import java.util.List;
import java.util.Optional;

public interface WarehouseDAO {
    List<Warehouse> findAll();
    Optional<Warehouse> findById(int id);
    int create(Warehouse warehouse);
    boolean update(Warehouse warehouse);
    boolean delete(int id);
}
