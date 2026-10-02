package com.capstone.dsaplatform.runner;

import com.capstone.dsaplatform.entity.Problem;

/**
 * Compiles and runs one submission against all tests of a problem.
 * An interface so the submission pipeline does not care whether code runs in a local
 * process (this demo) or in a sandbox such as Judge0 (production).
 */
public interface CodeRunner {
    RunResult run(Problem problem, String code);
}
