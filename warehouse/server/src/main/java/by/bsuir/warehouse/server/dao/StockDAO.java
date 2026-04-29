package by.bsuir.warehouse.server.dao;

import by.bsuir.warehouse.common.model.Stock;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;

public interface StockDAO {
    List<Stock> findAll();
    List<Stock> findByWarehouse(int warehouseId);
    Optional<Stock> findByWarehouseAndProduct(int warehouseId, int productId);

    /** Увеличивает остаток. Создаёт запись если не существует. */
    void increaseStock(Connection conn, int warehouseId, int productId, int quantity);

    /** Уменьшает остаток. Бросает исключение если недостаточно товара. */
    void decreaseStock(Connection conn, int warehouseId, int productId, int quantity);

    /** Устанавливает точное значение остатка (инвентаризация) */
    void setStock(Connection conn, int warehouseId, int productId, int quantity);
}
