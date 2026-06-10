package com.emias.dashboard.repository;

import com.emias.dashboard.entity.HcvRegistry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface HcvRegistryRepository extends JpaRepository<HcvRegistry, Long> {

    long count();

    @Modifying
    @Query("DELETE FROM HcvRegistry")
    void deleteAll();
}
