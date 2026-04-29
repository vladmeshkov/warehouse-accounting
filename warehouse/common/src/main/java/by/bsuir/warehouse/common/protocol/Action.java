package by.bsuir.warehouse.common.protocol;

/**
 * Перечень всех поддерживаемых команд протокола обмена клиент–сервер.
 * Диспетчер на сервере маршрутизирует запросы по этому полю.
 */
public enum Action {

    // ── Аутентификация ──────────────────────────────────────────────────────
    LOGIN,
    LOGOUT,

    // ── Пользователи (только ADMIN) ─────────────────────────────────────────
    GET_ALL_USERS,
    GET_USER_BY_ID,
    CREATE_USER,
    UPDATE_USER,
    DEACTIVATE_USER,
    GET_ALL_ROLES,

    // ── Товары (номенклатура) ────────────────────────────────────────────────
    GET_ALL_PRODUCTS,
    GET_PRODUCT_BY_ID,
    CREATE_PRODUCT,
    UPDATE_PRODUCT,
    DELETE_PRODUCT,

    // ── Склады ───────────────────────────────────────────────────────────────
    GET_ALL_WAREHOUSES,
    GET_WAREHOUSE_BY_ID,
    CREATE_WAREHOUSE,
    UPDATE_WAREHOUSE,
    DELETE_WAREHOUSE,

    // ── Остатки ──────────────────────────────────────────────────────────────
    GET_STOCK_BY_WAREHOUSE,
    GET_STOCK_ALL,

    // ── Поставщики ───────────────────────────────────────────────────────────
    GET_ALL_SUPPLIERS,
    CREATE_SUPPLIER,
    UPDATE_SUPPLIER,
    DELETE_SUPPLIER,

    // ── Покупатели ───────────────────────────────────────────────────────────
    GET_ALL_CUSTOMERS,
    CREATE_CUSTOMER,
    UPDATE_CUSTOMER,
    DELETE_CUSTOMER,

    // ── Документы движения (бизнес-логика) ──────────────────────────────────
    PROCESS_INCOME,       // оформить приходную накладную
    PROCESS_OUTCOME,      // оформить расходную накладную
    PROCESS_TRANSFER,     // внутреннее перемещение
    PROCESS_INVENTORY,    // провести инвентаризацию
    GET_DOCUMENTS,        // список документов с фильтром
    GET_DOCUMENT_BY_ID,

    // ── Прогнозирование дефицита ─────────────────────────────────────────────
    GET_FORECAST_ALL,          // все прогнозы (последние)
    GET_FORECAST_BY_WAREHOUSE, // прогнозы по складу
    RECALCULATE_FORECAST,      // принудительный пересчёт

    // ── Отчёты ───────────────────────────────────────────────────────────────
    GET_REPORT_TURNOVER,       // оборачиваемость за период
    GET_REPORT_DEFICIT,        // отчёт по дефицитным позициям
    EXPORT_DOCUMENTS_CSV,      // экспорт реестра документов

    // ── Аудит ────────────────────────────────────────────────────────────────
    GET_AUDIT_LOG
}
