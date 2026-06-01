package com.emias.dashboard.repository;

import com.emias.dashboard.entity.HcvProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HcvProgressRepository extends JpaRepository<HcvProgress, Long> {

    List<HcvProgress> findByProjectId(int projectId);

    Optional<HcvProgress> findByProjectIdAndOrgName(int projectId, String orgName);

    void deleteByProjectId(int projectId);
}
