package com.emias.dashboard.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Пациент из пофамильного плана амбулаторного лечения
 * (файлы «Гроза_..._СВОД ... с заменами.xlsx» / «Гроза_144_пациента_...xlsx»).
 */
@Entity
@Table(name = "amb_plan_patients")
public class AmbPlanPatient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Прикрепление
    @Column(name = "attach_mo", length = 300)
    private String attachMo;

    // ФИО (кто получает)
    @Column(name = "fio", length = 300)
    private String fio;

    // ФИО в верхнем регистре, Ё→Е, одиночные пробелы — для поиска в выгрузке
    @Column(name = "fio_key", length = 300)
    private String fioKey;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    // «Замена (кого заменили)» — ключ ФИО заменённого пациента; он не должен вернуться в план при догрузке
    @Column(name = "replaced_fio_key", length = 300)
    private String replacedFioKey;

    // Только цифры СНИЛС (в сводном файле его нет)
    @Column(name = "snils", length = 20)
    private String snils;

    @Column(name = "drug", length = 300)
    private String drug;

    @Column(name = "packs")
    private Integer packs;

    // Из какого файла загружен
    @Column(name = "source_file", length = 300)
    private String sourceFile;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    public AmbPlanPatient() {}

    public Long getId() { return id; }
    public String getAttachMo() { return attachMo; }
    public String getFio() { return fio; }
    public String getFioKey() { return fioKey; }
    public LocalDate getBirthDate() { return birthDate; }
    public String getReplacedFioKey() { return replacedFioKey; }
    public String getSnils() { return snils; }
    public String getDrug() { return drug; }
    public Integer getPacks() { return packs; }
    public String getSourceFile() { return sourceFile; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }

    public void setAttachMo(String v) { attachMo = v; }
    public void setFio(String v) { fio = v; }
    public void setFioKey(String v) { fioKey = v; }
    public void setBirthDate(LocalDate v) { birthDate = v; }
    public void setReplacedFioKey(String v) { replacedFioKey = v; }
    public void setSnils(String v) { snils = v; }
    public void setDrug(String v) { drug = v; }
    public void setPacks(Integer v) { packs = v; }
    public void setSourceFile(String v) { sourceFile = v; }
    public void setUploadedAt(LocalDateTime v) { uploadedAt = v; }
}
