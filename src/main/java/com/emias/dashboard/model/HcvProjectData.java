package com.emias.dashboard.model;

import java.util.List;

public class HcvProjectData {

    private int    projectId;
    private String title;
    private int    target;
    private int    leadTarget;
    private String deadline;
    private int    totalAccumulated;
    private int    totalLastWeek;
    private List<HcvOrgRow> rows;

    public HcvProjectData(int projectId, String title, int target, int leadTarget,
                          String deadline, int totalAccumulated, int totalLastWeek,
                          List<HcvOrgRow> rows) {
        this.projectId        = projectId;
        this.title            = title;
        this.target           = target;
        this.leadTarget       = leadTarget;
        this.deadline         = deadline;
        this.totalAccumulated = totalAccumulated;
        this.totalLastWeek    = totalLastWeek;
        this.rows             = rows;
    }

    public int getCompletionPercent() {
        return target > 0 ? (int) Math.min(100, totalAccumulated * 100L / target) : 0;
    }

    public double getPlanCompletionDouble() {
        int totalPlan = getTotalAnnualPlan();
        return totalPlan > 0 ? totalAccumulated * 100.0 / totalPlan : 0.0;
    }

    public double getWeeklyCompletionDouble() {
        return leadTarget > 0 ? totalLastWeek * 100.0 / leadTarget : 0.0;
    }

    public int getTotalAnnualPlan() {
        return rows.stream().mapToInt(HcvOrgRow::getAnnualPlan).sum();
    }

    public int getTotalWeeklyPlan() {
        return leadTarget;
    }

    public long getRedZoneCount() {
        return rows.stream().filter(r -> "red".equals(r.getStatus()) && r.getAnnualPlan() > 0).count();
    }

    public boolean isLeadOnTrack() {
        return totalLastWeek >= leadTarget;
    }

    public int    getProjectId()        { return projectId; }
    public String getTitle()            { return title; }
    public int    getTarget()           { return target; }
    public int    getLeadTarget()       { return leadTarget; }
    public String getDeadline()         { return deadline; }
    public int    getTotalAccumulated() { return totalAccumulated; }
    public int    getTotalLastWeek()    { return totalLastWeek; }
    public List<HcvOrgRow> getRows()   { return rows; }
}
