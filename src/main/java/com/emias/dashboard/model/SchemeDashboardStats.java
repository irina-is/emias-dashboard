package com.emias.dashboard.model;

import java.util.List;
import java.util.Map;

/**
 * Статистика дашборда «Схемы назначения».
 */
public class SchemeDashboardStats {

    /** Всего записей в своде */
    public final long totalAll;

    /** Записей с заполненной схемой */
    public final long withScheme;

    /** Уникальных кодов схем */
    public final long uniqueSchemes;

    /** Уникальных организаций маршрутизации */
    public final long uniqueRoutes;

    /** Строки сводной таблицы: схема+продолжительность → данные */
    public final List<SchemeGroupRow> groups;

    /** Топ-маршруты по всем схемам: МО → суммарное кол-во */
    public final Map<String, Long> topRoutes;

    public SchemeDashboardStats(long totalAll, long withScheme, long uniqueSchemes,
                                long uniqueRoutes, List<SchemeGroupRow> groups,
                                Map<String, Long> topRoutes) {
        this.totalAll      = totalAll;
        this.withScheme    = withScheme;
        this.uniqueSchemes = uniqueSchemes;
        this.uniqueRoutes  = uniqueRoutes;
        this.groups        = groups;
        this.topRoutes     = topRoutes;
    }
}
