package by.bsuir.warehouse.server.dao;

import by.bsuir.warehouse.common.model.ConsumptionHistory;
import by.bsuir.warehouse.common.model.DeficitForecast;
import java.time.LocalDate;
import java.util.List;

public interface ForecastDAO {
    /** История расхода товара за последние N дней */
    List<ConsumptionHistory> findHistory(int productId, int days);

    /** Обновить агрегированный суточный расход */
    void upsertConsumptionHistory(int productId, LocalDate date, double quantity);

    /** Все последние прогнозы (по одному на каждую пару склад-товар) */
    List<DeficitForecast> findLatestForecasts();

    List<DeficitForecast> findForecastsByWarehouse(int warehouseId);

    /** Сохранить/обновить прогноз */
    void upsertForecast(DeficitForecast forecast);
}
