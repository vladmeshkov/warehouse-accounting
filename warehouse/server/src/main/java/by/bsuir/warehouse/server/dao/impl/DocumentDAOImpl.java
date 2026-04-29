package by.bsuir.warehouse.server.dao.impl;

import by.bsuir.warehouse.common.model.*;
import by.bsuir.warehouse.server.config.DBConnection;
import by.bsuir.warehouse.server.dao.DocumentDAO;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.logging.Logger;

public class DocumentDAOImpl implements DocumentDAO {

    private static final Logger log = Logger.getLogger(DocumentDAOImpl.class.getName());

    private Connection conn() { return DBConnection.getInstance().getConnection(); }

    @Override
    public List<Document> findAll() {
        return queryDocuments(
            "SELECT d.document_id, d.document_type, d.document_number, d.document_date, " +
            "d.warehouse_id_from, d.warehouse_id_to, d.supplier_id, d.customer_id, " +
            "d.responsible_user_id, d.comment, " +
            "u.full_name AS responsible_name, " +
            "wf.name AS warehouse_from_name, wt.name AS warehouse_to_name, " +
            "s.name AS supplier_name, c.name AS customer_name " +
            "FROM document d " +
            "JOIN user u ON d.responsible_user_id = u.user_id " +
            "LEFT JOIN warehouse wf ON d.warehouse_id_from = wf.warehouse_id " +
            "LEFT JOIN warehouse wt ON d.warehouse_id_to  = wt.warehouse_id " +
            "LEFT JOIN supplier s   ON d.supplier_id = s.supplier_id " +
            "LEFT JOIN customer c   ON d.customer_id = c.customer_id " +
            "ORDER BY d.document_date DESC", null, null, null);
    }

    @Override
    public List<Document> findByType(DocumentType type) {
        return queryDocuments(buildBaseSql() + " WHERE d.document_type=? ORDER BY d.document_date DESC",
                type.name(), null, null);
    }

    @Override
    public List<Document> findByDateRange(LocalDate from, LocalDate to) {
        String sql = buildBaseSql() +
                " WHERE DATE(d.document_date) BETWEEN ? AND ? ORDER BY d.document_date DESC";
        return queryDocuments(sql, null, from.toString(), to.toString());
    }

    @Override
    public List<Document> findByWarehouse(int warehouseId) {
        String sql = buildBaseSql() +
                " WHERE d.warehouse_id_from=? OR d.warehouse_id_to=? ORDER BY d.document_date DESC";
        return queryDocuments(sql, String.valueOf(warehouseId), null, null);
    }

    @Override
    public Optional<Document> findById(int id) {
        String sql = buildBaseSql() + " WHERE d.document_id=?";
        List<Document> docs = queryDocuments(sql, String.valueOf(id), null, null);
        if (docs.isEmpty()) return Optional.empty();
        Document doc = docs.get(0);
        doc.setItems(findItems(doc.getId()));
        return Optional.of(doc);
    }

    /**
     * Сохраняет заголовок документа и все его позиции.
     * Вызывается ВНУТРИ транзакции, которую открывает InventoryService.
     */
    @Override
    public int create(Connection conn, Document doc) {
        String sql = "INSERT INTO document (document_type, document_number, document_date, " +
                     "warehouse_id_from, warehouse_id_to, supplier_id, customer_id, " +
                     "responsible_user_id, comment) VALUES (?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, doc.getDocumentType().name());
            ps.setString(2, doc.getDocumentNumber());
            ps.setTimestamp(3, Timestamp.valueOf(doc.getDocumentDate()));
            setIntOrNull(ps, 4, doc.getWarehouseFrom());
            setIntOrNull(ps, 5, doc.getWarehouseTo());
            setIntOrNull(ps, 6, doc.getSupplier());
            setIntOrNull(ps, 7, doc.getCustomer());
            ps.setInt(8, doc.getResponsibleUser().getId());
            ps.setString(9, doc.getComment());
            ps.executeUpdate();

            int docId;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Не получен ID документа");
                docId = keys.getInt(1);
            }

