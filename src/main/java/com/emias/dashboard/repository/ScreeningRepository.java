package com.emias.dashboard.repository;

import com.emias.dashboard.entity.Screening;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ScreeningRepository extends JpaRepository<Screening, Long> {

    List<Screening> findByReportDate(LocalDate reportDate);

    Page<Screening> findByReportDate(LocalDate reportDate, Pageable pageable);

    @Query("SELECT s FROM Screening s WHERE s.reportDate = :date AND " +
           "(:search = '' OR s.mkabNumber LIKE CONCAT('%', :search, '%'))")
    Page<Screening> searchByReportDate(@Param("date") LocalDate date,
                                       @Param("search") String search,
                                       Pageable pageable);

    long countByReportDate(LocalDate reportDate);

    List<Screening> findByReportDateBetween(LocalDate from, LocalDate to);

    // @Modifying — выполняет DELETE напрямую, без буферизации JPA
    @Modifying
    @Query("DELETE FROM Screening s WHERE s.reportDate = :date")
    void deleteByReportDate(@Param("date") LocalDate date);
}
