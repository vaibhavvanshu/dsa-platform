package com.capstone.dsaplatform.learner;

import com.capstone.dsaplatform.domain.ErrorCategory;
import com.capstone.dsaplatform.domain.TestTag;
import com.capstone.dsaplatform.domain.Verdict;
import com.capstone.dsaplatform.runner.RunResult;
import com.capstone.dsaplatform.runner.TestOutcome;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns raw runner output into a verdict and ONE error category (CONTRACT.md sections 4.1 - 4.2).
 * Pure function of its inputs: no DB, no Spring, so the precedence table is fully unit-tested.
 */
public final class SubmissionEvaluator {

    /** Index exceptions are the runtime signature of an off-by-one (precedence step 3). */
    private static final Set<String> INDEX_EXCEPTIONS =
            Set.of("ArrayIndexOutOfBoundsException", "StringIndexOutOfBoundsException");

    /** SAMPLE -> EDGE -> BOUNDARY -> LARGE (enum declaration order), then ordinal. */
    public static final Comparator<TestInfo> RUN_ORDER =
            Comparator.comparing(TestInfo::tag).thenComparing(TestInfo::ordinal);

    private SubmissionEvaluator() {
    }

    /** The parts of a test_cases row that evaluation needs (the DB row is the authority on tag). */
    public record TestInfo(Long id, TestTag tag, int ordinal, ErrorCategory override) {
    }

    /** One test after evaluation. */
    public record TestRun(TestInfo test, Verdict verdict, TestOutcome outcome) {
    }

    /**
     * @param flaggedRun the first failing test, or the slowest LARGE test for SUBOPTIMAL; null otherwise
     * @param category null only for a clean ACCEPTED (then no error_event is written)
     */
    public record Evaluation(Verdict verdict,
                             int passedCount,
                             int totalCount,
                             Integer largeRuntimeMs,
                             List<TestRun> runs,
                             ErrorCategory category,
                             TestRun flaggedRun,
                             String message) {
    }

    public static Evaluation evaluate(RunResult result, List<TestInfo> tests, Integer optimalRuntimeMs, boolean dpTopic) {
        // Step 1: nothing ran, so there is no failing test to diagnose.
        if (!result.compiled()) {
            String message = result.compileError() == null ? "Compilation failed." : result.compileError();
            return new Evaluation(Verdict.COMPILE_ERROR, 0, 0, null, List.of(),
                    ErrorCategory.SYNTAX_COMPILATION, null, message);
        }

        Map<Long, TestInfo> testsById = tests.stream().collect(Collectors.toMap(TestInfo::id, Function.identity()));
        // Re-sort instead of trusting the runner's order: "first failure" must follow the contract's run order.
        List<TestRun> runs = result.outcomes().stream()
                .map(o -> new TestRun(lookup(testsById, o.testCaseId()), verdictOf(o), o))
                .sorted(Comparator.comparing(TestRun::test, RUN_ORDER))
                .toList();

        int passed = (int) runs.stream().filter(r -> r.verdict() == Verdict.ACCEPTED).count();
        Integer largeRuntimeMs = slowestCompletedLarge(runs)
                .map(r -> Math.toIntExact(r.outcome().runtimeMs()))
                .orElse(null);

        Optional<TestRun> firstFailure = runs.stream().filter(r -> r.verdict() != Verdict.ACCEPTED).findFirst();
        if (firstFailure.isPresent()) {
            TestRun f = firstFailure.get();
            return new Evaluation(f.verdict(), passed, runs.size(), largeRuntimeMs, runs,
                    categoryOfFailure(f, dpTopic), f, failureMessage(f));
        }

        // Step 7: everything passed but the LARGE input ran slower than the reference solution.
        if (optimalRuntimeMs != null && largeRuntimeMs != null && largeRuntimeMs > optimalRuntimeMs) {
            TestRun slowest = slowestCompletedLarge(runs).orElseThrow();
            String message = "All tests passed, but LARGE took " + largeRuntimeMs + " ms (optimal " + optimalRuntimeMs + " ms).";
            return new Evaluation(Verdict.ACCEPTED, passed, runs.size(), largeRuntimeMs, runs,
                    ErrorCategory.SUBOPTIMAL, slowest, message);
        }

        // Step 8: clean ACCEPTED, no category.
        return new Evaluation(Verdict.ACCEPTED, passed, runs.size(), largeRuntimeMs, runs,
                null, null, "All " + runs.size() + " tests passed.");
    }

