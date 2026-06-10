package com.emias.dashboard.model;

import java.util.List;
import java.util.Map;

public class PcrDashboardStats {

    /** Итого по всем записям */
    public final long totalAll;
    public final long totalPositive;
    public final long totalNegative;
    public final long totalNoInfo;

    /** Отсортированный список лет (например ["2024","2025","2026","Не указан"]) */
    public final List<String> years;

    /** Итог по каждому году: год → кол-во (все статусы ПЦР) */
    public final Map<String, Long> totalByYear;

    /** Строки таблицы по территориям */
    public final List<PcrTerritoryRow> rows;

    public PcrDashboardStats(long totalAll, long totalPositive, long totalNegative, long totalNoInfo,
                             List<String> years, Map<String, Long> totalByYear,
                             List<PcrTerritoryRow> rows) {
        this.totalAll      = totalAll;
        this.totalPositive = totalPositive;
        this.totalNegative = totalNegative;
        this.totalNoInfo   = totalNoInfo;
        this.years         = years;
        this.totalByYear   = totalByYear;
        this.rows          = rows;
    }

    public int positivePct() {
        return totalAll > 0 ? (int) (totalPositive * 100 / totalAll) : 0;
    }
}
