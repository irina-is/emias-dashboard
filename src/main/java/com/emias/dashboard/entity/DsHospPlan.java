package com.emias.dashboard.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Одна строка файла «стационар по госпитализации.xlsx» — план госпитализаций на 2026 год
 * по ЛПУ, где лечат (город больницы), плюс строка «ЧУЗ».
 */
@Entity
@Table(name = "ds_hosp_plan")
public class DsHospPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Порядок строки в файле
    @Column(name = "sort_order")
    private Integer sortOrder;

    // A — МО
    @Column(name = "mo", length = 300)
    private String mo;

    // B — План 2026
    @Column(name = "plan2026")
    private Integer plan2026;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    public DsHospPlan() {}

    public Long getId() { return id; }
    public Integer getSortOrder() { return sortOrder; }
    public String getMo() { return mo; }
    public Integer getPlan2026() { return plan2026; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }

    public void setSortOrder(Integer v) { sortOrder = v; }
    public void setMo(String v) { mo = v; }
    public void setPlan2026(Integer v) { plan2026 = v; }
    public void setUploadedAt(LocalDateTime v) { uploadedAt = v; }

    /** Строка «ЧУЗ» — частные учреждения, факт берётся из загрузчика ЧУЗ. */
    public boolean isChuz() {
        return mo != null && mo.trim().toUpperCase().startsWith("ЧУЗ");
    }
}
