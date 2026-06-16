package com.emias.dashboard.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "hcv_weekly_plan")
public class HcvWeeklyPlanRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_name", nullable = false, length = 500)
    private String orgName;

    @Column(name = "plan_year_amb")
    private Integer planYearAmb;

    @Column(name = "plan_month_amb")
    private Integer planMonthAmb;

    // E — ожидают лечение (амбулаторно)
    @Column(name = "plan_month_stat")
    private Integer waitingAmb;

    // G — недельный план (амбулаторно)
    @Column(name = "weekly_plan_amb")
    private Integer weeklyPlanAmb;

    // F — недельный факт (амбулаторно)
    @Column(name = "weekly_fact_amb")
    private Integer weeklyFactAmb;

    @Column(name = "plan_year_stat")
    private Integer planYearStat;

    // J — направлены на лечение (стационар)
    @Column(name = "referrals_stat")
    private Integer referralsStat;

    // L — недельный план (стационар)
    @Column(name = "weekly_plan_stat")
    private Integer weeklyPlanStat;

    // K — недельный факт (стационар)
    @Column(name = "weekly_fact_stat")
    private Integer weeklyFactStat;

    @Column(name = "report_week", nullable = false)
    private LocalDate reportWeek;

    public HcvWeeklyPlanRow() {}

    public HcvWeeklyPlanRow(String orgName,
                             Integer planYearAmb, Integer planMonthAmb, Integer waitingAmb,
                             Integer weeklyPlanAmb, Integer weeklyFactAmb,
                             Integer planYearStat, Integer referralsStat,
                             Integer weeklyPlanStat, Integer weeklyFactStat,
                             LocalDate reportWeek) {
        this.orgName        = orgName;
        this.planYearAmb    = planYearAmb;
        this.planMonthAmb   = planMonthAmb;
        this.waitingAmb     = waitingAmb;
        this.weeklyPlanAmb  = weeklyPlanAmb;
        this.weeklyFactAmb  = weeklyFactAmb;
        this.planYearStat   = planYearStat;
        this.referralsStat  = referralsStat;
        this.weeklyPlanStat = weeklyPlanStat;
        this.weeklyFactStat = weeklyFactStat;
        this.reportWeek     = reportWeek;
    }

    public Long      getId()             { return id; }
    public String    getOrgName()        { return orgName; }
    public Integer   getPlanYearAmb()    { return planYearAmb; }
    public Integer   getPlanMonthAmb()   { return planMonthAmb; }
    public Integer   getWaitingAmb()     { return waitingAmb; }
    public Integer   getWeeklyPlanAmb()  { return weeklyPlanAmb; }
    public Integer   getWeeklyFactAmb()  { return weeklyFactAmb; }
    public Integer   getPlanYearStat()   { return planYearStat; }
    public Integer   getReferralsStat()  { return referralsStat; }
    public Integer   getWeeklyPlanStat() { return weeklyPlanStat; }
    public Integer   getWeeklyFactStat() { return weeklyFactStat; }
    public LocalDate getReportWeek()     { return reportWeek; }
}
