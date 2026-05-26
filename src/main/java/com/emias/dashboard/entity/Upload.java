package com.emias.dashboard.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Запись о загрузке файла.
 * Одна строка = один загруженный Excel-файл.
 */
@Entity
@Table(name = "uploads")
public class Upload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_date", unique = true, nullable = false)
    private LocalDate reportDate;

    @Column(name = "record_count")
    private int recordCount;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    public Upload() {}

    public Upload(LocalDate reportDate, int recordCount, String fileName) {
        this.reportDate  = reportDate;
        this.recordCount = recordCount;
        this.fileName    = fileName;
        this.uploadedAt  = LocalDateTime.now();
    }

    public Long          getId()          { return id; }
    public LocalDate     getReportDate()  { return reportDate; }
    public int           getRecordCount() { return recordCount; }
    public String        getFileName()    { return fileName; }
    public LocalDateTime getUploadedAt()  { return uploadedAt; }
}
