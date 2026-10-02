package com.capstone.dsaplatform.runner;

import java.util.List;

/**
 * Result of a whole submission. When compiled is false, compileError holds the
 * compiler output and outcomes is empty, because no test runs (CONTRACT.md section 4.1, step 1).
 */
public record RunResult(boolean compiled, String compileError,
                        List<TestOutcome> outcomes) {
}
