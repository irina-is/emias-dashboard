package com.emias.dashboard.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Одна строка из Excel-отчёта, сохранённая в базе данных.
 */
@Entity
@Table(name = "screenings")
public class Screening {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // К какому файлу относится запись
    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    // Поля пациента — все как строки, в том же виде что в Excel
    private String mkabNumber;
    private String lastName;
    private String firstName;
    private String middleName;
    private String visitType;
    private String snils;
    private String omsPolicy;
    private String dispensarizationDate;
    private String researchDate;
    private String cardClosingDate;
    private String birthDate;
    private String tfomsServiceCode;

    @Column(length = 1000)
    private String valueText;

    private String referralNumber;
    private String refusal;
    private String researchResult;
    private String serviceCode;
    private String researchStatus;
    private String doctorName;
    private String ogrnFrom;

    @Column(length = 500)
    private String facilityFrom;

    private String ogrnTo;

    @Column(length = 500)
    private String facilityTo;

    private String pcrResult;
    private String pcrDone;
    private String ageAtExport;
    private String ageAtResearch;
    private String biomaterialDate;
    private String deliveryDate;
    private String researchConductedDate;

    public Screening() {}

    // Геттеры
    public Long      getId()                    { return id; }
    public LocalDate getReportDate()             { return reportDate; }
    public String    getMkabNumber()             { return mkabNumber; }
    public String    getLastName()               { return lastName; }
    public String    getFirstName()              { return firstName; }
    public String    getMiddleName()             { return middleName; }
    public String    getVisitType()              { return visitType; }
    public String    getSnils()                  { return snils; }
    public String    getOmsPolicy()              { return omsPolicy; }
    public String    getDispensarizationDate()   { return dispensarizationDate; }
    public String    getResearchDate()           { return researchDate; }
    public String    getCardClosingDate()        { return cardClosingDate; }
    public String    getBirthDate()              { return birthDate; }
    public String    getTfomsServiceCode()       { return tfomsServiceCode; }
    public String    getValueText()              { return valueText; }
    public String    getReferralNumber()         { return referralNumber; }
    public String    getRefusal()                { return refusal; }
    public String    getResearchResult()         { return researchResult; }
    public String    getServiceCode()            { return serviceCode; }
    public String    getResearchStatus()         { return researchStatus; }
    public String    getDoctorName()             { return doctorName; }
    public String    getOgrnFrom()               { return ogrnFrom; }
    public String    getFacilityFrom()           { return facilityFrom; }
    public String    getOgrnTo()                 { return ogrnTo; }
    public String    getFacilityTo()             { return facilityTo; }
    public String    getPcrResult()              { return pcrResult; }
    public String    getPcrDone()                { return pcrDone; }
    public String    getAgeAtExport()            { return ageAtExport; }
    public String    getAgeAtResearch()          { return ageAtResearch; }
    public String    getBiomaterialDate()        { return biomaterialDate; }
    public String    getDeliveryDate()           { return deliveryDate; }
    public String    getResearchConductedDate()  { return researchConductedDate; }

    // Сеттеры
    public void setReportDate(LocalDate v)            { reportDate = v; }
    public void setMkabNumber(String v)               { mkabNumber = v; }
    public void setLastName(String v)                 { lastName = v; }
    public void setFirstName(String v)                { firstName = v; }
    public void setMiddleName(String v)               { middleName = v; }
    public void setVisitType(String v)                { visitType = v; }
    public void setSnils(String v)                    { snils = v; }
    public void setOmsPolicy(String v)                { omsPolicy = v; }
    public void setDispensarizationDate(String v)     { dispensarizationDate = v; }
    public void setResearchDate(String v)             { researchDate = v; }
    public void setCardClosingDate(String v)          { cardClosingDate = v; }
    public void setBirthDate(String v)                { birthDate = v; }
    public void setTfomsServiceCode(String v)         { tfomsServiceCode = v; }
    public void setValueText(String v)                { valueText = v; }
    public void setReferralNumber(String v)           { referralNumber = v; }
    public void setRefusal(String v)                  { refusal = v; }
    public void setResearchResult(String v)           { researchResult = v; }
    public void setServiceCode(String v)              { serviceCode = v; }
    public void setResearchStatus(String v)           { researchStatus = v; }
    public void setDoctorName(String v)               { doctorName = v; }
    public void setOgrnFrom(String v)                 { ogrnFrom = v; }
    public void setFacilityFrom(String v)             { facilityFrom = v; }
    public void setOgrnTo(String v)                   { ogrnTo = v; }
    public void setFacilityTo(String v)               { facilityTo = v; }
    public void setPcrResult(String v)                { pcrResult = v; }
    public void setPcrDone(String v)                  { pcrDone = v; }
    public void setAgeAtExport(String v)              { ageAtExport = v; }
    public void setAgeAtResearch(String v)            { ageAtResearch = v; }
    public void setBiomaterialDate(String v)          { biomaterialDate = v; }
    public void setDeliveryDate(String v)             { deliveryDate = v; }
    public void setResearchConductedDate(String v)    { researchConductedDate = v; }
}
