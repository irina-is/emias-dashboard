package com.emias.dashboard.repository;

import com.emias.dashboard.entity.HcvWeeklyPlanRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface HcvWeeklyPlanRepository extends JpaRepository<HcvWeeklyPlanRow, Long> {

    List<HcvWeeklyPlanRow> findByReportWeekOrderByOrgNameAsc(LocalDate reportWeek);

    void deleteByReportWeek(LocalDate reportWeek);

    @Query("SELECT DISTINCT r.reportWeek FROM HcvWeeklyPlanRow r ORDER BY r.reportWeek DESC")
    List<LocalDate> findDistinctWeeks();
}
