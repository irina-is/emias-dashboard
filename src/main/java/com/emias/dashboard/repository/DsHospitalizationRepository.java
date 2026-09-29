package com.emias.dashboard.repository;

import com.emias.dashboard.entity.DsHospitalization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;

public interface DsHospitalizationRepository extends JpaRepository<DsHospitalization, Long> {

    @Query("""
        SELECT r.mo,
               COUNT(r),
               SUM(CASE WHEN r.courses = 1 THEN 1 ELSE 0 END),
               SUM(CASE WHEN r.courses = 2 THEN 1 ELSE 0 END),
               SUM(CASE WHEN r.courses >= 3 THEN 1 ELSE 0 END)
        FROM DsHospitalization r
        GROUP BY r.mo
        ORDER BY COUNT(r) DESC, r.mo
        """)
    List<Object[]> summaryByMo();

    @Query("SELECT MAX(r.uploadedAt) FROM DsHospitalization r")
    LocalDateTime lastUploadedAt();
}
