package com.emias.dashboard.repository;

import com.emias.dashboard.entity.FacilityPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FacilityPlanRepository extends JpaRepository<FacilityPlan, Long> {

    /** Все организации, отсортированные по имени */
    List<FacilityPlan> findAllByOrderByFacilityNameAsc();

    /** Найти план по точному названию организации */
    Optional<FacilityPlan> findByFacilityName(String facilityName);
}
