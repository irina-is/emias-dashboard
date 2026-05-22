package com.emias.dashboard.repository;

import com.emias.dashboard.entity.FacilityMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FacilityMappingRepository extends JpaRepository<FacilityMapping, Long> {

    List<FacilityMapping> findAllByOrderByScreeningNameAsc();

    Optional<FacilityMapping> findByScreeningName(String screeningName);
}
