package com.capstone.dsaplatform.repository;

import com.capstone.dsaplatform.entity.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    // Unordered on purpose: run order is tag-then-ordinal, which SQL can't express on a text column.
    List<TestCase> findByProblemId(Long problemId);
}
