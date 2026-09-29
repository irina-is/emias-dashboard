package com.emias.dashboard.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Одна строка выгрузки «МО_Гепатит_ДДММГГГГ.xlsx» — отпуск препарата льготнику (амбулаторно).
 */
@Entity
@Table(name = "amb_dispenses")
public class AmbDispense {

    // SEQUENCE, а не IDENTITY — иначе Hibernate не пакетирует INSERT (в выгрузке ~4 тыс. строк)
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "amb_disp_seq")
    @SequenceGenerator(name = "amb_disp_seq", sequenceName = "amb_dispenses_seq", allocationSize = 500)
    private Long id;

    // A — СНИЛС, только цифры
    @Column(name = "snils", length = 20)
    private String snils;

    // B — Муниципальное образование
    @Column(name = "municipality", length = 300)
    private String municipality;

    // C — Наименование МО
    @Column(name = "mo", length = 1000)
    private String mo;

    // D — ФИО льготника
    @Column(name = "fio", length = 300)
    private String fio;

    @Column(name = "fio_key", length = 300)
    private String fioKey;

    // E — МНН
    @Column(name = "mnn", length = 300)
    private String mnn;

    // F — Кол-во уп.
    @Column(name = "qty")
    private Double qty;

    // G — Дата отпуска
    @Column(name = "dispense_date")
    private LocalDate dispenseDate;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    public AmbDispense() {}

    public Long getId() { return id; }
    public String getSnils() { return snils; }
    public String getMunicipality() { return municipality; }
    public String getMo() { return mo; }
    public String getFio() { return fio; }
    public String getFioKey() { return fioKey; }
    public String getMnn() { return mnn; }
    public Double getQty() { return qty; }
    public LocalDate getDispenseDate() { return dispenseDate; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }

    public void setSnils(String v) { snils = v; }
    public void setMunicipality(String v) { municipality = v; }
    public void setMo(String v) { mo = v; }
    public void setFio(String v) { fio = v; }
    public void setFioKey(String v) { fioKey = v; }
    public void setMnn(String v) { mnn = v; }
    public void setQty(Double v) { qty = v; }
    public void setDispenseDate(LocalDate v) { dispenseDate = v; }
    public void setUploadedAt(LocalDateTime v) { uploadedAt = v; }
}
