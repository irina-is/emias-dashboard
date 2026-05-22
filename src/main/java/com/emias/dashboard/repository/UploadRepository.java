package com.emias.dashboard.repository;

import com.emias.dashboard.entity.Upload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface UploadRepository extends JpaRepository<Upload, Long> {

    List<Upload> findAllByOrderByReportDateDesc();

    Optional<Upload> findByReportDate(LocalDate reportDate);

    // @Modifying — выполняет DELETE напрямую, без буферизации JPA
    @Modifying
    @Query("DELETE FROM Upload u WHERE u.reportDate = :date")
    void deleteByReportDate(@Param("date") LocalDate date);
}
