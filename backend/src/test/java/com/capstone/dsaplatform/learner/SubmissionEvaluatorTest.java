package com.capstone.dsaplatform.learner;

import com.capstone.dsaplatform.domain.ErrorCategory;
import com.capstone.dsaplatform.domain.TestTag;
import com.capstone.dsaplatform.domain.Verdict;
import com.capstone.dsaplatform.learner.SubmissionEvaluator.Evaluation;
import com.capstone.dsaplatform.learner.SubmissionEvaluator.TestInfo;
import com.capstone.dsaplatform.runner.RunResult;
import com.capstone.dsaplatform.runner.TestOutcome;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Category precedence, CONTRACT.md section 4.2, one test per rule plus the tricky interactions. */
class SubmissionEvaluatorTest {

    // A typical 7-test problem: ids 1..7 with ordinals 1..7 in run order.
    private static final List<TestInfo> TESTS = List.of(
            new TestInfo(1L, TestTag.SAMPLE, 1, null),
            new TestInfo(2L, TestTag.SAMPLE, 2, null),
            new TestInfo(3L, TestTag.EDGE, 3, null),
            new TestInfo(4L, TestTag.EDGE, 4, null),
            new TestInfo(5L, TestTag.BOUNDARY, 5, null),
            new TestInfo(6L, TestTag.BOUNDARY, 6, null),
            new TestInfo(7L, TestTag.LARGE, 7, null));

    private static TestOutcome pass(long id, long ms) {
        return new TestOutcome(id, tagOf(id), true, false, null, "ok", ms);
    }

    private static TestOutcome wrong(long id) {
        return new TestOutcome(id, tagOf(id), false, false, null, "bad", 10);
    }

    private static TestOutcome crash(long id, String exception) {
        return new TestOutcome(id, tagOf(id), false, false, exception, "", 10);
    }

    private static TestOutcome timeout(long id) {
        return new TestOutcome(id, tagOf(id), false, true, null, "", 2000);
    }

    private static TestTag tagOf(long id) {
        return TESTS.stream().filter(t -> t.id() == id).findFirst().orElseThrow().tag();
    }

    /** All 7 pass except the replacements given. */
    private static List<TestOutcome> allPassExcept(TestOutcome... replacements) {
        List<TestOutcome> outcomes = new ArrayList<>();
        for (long id = 1; id <= 7; id++) {
            outcomes.add(pass(id, id == 7 ? 200 : 40));
        }
        for (TestOutcome r : replacements) {
            outcomes.set((int) (r.testCaseId() - 1), r);
        }
        return outcomes;
    }

    private static Evaluation eval(List<TestOutcome> outcomes, List<TestInfo> tests, Integer optimalMs, boolean dp) {
        return SubmissionEvaluator.evaluate(new RunResult(true, null, outcomes), tests, optimalMs, dp);
    }

    private static Evaluation eval(List<TestOutcome> outcomes) {
        return eval(outcomes, TESTS, null, false);
    }

    private static List<TestInfo> withOverride(long id, ErrorCategory override) {
        return TESTS.stream()
                .map(t -> t.id() == id ? new TestInfo(t.id(), t.tag(), t.ordinal(), override) : t)
                .toList();
    }

    @Test
    void step1CompileErrorIsSyntaxCompilationWithNoTests() {
        Evaluation ev = SubmissionEvaluator.evaluate(
                new RunResult(false, "Main.java:7: error: ';' expected", List.of()), TESTS, 250, false);
        assertEquals(Verdict.COMPILE_ERROR, ev.verdict());
        assertEquals(ErrorCategory.SYNTAX_COMPILATION, ev.category());
        assertEquals(0, ev.totalCount());
        assertNull(ev.flaggedRun());
        assertEquals("Main.java:7: error: ';' expected", ev.message());
    }

    @Test
    void step2TimeoutBeatsEverythingEvenOnSampleWithOverride() {
        Evaluation ev = eval(allPassExcept(timeout(1)), withOverride(1, ErrorCategory.MISSED_EDGE_CASE), null, true);
        assertEquals(Verdict.TIME_LIMIT_EXCEEDED, ev.verdict());
        assertEquals(ErrorCategory.BAD_COMPLEXITY, ev.category());
    }

    @Test
    void step3IndexExceptionBeatsOverrideAndTag() {
        Evaluation ev = eval(allPassExcept(crash(3, "ArrayIndexOutOfBoundsException")),
                withOverride(3, ErrorCategory.WRONG_ALGORITHM), null, false);
        assertEquals(Verdict.RUNTIME_ERROR, ev.verdict());
        assertEquals(ErrorCategory.OFF_BY_ONE, ev.category());
        assertEquals("ArrayIndexOutOfBoundsException", ev.message());

        assertEquals(ErrorCategory.OFF_BY_ONE,
                eval(allPassExcept(crash(1, "StringIndexOutOfBoundsException"))).category());
    }

