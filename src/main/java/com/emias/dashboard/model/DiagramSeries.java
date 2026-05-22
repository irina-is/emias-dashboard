package com.emias.dashboard.model;

import java.util.List;

/**
 * Один набор данных для графика — одна неделя.
 */
public class DiagramSeries {

    private String weekName; // название недели, например "Неделя 20, 2026"
    private List<Long> values; // количество пациентов по каждой возрастной группе

    public DiagramSeries(String weekName, List<Long> values) {
        this.weekName = weekName;
        this.values   = values;
    }

    public String    getWeekName() { return weekName; }
    public List<Long> getValues()  { return values; }
}
