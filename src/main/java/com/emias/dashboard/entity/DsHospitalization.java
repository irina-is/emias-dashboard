package com.emias.dashboard.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Одна строка файла «Путь пациента при лечении гепатита.xlsx» —
 * пациент, госпитализированный в дневной стационар, с данными до трёх курсов.
 */
@Entity
@Table(name = "ds_hospitalizations")
public class DsHospitalization {

    // SEQUENCE, а не IDENTITY — иначе Hibernate не пакетирует INSERT (в файле ~3 тыс. строк)
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ds_hosp_seq")
    @SequenceGenerator(name = "ds_hosp_seq", sequenceName = "ds_hospitalizations_seq", allocationSize = 500)
    private Long id;

    // B — ОГРН прикрепления
    @Column(name = "attach_ogrn", length = 20)
    private String attachOgrn;

    // C — ЛПУ прикрепления
    @Column(name = "attach_mo", length = 500)
    private String attachMo;

    // D — Дата рождения
    @Column(name = "birth_date")
    private LocalDate birthDate;

    // E — Номер МКАБ
    @Column(name = "mkab", length = 100)
    private String mkab;

    // F — ОГРН
    @Column(name = "ogrn", length = 20)
    private String ogrn;

    // G — ЛПУ (госпитализации)
    @Column(name = "mo", length = 500)
    private String mo;

    // J — number (номер шаблона)
    @Column(name = "template_number", length = 50)
    private String templateNumber;

    // K — Дата заполнения шаблона
    @Column(name = "template_date")
    private LocalDate templateDate;

    // L — Рекомендации
    @Column(name = "recommendations", length = 10000)
    private String recommendations;

    // M — Заключение
    @Column(name = "conclusion", length = 10000)
    private String conclusion;

    // ── 1 курс: H, I, N, O, P, Q ──
    @Column(name = "dept1", length = 500)
    private String dept1;
    @Column(name = "hosp_date1")
    private LocalDate hospDate1;
    @Column(name = "drug1", length = 1000)
    private String drug1;
    @Column(name = "comment1", length = 2000)
    private String comment1;
    @Column(name = "prescribed_date1")
    private LocalDate prescribedDate1;
    @Column(name = "qty1")
    private Integer qty1;

    // ── 2 курс: R, S, T, U, V, W, X ──
    @Column(name = "dept2", length = 500)
    private String dept2;
    @Column(name = "hosp_date2")
    private LocalDate hospDate2;
    @Column(name = "drug2", length = 1000)
    private String drug2;
    @Column(name = "comment2", length = 2000)
    private String comment2;
    @Column(name = "prescribed_date2")
    private LocalDate prescribedDate2;
    @Column(name = "qty2")
    private Integer qty2;
    @Column(name = "cancel_date2")
    private LocalDate cancelDate2;

    // ── 3 курс: Y, Z, AA, AB, AC, AD, AE ──
    @Column(name = "dept3", length = 500)
    private String dept3;
    @Column(name = "hosp_date3")
    private LocalDate hospDate3;
    @Column(name = "drug3", length = 1000)
    private String drug3;
    @Column(name = "comment3", length = 2000)
    private String comment3;
    @Column(name = "prescribed_date3")
    private LocalDate prescribedDate3;
    @Column(name = "qty3")
    private Integer qty3;
    @Column(name = "cancel_date3")
    private LocalDate cancelDate3;

    // AF — Количество курсов
    @Column(name = "courses")
    private Integer courses;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    public DsHospitalization() {}

    public Long getId() { return id; }
    public String getAttachOgrn() { return attachOgrn; }
    public String getAttachMo() { return attachMo; }
    public LocalDate getBirthDate() { return birthDate; }
    public String getMkab() { return mkab; }
    public String getOgrn() { return ogrn; }
    public String getMo() { return mo; }
    public String getTemplateNumber() { return templateNumber; }
    public LocalDate getTemplateDate() { return templateDate; }
    public String getRecommendations() { return recommendations; }
    public String getConclusion() { return conclusion; }
    public String getDept1() { return dept1; }
    public LocalDate getHospDate1() { return hospDate1; }
    public String getDrug1() { return drug1; }
    public String getComment1() { return comment1; }
    public LocalDate getPrescribedDate1() { return prescribedDate1; }
    public Integer getQty1() { return qty1; }
    public String getDept2() { return dept2; }
    public LocalDate getHospDate2() { return hospDate2; }
    public String getDrug2() { return drug2; }
    public String getComment2() { return comment2; }
    public LocalDate getPrescribedDate2() { return prescribedDate2; }
    public Integer getQty2() { return qty2; }
    public LocalDate getCancelDate2() { return cancelDate2; }
    public String getDept3() { return dept3; }
    public LocalDate getHospDate3() { return hospDate3; }
    public String getDrug3() { return drug3; }
    public String getComment3() { return comment3; }
    public LocalDate getPrescribedDate3() { return prescribedDate3; }
    public Integer getQty3() { return qty3; }
    public LocalDate getCancelDate3() { return cancelDate3; }
    public Integer getCourses() { return courses; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }

    public void setAttachOgrn(String v) { attachOgrn = v; }
    public void setAttachMo(String v) { attachMo = v; }
    public void setBirthDate(LocalDate v) { birthDate = v; }
    public void setMkab(String v) { mkab = v; }
    public void setOgrn(String v) { ogrn = v; }
    public void setMo(String v) { mo = v; }
    public void setTemplateNumber(String v) { templateNumber = v; }
    public void setTemplateDate(LocalDate v) { templateDate = v; }
    public void setRecommendations(String v) { recommendations = v; }
    public void setConclusion(String v) { conclusion = v; }
    public void setDept1(String v) { dept1 = v; }
    public void setHospDate1(LocalDate v) { hospDate1 = v; }
    public void setDrug1(String v) { drug1 = v; }
    public void setComment1(String v) { comment1 = v; }
    public void setPrescribedDate1(LocalDate v) { prescribedDate1 = v; }
    public void setQty1(Integer v) { qty1 = v; }
    public void setDept2(String v) { dept2 = v; }
    public void setHospDate2(LocalDate v) { hospDate2 = v; }
    public void setDrug2(String v) { drug2 = v; }
    public void setComment2(String v) { comment2 = v; }
    public void setPrescribedDate2(LocalDate v) { prescribedDate2 = v; }
    public void setQty2(Integer v) { qty2 = v; }
    public void setCancelDate2(LocalDate v) { cancelDate2 = v; }
    public void setDept3(String v) { dept3 = v; }
    public void setHospDate3(LocalDate v) { hospDate3 = v; }
    public void setDrug3(String v) { drug3 = v; }
    public void setComment3(String v) { comment3 = v; }
    public void setPrescribedDate3(LocalDate v) { prescribedDate3 = v; }
    public void setQty3(Integer v) { qty3 = v; }
    public void setCancelDate3(LocalDate v) { cancelDate3 = v; }
    public void setCourses(Integer v) { courses = v; }
    public void setUploadedAt(LocalDateTime v) { uploadedAt = v; }
}
