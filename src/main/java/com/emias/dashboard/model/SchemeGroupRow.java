package com.emias.dashboard.model;

import java.util.Map;

/**
 * Одна строка в дашборде схем назначения.
 * Группировка: уникальный код схемы (столбец M) → количество пациентов + продолжительности + маршруты.
 */
public class SchemeGroupRow {

    /** Код схемы лечения (столбец M) */
    public final String scheme;

    /** Количество пациентов с данной схемой */
    public final long count;

    /** Продолжительности терапии для данной схемы: значение → кол-во */
    public final Map<String, Long> durationCounts;

    /** Организации маршрутизации: МО → кол-во */
    public final Map<String, Long> routedToCounts;

    public SchemeGroupRow(String scheme, long count,
                          Map<String, Long> durationCounts,
                          Map<String, Long> routedToCounts) {
        this.scheme         = scheme;
        this.count          = count;
        this.durationCounts = durationCounts;
        this.routedToCounts = routedToCounts;
    }
}
