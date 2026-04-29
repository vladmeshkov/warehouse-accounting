package by.bsuir.warehouse.server.dao;

import by.bsuir.warehouse.common.model.Product;
import java.util.List;
import java.util.Optional;

public interface ProductDAO {
    List<Product> findAll();
    Optional<Product> findById(int id);
    Optional<Product> findByArticle(String article);
    List<Product> findByCategory(String category);
    int create(Product product);          // возвращает сгенерированный id
    boolean update(Product product);
    boolean delete(int id);
}
