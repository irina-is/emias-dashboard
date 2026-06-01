package com.emias.dashboard.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class HcvOrgRow {

    private String orgName;
    private int    annualPlan;
    private int    weeklyPlan;
    private int    accumulated;
    private int    lastWeekActual;

    public HcvOrgRow(String orgName, int annualPlan, int weeklyPlan, int accumulated, int lastWeekActual) {
        this.orgName        = orgName;
        this.annualPlan     = annualPlan;
        this.weeklyPlan     = weeklyPlan;
        this.accumulated    = accumulated;
        this.lastWeekActual = lastWeekActual;
    }

    /** Исполнение плана 2026, % (без ограничения 100) */
    public double getPlanCompletionDouble() {
        return annualPlan > 0 ? accumulated * 100.0 / annualPlan : 0.0;
    }

    /** Исполнение недельного плана, % (без ограничения 100) */
    public double getWeeklyCompletionDouble() {
        return weeklyPlan > 0 ? lastWeekActual * 100.0 / weeklyPlan : 0.0;
    }

    /** Осталось до выполнения плана 2026 */
    public int getRemaining() {
        return Math.max(0, annualPlan - accumulated);
    }

    /** Прогноз выполнения к концу 2026 года, % */
    public double getForecastPercent() {
        if (annualPlan <= 0) return 0.0;
        int weeksLeft = (int) ChronoUnit.WEEKS.between(LocalDate.now(),
                LocalDate.of(LocalDate.now().getYear(), 12, 31));
        double projected = accumulated + (double) lastWeekActual * weeksLeft;
        return projected * 100.0 / annualPlan;
    }

    /** green / yellow / red по проценту выполнения годового плана */
    public String getStatus() {
        double pct = getPlanCompletionDouble();
        if (pct >= 100.0) return "green";
        if (pct >= 80.0)  return "yellow";
        return "red";
    }

    // backward compat
    public int getCompletionPercent() {
        return annualPlan > 0 ? (int) Math.min(100, accumulated * 100L / annualPlan) : 0;
    }

    public boolean isOnTrackWeekly() {
        return lastWeekActual >= weeklyPlan;
    }

    public String getOrgName()        { return orgName; }
    public int    getAnnualPlan()     { return annualPlan; }
    public int    getWeeklyPlan()     { return weeklyPlan; }
    public int    getAccumulated()    { return accumulated; }
    public int    getLastWeekActual() { return lastWeekActual; }
}
