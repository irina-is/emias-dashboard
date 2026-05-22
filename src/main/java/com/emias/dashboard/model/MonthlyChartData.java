package com.emias.dashboard.model;

import java.util.List;

/**
 * Данные для графика "план / факт по месяцам".
 */
public class MonthlyChartData {

    private List<String> labels;      // ["Январь", ..., "Декабрь"]
    private List<Long>   fact;        // уникальных МКАБ за каждый месяц
    private long         monthlyPlan; // равномерный план на каждый месяц
    private long         noDateCount; // уникальных МКАБ без даты исследования

    public MonthlyChartData(List<String> labels, List<Long> fact, long monthlyPlan, long noDateCount) {
        this.labels      = labels;
        this.fact        = fact;
        this.monthlyPlan = monthlyPlan;
        this.noDateCount = noDateCount;
    }

    public List<String> getLabels()      { return labels; }
    public List<Long>   getFact()        { return fact; }
    public long         getMonthlyPlan() { return monthlyPlan; }
    public long         getNoDateCount() { return noDateCount; }
}
