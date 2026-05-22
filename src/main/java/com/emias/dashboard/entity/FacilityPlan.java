package com.emias.dashboard.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Плановые показатели скрининга для одной медицинской организации.
 * Одна строка из Excel-файла планов = одна запись в этой таблице.
 *
 * При повторной загрузке файла планов вся таблица очищается и заполняется заново.
 */
@Entity
@Table(name = "facility_plans")
public class FacilityPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Название медицинской организации (первый столбец Excel) */
    @Column(name = "facility_name", nullable = false, length = 1000)
    private String facilityName;

    /** Когда был загружен файл планов */
    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    // ── Годовые планы ──────────────────────────────────────────────────────────

    /** План скрининг 25,35,45 (годовой) */
    @Column(name = "annual_plan_254545")
    private Long annualPlan254545;

    /** План скрининг все возраста (годовой) */
    @Column(name = "annual_plan_all_ages")
    private Long annualPlanAllAges;

    /** План скрининг общий (годовой) */
    @Column(name = "annual_plan_total")
    private Long annualPlanTotal;

    // ── Месячные планы ────────────────────────────────────────────────────────

    /** План скрининг 25,35,45 (месячный) */
    @Column(name = "monthly_plan_254545")
    private Long monthlyPlan254545;

    /** План скрининг все возраста (месячный) */
    @Column(name = "monthly_plan_all_ages")
    private Long monthlyPlanAllAges;

    /** План скрининг общий (месячный) */
    @Column(name = "monthly_plan_total")
    private Long monthlyPlanTotal;

    // ── Недельные планы ───────────────────────────────────────────────────────

    /** План скрининг 25,35,45 (недельный) */
    @Column(name = "weekly_plan_254545")
    private Long weeklyPlan254545;

    /** План скрининг все возраста (недельный) */
    @Column(name = "weekly_plan_all_ages")
    private Long weeklyPlanAllAges;

    /** План скрининг общий (недельный) */
    @Column(name = "weekly_plan_total")
    private Long weeklyPlanTotal;

    public FacilityPlan() {}

    // ── Геттеры ───────────────────────────────────────────────────────────────

    public Long          getId()                  { return id; }
    public String        getFacilityName()         { return facilityName; }
    public LocalDateTime getUploadedAt()           { return uploadedAt; }
    public Long          getAnnualPlan254545()     { return annualPlan254545; }
    public Long          getAnnualPlanAllAges()    { return annualPlanAllAges; }
    public Long          getAnnualPlanTotal()      { return annualPlanTotal; }
    public Long          getMonthlyPlan254545()    { return monthlyPlan254545; }
    public Long          getMonthlyPlanAllAges()   { return monthlyPlanAllAges; }
    public Long          getMonthlyPlanTotal()     { return monthlyPlanTotal; }
    public Long          getWeeklyPlan254545()     { return weeklyPlan254545; }
    public Long          getWeeklyPlanAllAges()    { return weeklyPlanAllAges; }
    public Long          getWeeklyPlanTotal()      { return weeklyPlanTotal; }

    // ── Сеттеры ───────────────────────────────────────────────────────────────

    public void setFacilityName(String v)          { facilityName = v; }
    public void setUploadedAt(LocalDateTime v)     { uploadedAt = v; }
    public void setAnnualPlan254545(Long v)        { annualPlan254545 = v; }
    public void setAnnualPlanAllAges(Long v)       { annualPlanAllAges = v; }
    public void setAnnualPlanTotal(Long v)         { annualPlanTotal = v; }
    public void setMonthlyPlan254545(Long v)       { monthlyPlan254545 = v; }
    public void setMonthlyPlanAllAges(Long v)      { monthlyPlanAllAges = v; }
    public void setMonthlyPlanTotal(Long v)        { monthlyPlanTotal = v; }
    public void setWeeklyPlan254545(Long v)        { weeklyPlan254545 = v; }
    public void setWeeklyPlanAllAges(Long v)       { weeklyPlanAllAges = v; }
    public void setWeeklyPlanTotal(Long v)         { weeklyPlanTotal = v; }
}
