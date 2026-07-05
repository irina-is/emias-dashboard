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

    // C — план год (амб.)
    @Column(name = "plan_year_amb")
    private Integer planYearAmb;

    // D — факт 2026 (амб., с начала года)
    @Column(name = "fact_year_amb")
    private Integer factYearAmb;

    // E — % от плана (амб.) — из Excel
    @Column(name = "pct_amb")
    private Integer pctAmb;

    // F — недельный план (амб.)
    @Column(name = "weekly_plan_amb")
    private Integer weeklyPlanAmb;

    // G — недельный факт (амб.)
    @Column(name = "weekly_fact_amb")
    private Integer weeklyFactAmb;

    // H — динамика (амб.) — из Excel
    @Column(name = "dynamic_amb")
    private Integer dynamicAmb;

    // I — план год (стац.)
    @Column(name = "plan_year_stat")
    private Integer planYearStat;

    // J — направлены на лечение (стац.)
    @Column(name = "referrals_stat")
    private Integer referralsStat;

    // K — % от плана (стац.) — из Excel
    @Column(name = "pct_stat")
    private Integer pctStat;

    // L — недельный план (стац.)
    @Column(name = "weekly_plan_stat")
    private Integer weeklyPlanStat;

    // M — недельный факт (стац.)
    @Column(name = "weekly_fact_stat")
    private Integer weeklyFactStat;

    // N — динамика (стац.) — из Excel
    @Column(name = "dynamic_stat")
    private Integer dynamicStat;

    @Column(name = "report_week", nullable = false)
    private LocalDate reportWeek;

    public HcvWeeklyPlanRow() {}

    public HcvWeeklyPlanRow(String orgName,
                             Integer planYearAmb, Integer factYearAmb, Integer pctAmb,
                             Integer weeklyPlanAmb, Integer weeklyFactAmb, Integer dynamicAmb,
                             Integer planYearStat, Integer referralsStat, Integer pctStat,
                             Integer weeklyPlanStat, Integer weeklyFactStat, Integer dynamicStat,
                             LocalDate reportWeek) {
        this.orgName        = orgName;
        this.planYearAmb    = planYearAmb;
        this.factYearAmb    = factYearAmb;
        this.pctAmb         = pctAmb;
        this.weeklyPlanAmb  = weeklyPlanAmb;
        this.weeklyFactAmb  = weeklyFactAmb;
        this.dynamicAmb     = dynamicAmb;
        this.planYearStat   = planYearStat;
        this.referralsStat  = referralsStat;
        this.pctStat        = pctStat;
        this.weeklyPlanStat = weeklyPlanStat;
        this.weeklyFactStat = weeklyFactStat;
        this.dynamicStat    = dynamicStat;
        this.reportWeek     = reportWeek;
    }

    public Long      getId()             { return id; }
    public String    getOrgName()        { return orgName; }
    public Integer   getPlanYearAmb()    { return planYearAmb; }
    public Integer   getFactYearAmb()    { return factYearAmb; }
    public Integer   getPctAmb()         { return pctAmb; }
    public Integer   getWeeklyPlanAmb()  { return weeklyPlanAmb; }
    public Integer   getWeeklyFactAmb()  { return weeklyFactAmb; }
    public Integer   getDynamicAmb()     { return dynamicAmb; }
    public Integer   getPlanYearStat()   { return planYearStat; }
    public Integer   getReferralsStat()  { return referralsStat; }
    public Integer   getPctStat()        { return pctStat; }
    public Integer   getWeeklyPlanStat() { return weeklyPlanStat; }
    public Integer   getWeeklyFactStat() { return weeklyFactStat; }
    public Integer   getDynamicStat()    { return dynamicStat; }
    public LocalDate getReportWeek()     { return reportWeek; }
}