    /** Precedence steps 2 - 6 for the first failing test. First matching step wins. */
    static ErrorCategory categoryOfFailure(TestRun failed, boolean dpTopic) {
        // Step 2: too slow is a complexity problem whatever the tag.
        if (failed.verdict() == Verdict.TIME_LIMIT_EXCEEDED) {
            return ErrorCategory.BAD_COMPLEXITY;
        }
        // Step 3: the exception itself is stronger evidence than the curator's guess.
        if (failed.verdict() == Verdict.RUNTIME_ERROR && INDEX_EXCEPTIONS.contains(simpleName(failed.outcome().exceptionType()))) {
            return ErrorCategory.OFF_BY_ONE;
        }
        // Step 4: a curator override beats the generic tag meaning (and step 6, below).
        if (failed.test().override() != null) {
            return failed.test().override();
        }
        // Step 5: the tag says what the test was probing. "NonZeroExit" lands here too.
        ErrorCategory byTag = switch (failed.test().tag()) {
            case SAMPLE -> ErrorCategory.WRONG_ALGORITHM;
            case EDGE -> ErrorCategory.MISSED_EDGE_CASE;
            case BOUNDARY -> ErrorCategory.OFF_BY_ONE;
            case LARGE -> ErrorCategory.BAD_COMPLEXITY;
        };
        // Step 6: in DP a wrong typical answer almost always means a wrong recurrence.
        if (byTag == ErrorCategory.WRONG_ALGORITHM && dpTopic) {
            return ErrorCategory.STATE_TRANSITION;
        }
        return byTag;
    }

    /** timedOut is checked before passed/exception because a killed process proves nothing else. */
    static Verdict verdictOf(TestOutcome o) {
        if (o.timedOut()) {
            return Verdict.TIME_LIMIT_EXCEEDED;
        }
        if (o.passed()) {
            return Verdict.ACCEPTED;
        }
        if (o.exceptionType() != null) {
            return Verdict.RUNTIME_ERROR;
        }
        return Verdict.WRONG_ANSWER;
    }

    // A timed-out LARGE test has no meaningful runtime, so it never counts as "slowest".
    private static Optional<TestRun> slowestCompletedLarge(List<TestRun> runs) {
        TestRun slowest = null;
        for (TestRun r : runs) {
            if (r.test().tag() == TestTag.LARGE && !r.outcome().timedOut()
                    && (slowest == null || r.outcome().runtimeMs() > slowest.outcome().runtimeMs())) {
                slowest = r; // strict ">" keeps the earliest test on ties
            }
        }
        return Optional.ofNullable(slowest);
    }

    private static String failureMessage(TestRun f) {
        return switch (f.verdict()) {
            case RUNTIME_ERROR -> f.outcome().exceptionType();
            case TIME_LIMIT_EXCEEDED -> "Time limit exceeded on a " + f.test().tag() + " test.";
            default -> "Wrong answer on a" + (f.test().tag() == TestTag.EDGE ? "n " : " ") + f.test().tag() + " test.";
        };
    }

    // Tolerates "java.lang.ArrayIndexOutOfBoundsException" as well as the simple name.
    private static String simpleName(String exceptionType) {
        return exceptionType == null ? null : exceptionType.substring(exceptionType.lastIndexOf('.') + 1);
    }

    private static TestInfo lookup(Map<Long, TestInfo> testsById, Long id) {
        TestInfo test = testsById.get(id);
        if (test == null) {
            throw new IllegalStateException("Runner returned an outcome for test case " + id + " which is not part of this problem");
        }
        return test;
    }
}
