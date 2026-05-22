package com.emias.dashboard.model;

public class ScreeningStats {

    private long completedCount;    // уникальных МКАБ за год
    private long plan;              // годовой план
    private long remainingCount;    // сколько осталось до плана
    private int  completionPercent; // процент выполнения (0–100)
    private long dailyRateNeeded;   // необходимый темп в сутки

    public ScreeningStats(long completedCount, long dailyRateNeeded, long plan) {
        this.plan              = plan;
        this.completedCount    = completedCount;
        this.remainingCount    = Math.max(0, plan - completedCount);
        this.completionPercent = plan > 0 ? (int) Math.min(100, completedCount * 100 / plan) : 0;
        this.dailyRateNeeded   = dailyRateNeeded;
    }

    public long getPlan()              { return plan; }
    public long getCompletedCount()    { return completedCount; }
    public long getRemainingCount()    { return remainingCount; }
    public int  getCompletionPercent() { return completionPercent; }
    public long getDailyRateNeeded()   { return dailyRateNeeded; }
}
