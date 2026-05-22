package com.emias.dashboard.model;

/**
 * Одна строка рейтинга медицинской организации.
 */
public class FacilityRating {

    private String name;
    private long completed;          // Завершено (статус исследования)
    private long withoutDeviations;  // Без отклонений (результат)
    private long withDeviations;     // С отклонениями (результат)
    private long refusal;            // Отказ (результат)
    private long noData;             // Нет данных (результат)

    public FacilityRating(String name, long completed, long withoutDeviations,
                          long withDeviations, long refusal, long noData) {
        this.name               = name;
        this.completed          = completed;
        this.withoutDeviations  = withoutDeviations;
        this.withDeviations     = withDeviations;
        this.refusal            = refusal;
        this.noData             = noData;
    }

    public String getName()              { return name; }
    public long   getCompleted()         { return completed; }
    public long   getWithoutDeviations() { return withoutDeviations; }
    public long   getWithDeviations()    { return withDeviations; }
    public long   getRefusal()           { return refusal; }
    public long   getNoData()            { return noData; }
}
