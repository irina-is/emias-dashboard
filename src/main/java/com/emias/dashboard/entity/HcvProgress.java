package com.emias.dashboard.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Фактические показатели МО по 4ДИ-проектам Гепатит С (последняя загруженная неделя).
 */
@Entity
@Table(name = "hcv_progress", uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "org_name"}))
public class HcvProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private int projectId;

    @Column(name = "org_name", nullable = false, length = 500)
    private String orgName;

    @Column(name = "accumulated")
    private int accumulated;

    @Column(name = "last_week_actual")
    private int lastWeekActual;

    @Column(name = "report_date")
    private LocalDate reportDate;

    public HcvProgress() {}

    public HcvProgress(int projectId, String orgName, int accumulated, int lastWeekActual, LocalDate reportDate) {
        this.projectId      = projectId;
        this.orgName        = orgName;
        this.accumulated    = accumulated;
        this.lastWeekActual = lastWeekActual;
        this.reportDate     = reportDate;
    }

    public Long      getId()             { return id; }
    public int       getProjectId()      { return projectId; }
    public String    getOrgName()        { return orgName; }
    public int       getAccumulated()    { return accumulated; }
    public int       getLastWeekActual() { return lastWeekActual; }
    public LocalDate getReportDate()     { return reportDate; }

    public void setAccumulated(int v)    { accumulated = v; }
    public void setLastWeekActual(int v) { lastWeekActual = v; }
    public void setReportDate(LocalDate v) { reportDate = v; }
}
