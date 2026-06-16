package com.emias.dashboard.repository;

import com.emias.dashboard.entity.MoTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MoTaskRepository extends JpaRepository<MoTask, Long> {
    List<MoTask> findAllByOrderByIdAsc();
}
