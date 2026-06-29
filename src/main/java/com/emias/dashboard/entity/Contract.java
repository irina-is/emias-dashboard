package com.emias.dashboard.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "contracts")
public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_name", nullable = false, length = 500)
    private String orgName;

    @Column(name = "drug_name", length = 500)
    private String drugName;

    @Column(name = "contract_number", length = 200)
    private String contractNumber;

    @Column(name = "total_amount")
    private Long totalAmount;

    @Column(name = "executed_amount")
    private Long executedAmount;

    /** Статус из файла: «Исполнен», «Исполнение» и т.д. */
    @Column(name = "contract_status", length = 100)
    private String contractStatus;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    public Contract() {}

    public Long          getId()             { return id; }
    public String        getOrgName()        { return orgName; }
    public String        getDrugName()       { return drugName; }
    public String        getContractNumber() { return contractNumber; }
    public Long          getTotalAmount()    { return totalAmount; }
    public Long          getExecutedAmount() { return executedAmount; }
    public String        getContractStatus() { return contractStatus; }
    public LocalDateTime getUploadedAt()     { return uploadedAt; }

    public void setOrgName(String v)           { orgName = v; }
    public void setDrugName(String v)          { drugName = v; }
    public void setContractNumber(String v)    { contractNumber = v; }
    public void setTotalAmount(Long v)         { totalAmount = v; }
    public void setExecutedAmount(Long v)      { executedAmount = v; }
    public void setContractStatus(String v)    { contractStatus = v; }
    public void setUploadedAt(LocalDateTime v) { uploadedAt = v; }

    /**
     * Нормализованный статус для фронтенда: исполнен / без_заявок / частично.
     * Если в файле стоит «Исполнен» — доверяем файлу.
     */
    public String getStatus() {
        if (executedAmount == null || executedAmount == 0) return "без_заявок";
        if ("исполнен".equalsIgnoreCase(contractStatus != null ? contractStatus.trim() : ""))
            return "исполнен";
        if (totalAmount != null && totalAmount > 0 && executedAmount >= totalAmount)
            return "исполнен";
        return "частично";
    }
}
