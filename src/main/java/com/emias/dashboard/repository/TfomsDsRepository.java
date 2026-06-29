package com.emias.dashboard.repository;

import com.emias.dashboard.entity.TfomsDsRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface TfomsDsRepository extends JpaRepository<TfomsDsRecord, Long> {

    List<TfomsDsRecord> findAllByOrderByMoShortAscLastNameAsc();

    @Query("""
        SELECT r.moShort, r.moFull,
               COUNT(r) as totalRecords,
               SUM(r.cost) as totalCost
        FROM TfomsDsRecord r
        GROUP BY r.moShort, r.moFull
        ORDER BY r.moShort
        """)
    List<Object[]> summaryByMo();
}