    @Test
    void step4OverrideBeatsTagMapping() {
        Evaluation ev = eval(allPassExcept(wrong(5)), withOverride(5, ErrorCategory.MISSED_EDGE_CASE), null, false);
        assertEquals(ErrorCategory.MISSED_EDGE_CASE, ev.category());
    }

    @Test
    void step5TagMapping() {
        assertEquals(ErrorCategory.WRONG_ALGORITHM, eval(allPassExcept(wrong(2))).category());
        assertEquals(ErrorCategory.MISSED_EDGE_CASE, eval(allPassExcept(wrong(4))).category());
        assertEquals(ErrorCategory.OFF_BY_ONE, eval(allPassExcept(wrong(6))).category());
        assertEquals(ErrorCategory.BAD_COMPLEXITY, eval(allPassExcept(wrong(7))).category());
    }

    @Test
    void nonIndexExceptionAndNonZeroExitFallThroughToTagMapping() {
        Evaluation npe = eval(allPassExcept(crash(3, "NullPointerException")));
        assertEquals(Verdict.RUNTIME_ERROR, npe.verdict());
        assertEquals(ErrorCategory.MISSED_EDGE_CASE, npe.category());

        Evaluation exit = eval(allPassExcept(crash(5, "NonZeroExit")));
        assertEquals(Verdict.RUNTIME_ERROR, exit.verdict());
        assertEquals(ErrorCategory.OFF_BY_ONE, exit.category()); // BOUNDARY tag
        assertEquals("NonZeroExit", exit.message());
    }

    @Test
    void step6DpSampleFailureIsStateTransitionButOverrideStillWins() {
        assertEquals(ErrorCategory.STATE_TRANSITION, eval(allPassExcept(wrong(1)), TESTS, null, true).category());
        // An explicit override of WRONG_ALGORITHM is NOT rewritten by step 6.
        assertEquals(ErrorCategory.WRONG_ALGORITHM,
                eval(allPassExcept(wrong(1)), withOverride(1, ErrorCategory.WRONG_ALGORITHM), null, true).category());
        // Step 6 touches only WRONG_ALGORITHM: a DP EDGE failure stays MISSED_EDGE_CASE.
        assertEquals(ErrorCategory.MISSED_EDGE_CASE, eval(allPassExcept(wrong(3)), TESTS, null, true).category());
    }

    @Test
    void firstFailureFollowsRunOrderNotRunnerOrder() {
        // EDGE (id 3) and BOUNDARY (id 5) both fail; runner returns them reversed.
        List<TestOutcome> outcomes = new ArrayList<>(allPassExcept(wrong(3), crash(5, "ArrayIndexOutOfBoundsException")));
        java.util.Collections.reverse(outcomes);
        Evaluation ev = eval(outcomes);
        assertEquals(Verdict.WRONG_ANSWER, ev.verdict());
        assertEquals(ErrorCategory.MISSED_EDGE_CASE, ev.category());
        assertEquals(3L, ev.flaggedRun().test().id());
        assertEquals(5, ev.passedCount());
        assertEquals(1, ev.runs().get(0).test().ordinal()); // runs come back sorted
    }

    @Test
    void step7SuboptimalFlagsSlowestLargeTest() {
        List<TestInfo> twoLarge = new ArrayList<>(TESTS);
        twoLarge.add(new TestInfo(8L, TestTag.LARGE, 8, null));
        List<TestOutcome> outcomes = new ArrayList<>(allPassExcept(pass(7, 300)));
        outcomes.add(new TestOutcome(8L, TestTag.LARGE, true, false, null, "ok", 610));

        Evaluation ev = eval(outcomes, twoLarge, 250, false);
        assertEquals(Verdict.ACCEPTED, ev.verdict());
        assertEquals(ErrorCategory.SUBOPTIMAL, ev.category());
        assertEquals(610, ev.largeRuntimeMs());
        assertEquals(8L, ev.flaggedRun().test().id());
        assertEquals("All tests passed, but LARGE took 610 ms (optimal 250 ms).", ev.message());
    }

    @Test
    void step8CleanAcceptedHasNoCategory() {
        Evaluation fast = eval(allPassExcept(), TESTS, 250, false); // LARGE 200 ms <= 250
        assertEquals(Verdict.ACCEPTED, fast.verdict());
        assertNull(fast.category());
        assertNull(fast.flaggedRun());
        assertEquals(7, fast.passedCount());

        // No calibrated optimal runtime -> never SUBOPTIMAL, however slow.
        assertNull(eval(allPassExcept(pass(7, 99_999)), TESTS, null, false).category());
    }

    @Test
    void largeRuntimeIgnoresTimedOutLargeTests() {
        Evaluation ev = eval(allPassExcept(timeout(7)));
        assertNull(ev.largeRuntimeMs());
        assertEquals(ErrorCategory.BAD_COMPLEXITY, ev.category());
    }
}
