package com.capstone.dsaplatform.runner;

import com.capstone.dsaplatform.entity.TestCase;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The runner's own query, kept in this package so the shared TestCaseRepository is not edited.
 * No ORDER BY here: tags are stored as strings, which sort alphabetically
 * (BOUNDARY, EDGE, LARGE, SAMPLE), so the runner sorts by enum order in Java instead.
 */
public interface RunnerTestCaseRepository extends JpaRepository<TestCase, Long> {
    List<TestCase> findByProblemId(Long problemId);
}
