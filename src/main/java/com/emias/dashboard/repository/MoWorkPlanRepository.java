package com.emias.dashboard.repository;

import com.emias.dashboard.entity.MoWorkPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface MoWorkPlanRepository extends JpaRepository<MoWorkPlan, Long> {

    List<MoWorkPlan> findByReportMonthOrderByOrgNameAsc(LocalDate reportMonth);

    void deleteByReportMonth(LocalDate reportMonth);

    @Query("SELECT DISTINCT m.reportMonth FROM MoWorkPlan m ORDER BY m.reportMonth DESC")
    List<LocalDate> findDistinctMonths();
}
