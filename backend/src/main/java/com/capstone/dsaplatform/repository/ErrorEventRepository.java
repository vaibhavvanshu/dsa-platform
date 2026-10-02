package com.capstone.dsaplatform.repository;

import com.capstone.dsaplatform.entity.ErrorEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ErrorEventRepository extends JpaRepository<ErrorEvent, Long> {

    // Topic fetched in the same query because the weakness profile shows topic names per category.
    @Query("select e from ErrorEvent e join fetch e.topic where e.user.id = :userId")
    List<ErrorEvent> findByUserIdWithTopic(@Param("userId") Long userId);
}
