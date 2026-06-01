package com.emias.dashboard.repository;

import com.emias.dashboard.entity.HcvPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HcvPlanRepository extends JpaRepository<HcvPlan, Long> {

    List<HcvPlan> findByProjectIdOrderByOrgName(int projectId);

    Optional<HcvPlan> findByProjectIdAndOrgName(int projectId, String orgName);

    long countByProjectId(int projectId);
}
