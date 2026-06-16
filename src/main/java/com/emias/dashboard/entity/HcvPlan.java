package com.emias.dashboard.entity;

import jakarta.persistence.*;

/**
 * Плановые показатели МО по 4ДИ-проектам Гепатит С.
 * projectId: 1=Лечение ГС, 2=Амбулаторная помощь, 3=ПЦР-тестирование
 */
@Entity
@Table(name = "hcv_plans", uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "org_name"}))
public class HcvPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private int projectId;

    @Column(name = "org_name", nullable = false, length = 500)
    private String orgName;

    @Column(name = "annual_plan")
    private int annualPlan;

    @Column(name = "weekly_plan")
    private int weeklyPlan;

    public HcvPlan() {}

    public HcvPlan(int projectId, String orgName, int annualPlan, int weeklyPlan) {
        this.projectId  = projectId;
        this.orgName    = orgName;
        this.annualPlan = annualPlan;
        this.weeklyPlan = weeklyPlan;
    }

    public Long   getId()         { return id; }
    public int    getProjectId()  { return projectId; }
    public String getOrgName()    { return orgName; }
    public int    getAnnualPlan() { return annualPlan; }
    public int    getWeeklyPlan() { return weeklyPlan; }

    public void setAnnualPlan(int annualPlan) { this.annualPlan = annualPlan; }
    public void setWeeklyPlan(int weeklyPlan) { this.weeklyPlan = weeklyPlan; }
}