            // Сохраняем позиции
            String itemSql = "INSERT INTO document_item (document_id, product_id, quantity, price) " +
                             "VALUES (?,?,?,?)";
            try (PreparedStatement ips = conn.prepareStatement(itemSql)) {
                for (DocumentItem item : doc.getItems()) {
                    ips.setInt(1, docId);
                    ips.setInt(2, item.getProduct().getId());
                    ips.setBigDecimal(3, item.getQuantity());
                    if (item.getPrice() != null) ips.setBigDecimal(4, item.getPrice());
                    else ips.setNull(4, Types.DECIMAL);
                    ips.addBatch();
                }
                ips.executeBatch();
            }
            return docId;
        } catch (SQLException e) {
            throw new RuntimeException("create document failed: " + e.getMessage(), e);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private String buildBaseSql() {
        return "SELECT d.document_id, d.document_type, d.document_number, d.document_date, " +
               "d.warehouse_id_from, d.warehouse_id_to, d.supplier_id, d.customer_id, " +
               "d.responsible_user_id, d.comment, " +
               "u.full_name AS responsible_name, " +
               "wf.name AS warehouse_from_name, wt.name AS warehouse_to_name, " +
               "s.name AS supplier_name, c.name AS customer_name " +
               "FROM document d " +
               "JOIN user u ON d.responsible_user_id = u.user_id " +
               "LEFT JOIN warehouse wf ON d.warehouse_id_from = wf.warehouse_id " +
               "LEFT JOIN warehouse wt ON d.warehouse_id_to  = wt.warehouse_id " +
               "LEFT JOIN supplier s   ON d.supplier_id = s.supplier_id " +
               "LEFT JOIN customer c   ON d.customer_id = c.customer_id";
    }

    private List<Document> queryDocuments(String sql, String p1, String p2, String p3) {
        List<Document> list = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            int idx = 1;
            if (p1 != null) ps.setString(idx++, p1);
            if (p2 != null) ps.setString(idx++, p2);
            if (p3 != null) ps.setString(idx, p3);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapHeader(rs));
            }
        } catch (SQLException e) {
            log.severe("queryDocuments: " + e.getMessage());
        }
        return list;
    }

    private Document mapHeader(ResultSet rs) throws SQLException {
        Document d = new Document();
        d.setId(rs.getInt("document_id"));
        d.setDocumentType(DocumentType.valueOf(rs.getString("document_type")));
        d.setDocumentNumber(rs.getString("document_number"));
        Timestamp ts = rs.getTimestamp("document_date");
        if (ts != null) d.setDocumentDate(ts.toLocalDateTime());
        d.setComment(rs.getString("comment"));

        // Денормализованные поля для отображения
        User responsible = new User();
        responsible.setId(rs.getInt("responsible_user_id"));
        responsible.setFullName(rs.getString("responsible_name"));
        d.setResponsibleUser(responsible);

        int wfId = rs.getInt("warehouse_id_from");
        if (!rs.wasNull()) {
            Warehouse wf = new Warehouse(wfId, rs.getString("warehouse_from_name"), null);
            d.setWarehouseFrom(wf);
        }
        int wtId = rs.getInt("warehouse_id_to");
        if (!rs.wasNull()) {
            Warehouse wt = new Warehouse(wtId, rs.getString("warehouse_to_name"), null);
            d.setWarehouseTo(wt);
        }
        int sId = rs.getInt("supplier_id");
        if (!rs.wasNull()) {
            Supplier sup = new Supplier(sId, rs.getString("supplier_name"));
            d.setSupplier(sup);
        }
        int cId = rs.getInt("customer_id");
        if (!rs.wasNull()) {
            Customer cust = new Customer(cId, rs.getString("customer_name"));
            d.setCustomer(cust);
        }
        return d;
    }

    private List<DocumentItem> findItems(int documentId) {
        List<DocumentItem> items = new ArrayList<>();
        String sql = "SELECT di.item_id, di.document_id, di.product_id, di.quantity, di.price, " +
                     "p.name AS product_name, p.article, p.unit " +
                     "FROM document_item di JOIN product p ON di.product_id = p.product_id " +
                     "WHERE di.document_id=?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, documentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DocumentItem item = new DocumentItem();
                    item.setId(rs.getInt("item_id"));
                    item.setDocumentId(documentId);
                    Product p = new Product();
                    p.setId(rs.getInt("product_id"));
                    p.setName(rs.getString("product_name"));
                    p.setArticle(rs.getString("article"));
                    p.setUnit(rs.getString("unit"));
                    item.setProduct(p);
                    item.setQuantity(rs.getBigDecimal("quantity"));
                    item.setPrice(rs.getBigDecimal("price"));
                    items.add(item);
                }
            }
        } catch (SQLException e) {
            log.severe("findItems: " + e.getMessage());
        }
        return items;
    }

    private void setIntOrNull(PreparedStatement ps, int idx, Object entity) throws SQLException {
        if (entity == null) { ps.setNull(idx, Types.INTEGER); return; }
        if (entity instanceof Warehouse w) ps.setInt(idx, w.getId());
        else if (entity instanceof Supplier s) ps.setInt(idx, s.getId());
        else if (entity instanceof Customer c) ps.setInt(idx, c.getId());
        else ps.setNull(idx, Types.INTEGER);
    }
}
