package com.emias.dashboard.repository;

import com.emias.dashboard.entity.DsHospPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;

public interface DsHospPlanRepository extends JpaRepository<DsHospPlan, Long> {

    List<DsHospPlan> findAllByOrderBySortOrderAsc();

    @Query("SELECT MAX(r.uploadedAt) FROM DsHospPlan r")
    LocalDateTime lastUploadedAt();
}
