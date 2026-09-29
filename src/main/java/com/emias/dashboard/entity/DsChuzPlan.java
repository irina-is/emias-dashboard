package com.emias.dashboard.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Одна строка файла «дс_с чуз.xlsx» — план стационарной помощи на 2026 год по МО
 * и число пациентов, пролеченных в ЧУЗ (Гармония).
 */
@Entity
@Table(name = "ds_chuz_plan")
public class DsChuzPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // A — №
    @Column(name = "num")
    private Integer num;

    // B — МО
    @Column(name = "mo", length = 300)
    private String mo;

    // C — План 2026
    @Column(name = "plan2026")
    private Integer plan2026;

    // D — ЧУЗ (Гармония)
    @Column(name = "chuz_treated")
    private Integer chuzTreated;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    public DsChuzPlan() {}

    public Long getId() { return id; }
    public Integer getNum() { return num; }
    public String getMo() { return mo; }
    public Integer getPlan2026() { return plan2026; }
    public Integer getChuzTreated() { return chuzTreated; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }

    public void setNum(Integer v) { num = v; }
    public void setMo(String v) { mo = v; }
    public void setPlan2026(Integer v) { plan2026 = v; }
    public void setChuzTreated(Integer v) { chuzTreated = v; }
    public void setUploadedAt(LocalDateTime v) { uploadedAt = v; }
}
