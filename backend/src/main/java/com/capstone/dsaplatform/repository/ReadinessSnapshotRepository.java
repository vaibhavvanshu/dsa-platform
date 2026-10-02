package com.capstone.dsaplatform.repository;

import com.capstone.dsaplatform.entity.ReadinessSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReadinessSnapshotRepository extends JpaRepository<ReadinessSnapshot, Long> {

    // Newest 30 (id breaks ties within the same second); the service reverses them for the chart.
    List<ReadinessSnapshot> findTop30ByUserIdOrderByTakenAtDescIdDesc(Long userId);
}
