package com.emias.dashboard.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "mo_work_plan")
public class MoWorkPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_name", nullable = false, length = 500)
    private String orgName;

    @Column(name = "week1")
    private Integer week1;

    @Column(name = "week2")
    private Integer week2;

    @Column(name = "week3")
    private Integer week3;

    @Column(name = "week4")
    private Integer week4;

    @Column(name = "worked")
    private Integer worked;

    // хранится как первый день месяца, например 2026-06-01
    @Column(name = "report_month", nullable = false)
    private LocalDate reportMonth;

    public MoWorkPlan() {}

    public MoWorkPlan(String orgName, Integer week1, Integer week2, Integer week3,
                      Integer week4, Integer worked, LocalDate reportMonth) {
        this.orgName = orgName;
        this.week1 = week1;
        this.week2 = week2;
        this.week3 = week3;
        this.week4 = week4;
        this.worked = worked;
        this.reportMonth = reportMonth;
    }

    public Long getId() { return id; }
    public String getOrgName() { return orgName; }
    public Integer getWeek1() { return week1; }
    public Integer getWeek2() { return week2; }
    public Integer getWeek3() { return week3; }
    public Integer getWeek4() { return week4; }
    public Integer getWorked() { return worked; }
    public LocalDate getReportMonth() { return reportMonth; }
}
