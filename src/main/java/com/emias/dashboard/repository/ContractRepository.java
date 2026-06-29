package com.emias.dashboard.repository;

import com.emias.dashboard.entity.Contract;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ContractRepository extends JpaRepository<Contract, Long> {
    List<Contract> findAllByOrderByOrgNameAscDrugNameAsc();
}
