package com.emias.dashboard.entity;

import jakarta.persistence.*;

/**
 * Одна строка из свода ВГС (реестр пациентов с гепатитом С).
 * Колонки соответствуют листу «СВОД» файла-свода.
 */
@Entity
@Table(name = "hcv_registry", indexes = {
    @Index(name = "idx_hcv_territory", columnList = "territory"),
    @Index(name = "idx_hcv_pcr_status", columnList = "pcr_status"),
    @Index(name = "idx_hcv_treatment_status", columnList = "treatment_status")
})
public class HcvRegistry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Кол.1: Территория (МО) */
    @Column(name = "territory", length = 500)
    private String territory;

    /** Кол.2: Статус ВИМИС (да/нет) + ID */
    @Column(name = "vimis_status", length = 200)
    private String vimisStatus;

    /** Кол.3: ФИО пациента */
    @Column(name = "full_name", length = 500)
    private String fullName;

    /** Кол.4: СНИЛС */
    @Column(name = "snils", length = 50)
    private String snils;

    /** Кол.5: СВО */
    @Column(name = "svo_status", length = 300)
    private String svoStatus;

    /** Кол.6: ПЦР (положительный/отрицательный/отсутствует информация) */
    @Column(name = "pcr_status", length = 300)
    private String pcrStatus;

    /** Кол.7: ВГС / Генотип */
    @Column(name = "hcv_genotype", length = 300)
    private String hcvGenotype;

    /** Кол.8: Стадия фиброза */
    @Column(name = "fibrosis_stage", length = 300)
    private String fibrosisStage;

    /** Кол.9: Заключение МОНИКИ/Центр СПИД (да/нет) */
    @Column(name = "monik_conclusion", length = 300)
    private String monikConclusion;

    /** Кол.10: Дата заключения МОНИКИ */
    @Column(name = "monik_conclusion_date", length = 50)
    private String monikConclusionDate;

    /** Кол.11: Условия МП (амб./ДС) */
    @Column(name = "care_conditions", length = 300)
    private String careConditions;

    /** Кол.12: Схема лечения (код) */
    @Column(name = "treatment_scheme", length = 300)
    private String treatmentScheme;

    /** Кол.13: Продолжительность терапии (недели) */
    @Column(name = "treatment_duration_weeks", length = 300)
    private String treatmentDurationWeeks;

    /** Кол.14: Дата выдачи 057у / рецепта */
    @Column(name = "referral_date", length = 50)
    private String referralDate;

    /** Кол.15: Куда маршрутизирован (МО) */
    @Column(name = "routed_to", length = 500)
    private String routedTo;

    /** Кол.16: Статус (в очереди / госпитализирован / прервал / завершен) */
    @Column(name = "treatment_status", length = 300)
    private String treatmentStatus;

    /** Кол.17: Дата начала курса */
    @Column(name = "treatment_start_date", length = 50)
    private String treatmentStartDate;

    /** Кол.18: Дата завершения курса */
    @Column(name = "treatment_end_date", length = 50)
    private String treatmentEndDate;

    /** Кол.19: Дата ПЦР УВО12 */
    @Column(name = "uvo12_pcr_date", length = 50)
    private String uvo12PcrDate;

    /** Кол.20: Результат УВО12 */
    @Column(name = "uvo12_result", length = 300)
    private String uvo12Result;

    /** Кол.21: Примечания */
    @Column(name = "notes", length = 1000)
    private String notes;

    public HcvRegistry() {}

    // Геттеры
    public Long   getId()                    { return id; }
    public String getTerritory()             { return territory; }
    public String getVimisStatus()           { return vimisStatus; }
    public String getFullName()              { return fullName; }
    public String getSnils()                 { return snils; }
    public String getSvoStatus()             { return svoStatus; }
    public String getPcrStatus()             { return pcrStatus; }
    public String getHcvGenotype()           { return hcvGenotype; }
    public String getFibrosisStage()         { return fibrosisStage; }
    public String getMonikConclusion()       { return monikConclusion; }
    public String getMonikConclusionDate()   { return monikConclusionDate; }
    public String getCareConditions()        { return careConditions; }
    public String getTreatmentScheme()       { return treatmentScheme; }
    public String getTreatmentDurationWeeks(){ return treatmentDurationWeeks; }
    public String getReferralDate()          { return referralDate; }
    public String getRoutedTo()              { return routedTo; }
    public String getTreatmentStatus()       { return treatmentStatus; }
    public String getTreatmentStartDate()    { return treatmentStartDate; }
    public String getTreatmentEndDate()      { return treatmentEndDate; }
    public String getUvo12PcrDate()          { return uvo12PcrDate; }
    public String getUvo12Result()           { return uvo12Result; }
    public String getNotes()                 { return notes; }

    // Сеттеры
    public void setTerritory(String v)              { territory = v; }
    public void setVimisStatus(String v)            { vimisStatus = v; }
    public void setFullName(String v)               { fullName = v; }
    public void setSnils(String v)                  { snils = v; }
    public void setSvoStatus(String v)              { svoStatus = v; }
    public void setPcrStatus(String v)              { pcrStatus = v; }
    public void setHcvGenotype(String v)            { hcvGenotype = v; }
    public void setFibrosisStage(String v)          { fibrosisStage = v; }
    public void setMonikConclusion(String v)        { monikConclusion = v; }
    public void setMonikConclusionDate(String v)    { monikConclusionDate = v; }
    public void setCareConditions(String v)         { careConditions = v; }
    public void setTreatmentScheme(String v)        { treatmentScheme = v; }
    public void setTreatmentDurationWeeks(String v) { treatmentDurationWeeks = v; }
    public void setReferralDate(String v)           { referralDate = v; }
    public void setRoutedTo(String v)               { routedTo = v; }
    public void setTreatmentStatus(String v)        { treatmentStatus = v; }
    public void setTreatmentStartDate(String v)     { treatmentStartDate = v; }
    public void setTreatmentEndDate(String v)       { treatmentEndDate = v; }
    public void setUvo12PcrDate(String v)           { uvo12PcrDate = v; }
    public void setUvo12Result(String v)            { uvo12Result = v; }
    public void setNotes(String v)                  { notes = v; }
}
