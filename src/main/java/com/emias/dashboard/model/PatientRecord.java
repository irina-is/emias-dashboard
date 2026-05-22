package com.emias.dashboard.model;

/**
 * Одна строка из Excel-отчёта.
 * Каждое поле соответствует одной колонке в файле.
 */
public class PatientRecord {

    private String mkabNumber;           // Номер МКАБ
    private String lastName;             // Фамилия
    private String firstName;            // Имя
    private String middleName;           // Отчество
    private String visitType;            // Диспансеризация / профосмотр
    private String snils;                // СНИЛС
    private String omsPolicy;            // Полис ОМС
    private String dispensarizationDate; // Дата диспансеризации
    private String researchDate;         // Дата исследования
    private String cardClosingDate;      // Дата закрытия дисп. карты
    private String birthDate;            // Дата рождения
    private String tfomsServiceCode;     // Код услуги ТФОМС
    private String valueText;            // Значение (Текст)
    private String referralNumber;       // Номер направления
    private String refusal;              // Отказ (1 - отказ)
    private String researchResult;       // Результат исследования
    private String serviceCode;          // Код услуги
    private String researchStatus;       // Статус исследования
    private String doctorName;           // Врач, подписавший карту
    private String ogrnFrom;             // ОГРН откуда направили
    private String facilityFrom;         // ЛПУ откуда направили
    private String ogrnTo;               // ОГРН куда направили
    private String facilityTo;           // ЛПУ куда направили
    private String pcrResult;            // Результат ПЦР
    private String pcrDone;              // Проводился ли ПЦР (1 - да)
    private String ageAtExport;          // Возраст на момент выгрузки
    private String ageAtResearch;        // Возраст на момент исследования
    private String biomaterialDate;      // Дата забора биоматериала
    private String deliveryDate;         // Дата доставки
    private String researchConductedDate;// Дата проведения исследования

    public PatientRecord(
            String mkabNumber, String lastName, String firstName, String middleName,
            String visitType, String snils, String omsPolicy,
            String dispensarizationDate, String researchDate, String cardClosingDate,
            String birthDate, String tfomsServiceCode, String valueText,
            String referralNumber, String refusal, String researchResult,
            String serviceCode, String researchStatus, String doctorName,
            String ogrnFrom, String facilityFrom, String ogrnTo, String facilityTo,
            String pcrResult, String pcrDone, String ageAtExport, String ageAtResearch,
            String biomaterialDate, String deliveryDate, String researchConductedDate
    ) {
        this.mkabNumber            = mkabNumber;
        this.lastName              = lastName;
        this.firstName             = firstName;
        this.middleName            = middleName;
        this.visitType             = visitType;
        this.snils                 = snils;
        this.omsPolicy             = omsPolicy;
        this.dispensarizationDate  = dispensarizationDate;
        this.researchDate          = researchDate;
        this.cardClosingDate       = cardClosingDate;
        this.birthDate             = birthDate;
        this.tfomsServiceCode      = tfomsServiceCode;
        this.valueText             = valueText;
        this.referralNumber        = referralNumber;
        this.refusal               = refusal;
        this.researchResult        = researchResult;
        this.serviceCode           = serviceCode;
        this.researchStatus        = researchStatus;
        this.doctorName            = doctorName;
        this.ogrnFrom              = ogrnFrom;
        this.facilityFrom          = facilityFrom;
        this.ogrnTo                = ogrnTo;
        this.facilityTo            = facilityTo;
        this.pcrResult             = pcrResult;
        this.pcrDone               = pcrDone;
        this.ageAtExport           = ageAtExport;
        this.ageAtResearch         = ageAtResearch;
        this.biomaterialDate       = biomaterialDate;
        this.deliveryDate          = deliveryDate;
        this.researchConductedDate = researchConductedDate;
    }

    public String getMkabNumber()            { return mkabNumber; }
    public String getLastName()              { return lastName; }
    public String getFirstName()             { return firstName; }
    public String getMiddleName()            { return middleName; }
    public String getVisitType()             { return visitType; }
    public String getSnils()                 { return snils; }
    public String getOmsPolicy()             { return omsPolicy; }
    public String getDispensarizationDate()  { return dispensarizationDate; }
    public String getResearchDate()          { return researchDate; }
    public String getCardClosingDate()       { return cardClosingDate; }
    public String getBirthDate()             { return birthDate; }
    public String getTfomsServiceCode()      { return tfomsServiceCode; }
    public String getValueText()             { return valueText; }
    public String getReferralNumber()        { return referralNumber; }
    public String getRefusal()               { return refusal; }
    public String getResearchResult()        { return researchResult; }
    public String getServiceCode()           { return serviceCode; }
    public String getResearchStatus()        { return researchStatus; }
    public String getDoctorName()            { return doctorName; }
    public String getOgrnFrom()              { return ogrnFrom; }
    public String getFacilityFrom()          { return facilityFrom; }
    public String getOgrnTo()               { return ogrnTo; }
    public String getFacilityTo()            { return facilityTo; }
    public String getPcrResult()             { return pcrResult; }
    public String getPcrDone()               { return pcrDone; }
    public String getAgeAtExport()           { return ageAtExport; }
    public String getAgeAtResearch()         { return ageAtResearch; }
    public String getBiomaterialDate()       { return biomaterialDate; }
    public String getDeliveryDate()          { return deliveryDate; }
    public String getResearchConductedDate() { return researchConductedDate; }
}
