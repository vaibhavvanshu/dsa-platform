package com.capstone.dsaplatform.repository;

import com.capstone.dsaplatform.entity.Problem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProblemRepository extends JpaRepository<Problem, Long> {

    // Topic fetched with the problem so lists can show topic names without N+1 queries.
    // Sorting is done in Java: difficulty is stored as text, and "EASY < HARD < MEDIUM" alphabetically is wrong.
    @Query("select p from Problem p join fetch p.topic")
    List<Problem> findAllWithTopic();
}
