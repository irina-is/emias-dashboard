package com.emias.dashboard.model;

import java.util.List;

/**
 * Данные для графика распределения по возрасту.
 * Содержит подписи групп и несколько серий (по одной на каждую неделю).
 */
public class AgeDiagram {

    // Названия возрастных групп — одинаковые для всех недель
    private List<String> labels;

    // Серии данных — каждая серия это одна неделя
    private List<DiagramSeries> series;

    public AgeDiagram(List<String> labels, List<DiagramSeries> series) {
        this.labels = labels;
        this.series = series;
    }

    public List<String>       getLabels() { return labels; }
    public List<DiagramSeries> getSeries() { return series; }
}
