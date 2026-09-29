package com.emias.dashboard.repository;

import com.emias.dashboard.entity.AmbDispense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDate;
import java.time.LocalDateTime;

public interface AmbDispenseRepository extends JpaRepository<AmbDispense, Long> {

    @Query("SELECT MAX(r.uploadedAt) FROM AmbDispense r")
    LocalDateTime lastUploadedAt();

    @Query("SELECT MAX(r.dispenseDate) FROM AmbDispense r")
    LocalDate maxDispenseDate();
}
