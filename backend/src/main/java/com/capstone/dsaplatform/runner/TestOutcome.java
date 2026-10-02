package com.capstone.dsaplatform.runner;

import com.capstone.dsaplatform.domain.TestTag;

/**
 * Raw result of one test process. The runner reports facts only; turning them into a
 * Verdict and an ErrorCategory is the next phase's job (CONTRACT.md sections 4.1 and 4.2).
 */
public record TestOutcome(Long testCaseId, TestTag tag, boolean passed,
                          boolean timedOut, String exceptionType, String actualOutput,
                          long runtimeMs) {
}
