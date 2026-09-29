package com.emias.dashboard.repository;

import com.emias.dashboard.entity.DsChuzPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;

public interface DsChuzPlanRepository extends JpaRepository<DsChuzPlan, Long> {

    List<DsChuzPlan> findAllByOrderByMoAsc();

    @Query("SELECT MAX(r.uploadedAt) FROM DsChuzPlan r")
    LocalDateTime lastUploadedAt();
}
