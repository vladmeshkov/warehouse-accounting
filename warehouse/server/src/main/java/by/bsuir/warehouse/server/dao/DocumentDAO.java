package by.bsuir.warehouse.server.dao;

import by.bsuir.warehouse.common.model.Document;
import by.bsuir.warehouse.common.model.DocumentType;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DocumentDAO {
    List<Document> findAll();
    List<Document> findByType(DocumentType type);
    List<Document> findByDateRange(LocalDate from, LocalDate to);
    List<Document> findByWarehouse(int warehouseId);
    Optional<Document> findById(int id);

    /** Сохраняет заголовок + все позиции документа в рамках транзакции */
    int create(Connection conn, Document document);
}
