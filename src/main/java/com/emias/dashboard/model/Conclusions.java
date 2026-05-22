package com.emias.dashboard.model;

/**
 * Автоматические выводы по дашборду.
 */
public class Conclusions {

    private boolean planAtRisk;           // есть риск невыполнения плана
    private long    lagPercent;           // процент отставания от прогноза
    private long    projected;            // сколько должно быть сделано на сегодня по графику

    private boolean highRefusals;         // отказов больше 10%
    private long    refusalPercent;       // фактический процент отказов
    private String  topRefusalFacility;   // ЛПУ с наибольшим числом отказов

    private boolean highNoData;           // "Нет данных" больше 20%
    private long    noDataPercent;        // фактический процент без результата

    private boolean slowingDown;          // в текущем месяце скринингов меньше чем в прошлом
    private long    currentMonthCount;    // скринингов в текущем месяце
    private long    previousMonthCount;   // скринингов в прошлом месяце

    public Conclusions(boolean planAtRisk, long lagPercent, long projected,
                       boolean highRefusals, long refusalPercent, String topRefusalFacility,
                       boolean highNoData, long noDataPercent,
                       boolean slowingDown, long currentMonthCount, long previousMonthCount) {
        this.planAtRisk         = planAtRisk;
        this.lagPercent         = lagPercent;
        this.projected          = projected;
        this.highRefusals       = highRefusals;
        this.refusalPercent     = refusalPercent;
        this.topRefusalFacility = topRefusalFacility;
        this.highNoData         = highNoData;
        this.noDataPercent      = noDataPercent;
        this.slowingDown        = slowingDown;
        this.currentMonthCount  = currentMonthCount;
        this.previousMonthCount = previousMonthCount;
    }

    public boolean isPlanAtRisk()          { return planAtRisk; }
    public long    getLagPercent()         { return lagPercent; }
    public long    getProjected()          { return projected; }
    public boolean isHighRefusals()        { return highRefusals; }
    public long    getRefusalPercent()     { return refusalPercent; }
    public String  getTopRefusalFacility() { return topRefusalFacility; }
    public boolean isHighNoData()          { return highNoData; }
    public long    getNoDataPercent()      { return noDataPercent; }
    public boolean isSlowingDown()         { return slowingDown; }
    public long    getCurrentMonthCount()  { return currentMonthCount; }
    public long    getPreviousMonthCount() { return previousMonthCount; }
}
