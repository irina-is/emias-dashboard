package com.emias.dashboard.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Соответствие между названием ЛПУ в скрининговых данных
 * и названием ЛПУ в файле планов.
 *
 * Левый столбец (screeningName):  "[010101] ГБУЗ МОСКОВСКОЙ ОБЛАСТИ «БАЛАШИХИНСКАЯ БОЛЬНИЦА»"
 * Правый столбец (planName):      "ГБУЗ МО «БАЛАШИХИНСКАЯ БОЛЬНИЦА»"
 */
@Entity
@Table(name = "facility_mappings")
public class FacilityMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Название ЛПУ как оно приходит в скрининговых данных (с кодом [XXXXXX]) */
    @Column(name = "screening_name", nullable = false, length = 1000)
    private String screeningName;

    /** Название ЛПУ как оно записано в файле планов */
    @Column(name = "plan_name", nullable = false, length = 1000)
    private String planName;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    public FacilityMapping() {}

    public Long          getId()           { return id; }
    public String        getScreeningName() { return screeningName; }
    public String        getPlanName()      { return planName; }
    public LocalDateTime getUploadedAt()   { return uploadedAt; }

    public void setScreeningName(String v)  { screeningName = v; }
    public void setPlanName(String v)       { planName = v; }
    public void setUploadedAt(LocalDateTime v) { uploadedAt = v; }
}
