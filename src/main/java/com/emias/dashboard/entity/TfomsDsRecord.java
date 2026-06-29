package com.emias.dashboard.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Одна строка из листа «Все МО» файла ДС_ТФОМС_СВОД.xlsx.
 */
@Entity
@Table(name = "tfoms_ds_records")
public class TfomsDsRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // A — МО (краткое)
    @Column(name = "mo_short", length = 200)
    private String moShort;

    // B — Наименование МО
    @Column(name = "mo_full", length = 500)
    private String moFull;

    // C–E — ФИО
    @Column(name = "last_name", length = 200)
    private String lastName;

    @Column(name = "first_name", length = 200)
    private String firstName;

    @Column(name = "middle_name", length = 200)
    private String middleName;

    // F — Дата рождения
    @Column(name = "birth_date")
    private LocalDate birthDate;

    // G — Код диагноза
    @Column(name = "diagnosis_code", length = 20)
    private String diagnosisCode;

    // H — Наименование услуги/КСГ
    @Column(name = "service_name", length = 500)
    private String serviceName;

    // I — Код услуги
    @Column(name = "service_code", length = 50)
    private String serviceCode;

    // J — DKK1/thc
    @Column(name = "thc_code", length = 50)
    private String thcCode;

    // K — Схема лечения
    @Column(name = "treatment_scheme", length = 1000)
    private String treatmentScheme;

    // L — Дата начала
    @Column(name = "date_start")
    private LocalDate dateStart;

    // M — Дата окончания
    @Column(name = "date_end")
    private LocalDate dateEnd;

    // N — Стоимость (руб.)
    @Column(name = "cost")
    private Long cost;

    // O — МО прикрепления
    @Column(name = "attachment_mo", length = 500)
    private String attachmentMo;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    public TfomsDsRecord() {}

    public Long getId() { return id; }
    public String getMoShort() { return moShort; }
    public String getMoFull() { return moFull; }
    public String getLastName() { return lastName; }
    public String getFirstName() { return firstName; }
    public String getMiddleName() { return middleName; }
    public LocalDate getBirthDate() { return birthDate; }
    public String getDiagnosisCode() { return diagnosisCode; }
    public String getServiceName() { return serviceName; }
    public String getServiceCode() { return serviceCode; }
    public String getThcCode() { return thcCode; }
    public String getTreatmentScheme() { return treatmentScheme; }
    public LocalDate getDateStart() { return dateStart; }
    public LocalDate getDateEnd() { return dateEnd; }
    public Long getCost() { return cost; }
    public String getAttachmentMo() { return attachmentMo; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }

    public void setMoShort(String v) { moShort = v; }
    public void setMoFull(String v) { moFull = v; }
    public void setLastName(String v) { lastName = v; }
    public void setFirstName(String v) { firstName = v; }
    public void setMiddleName(String v) { middleName = v; }
    public void setBirthDate(LocalDate v) { birthDate = v; }
    public void setDiagnosisCode(String v) { diagnosisCode = v; }
    public void setServiceName(String v) { serviceName = v; }
    public void setServiceCode(String v) { serviceCode = v; }
    public void setThcCode(String v) { thcCode = v; }
    public void setTreatmentScheme(String v) { treatmentScheme = v; }
    public void setDateStart(LocalDate v) { dateStart = v; }
    public void setDateEnd(LocalDate v) { dateEnd = v; }
    public void setCost(Long v) { cost = v; }
    public void setAttachmentMo(String v) { attachmentMo = v; }
    public void setUploadedAt(LocalDateTime v) { uploadedAt = v; }
}
