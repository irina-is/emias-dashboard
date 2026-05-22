package com.emias.dashboard.model;

/**
 * Одна строка рейтинга медицинской организации.
 * Содержит фактические показатели скрининга и плановые значения (если загружены).
 */
public class FacilityRating {

    private String name;
    private long completed;          // Завершено (статус исследования)
    private long withoutDeviations;  // Без отклонений (результат)
    private long withDeviations;     // С отклонениями (результат)
    private long refusal;            // Отказ (результат)
    private long noData;             // Нет данных (результат)

    // Плановые значения (null = план не загружен для этой организации)
    private Long annualPlanTotal;    // Годовой план (общий)
    private Long monthlyPlanTotal;   // Месячный план (общий)
    private Long weeklyPlanTotal;    // Недельный план (общий)

    public FacilityRating(String name, long completed, long withoutDeviations,
                          long withDeviations, long refusal, long noData) {
        this.name               = name;
        this.completed          = completed;
        this.withoutDeviations  = withoutDeviations;
        this.withDeviations     = withDeviations;
        this.refusal            = refusal;
        this.noData             = noData;
    }

    // ── Геттеры факта ────────────────────────────────────────────────────────

    public String getName()              { return name; }
    public long   getCompleted()         { return completed; }
    public long   getWithoutDeviations() { return withoutDeviations; }
    public long   getWithDeviations()    { return withDeviations; }
    public long   getRefusal()           { return refusal; }
    public long   getNoData()            { return noData; }

    // ── Геттеры плана ────────────────────────────────────────────────────────

    public Long getAnnualPlanTotal()  { return annualPlanTotal; }
    public Long getMonthlyPlanTotal() { return monthlyPlanTotal; }
    public Long getWeeklyPlanTotal()  { return weeklyPlanTotal; }

    /**
     * Процент выполнения годового плана. Возвращает null если план не задан.
     */
    public Integer getCompletionPercent() {
        if (annualPlanTotal == null || annualPlanTotal == 0) return null;
        return (int) Math.min(100L, completed * 100L / annualPlanTotal);
    }

    /**
     * true если план загружен для этой организации.
     */
    public boolean hasPlan() {
        return annualPlanTotal != null;
    }

    // ── Сеттеры плана ────────────────────────────────────────────────────────

    public void setAnnualPlanTotal(Long v)  { annualPlanTotal  = v; }
    public void setMonthlyPlanTotal(Long v) { monthlyPlanTotal = v; }
    public void setWeeklyPlanTotal(Long v)  { weeklyPlanTotal  = v; }
}
