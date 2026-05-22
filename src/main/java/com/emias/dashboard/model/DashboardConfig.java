package com.emias.dashboard.model;

public class DashboardConfig {

    private String  dashboardTitle;
    private long    annualPlan;
    private String  chartMonthlyTitle;
    private boolean chartMonthlyEnabled;
    private String  chartAgesTitle;
    private boolean chartAgesEnabled;

    public DashboardConfig(String dashboardTitle, long annualPlan,
                           String chartMonthlyTitle, boolean chartMonthlyEnabled,
                           String chartAgesTitle, boolean chartAgesEnabled) {
        this.dashboardTitle      = dashboardTitle;
        this.annualPlan          = annualPlan;
        this.chartMonthlyTitle   = chartMonthlyTitle;
        this.chartMonthlyEnabled = chartMonthlyEnabled;
        this.chartAgesTitle      = chartAgesTitle;
        this.chartAgesEnabled    = chartAgesEnabled;
    }

    public String  getDashboardTitle()      { return dashboardTitle; }
    public long    getAnnualPlan()          { return annualPlan; }
    public String  getChartMonthlyTitle()   { return chartMonthlyTitle; }
    public boolean isChartMonthlyEnabled()  { return chartMonthlyEnabled; }
    public String  getChartAgesTitle()      { return chartAgesTitle; }
    public boolean isChartAgesEnabled()     { return chartAgesEnabled; }
}
