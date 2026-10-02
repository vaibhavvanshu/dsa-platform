package com.capstone.dsaplatform.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.capstone.dsaplatform.domain.TestTag;
import com.capstone.dsaplatform.entity.Problem;
import com.capstone.dsaplatform.entity.TestCase;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Runs real javac and real child JVMs, but no Spring context and no DB: the entities have
 * protected constructors and no setters, so Mockito stands in for them and for the repository.
 * Requires a JDK, exactly like the runner itself.
 */
class JavaProcessCodeRunnerTest {

    private static final long PROBLEM_ID = 1L;

    // Reads n, then n integers, and prints their sum: small enough to read in a viva.
    private static final String SUM_SOLUTION = """
            import java.util.*;

            public class Main {
                public static void main(String[] args) {
                    Scanner sc = new Scanner(System.in);
                    int n = sc.nextInt();
                    long sum = 0;
                    for (int i = 0; i < n; i++) sum += sc.nextInt();
                    System.out.println(sum);
                }
            }
            """;

    @Test
    void correctSolutionPassesEveryTestInTagOrder() {
        // Deliberately returned out of order, to prove the runner sorts by tag and not by DB order.
        RunResult result = runner(
                test(30L, TestTag.LARGE, 1, "4\n1 2 3 4\n", "10\n"),
                test(10L, TestTag.SAMPLE, 1, "3\n1 2 3\n", "6\n"),
                // Trailing spaces and blank lines in the expected output must be ignored (CONTRACT 4.1).
                test(20L, TestTag.EDGE, 1, "1\n-5\n", "-5   \n\n\n")
        ).run(problem(2000), SUM_SOLUTION);

        assertTrue(result.compiled());
        assertNull(result.compileError());
        assertEquals(List.of(TestTag.SAMPLE, TestTag.EDGE, TestTag.LARGE),
                result.outcomes().stream().map(TestOutcome::tag).toList());
        assertEquals(List.of(10L, 20L, 30L),
                result.outcomes().stream().map(TestOutcome::testCaseId).toList());
        for (TestOutcome o : result.outcomes()) {
            assertTrue(o.passed(), "expected pass for test " + o.testCaseId());
            assertFalse(o.timedOut());
            assertNull(o.exceptionType());
            assertTrue(o.runtimeMs() > 0);
        }
    }

    @Test
    void compileErrorIsReportedWithLineNumberAndNoTestsRun() {
        String missingSemicolon = """
                public class Main {
                    public static void main(String[] args) {
                        int x = 5
                        System.out.println(x);
                    }
                }
                """;

        RunResult result = runner(test(10L, TestTag.SAMPLE, 1, "", "5\n"))
                .run(problem(2000), missingSemicolon);

        assertFalse(result.compiled());
        assertTrue(result.compileError().startsWith("Main.java:3: error:"), result.compileError());
        assertTrue(result.compileError().contains("';' expected"), result.compileError());
        assertTrue(result.outcomes().isEmpty());
    }

    @Test
    void infiniteLoopIsKilledAtTheTimeLimit() {
        String infiniteLoop = """
                public class Main {
                    public static void main(String[] args) {
                        while (true) { }
                    }
                }
                """;

        RunResult result = runner(test(10L, TestTag.LARGE, 1, "", "0\n"))
                .run(problem(1000), infiniteLoop);

        TestOutcome o = result.outcomes().get(0);
        assertTrue(o.timedOut());
        assertFalse(o.passed());
        assertNull(o.exceptionType());
        assertTrue(o.runtimeMs() >= 1000, "runtime was " + o.runtimeMs());
        assertTrue(o.runtimeMs() < 3000, "process was not killed promptly: " + o.runtimeMs());
    }

    @Test
    void arrayIndexOutOfBoundsIsReportedBySimpleClassName() {
        // Classic off-by-one: i <= n reads one element past the end.
        String offByOne = """
                public class Main {
                    public static void main(String[] args) {
                        int[] a = new int[3];
                        int sum = 0;
                        for (int i = 0; i <= a.length; i++) sum += a[i];
                        System.out.println(sum);
                    }
                }
                """;

        RunResult result = runner(test(10L, TestTag.BOUNDARY, 1, "", "0\n"))
                .run(problem(2000), offByOne);

        TestOutcome o = result.outcomes().get(0);
        assertFalse(o.passed());
        assertFalse(o.timedOut());
        assertEquals("ArrayIndexOutOfBoundsException", o.exceptionType());
    }

    @Test
    void wrongOutputFailsThatTestButLaterTestsStillRun() {
        // Always prints 42: wrong for the first test, right for the second.
        String alwaysFortyTwo = """
                public class Main {
                    public static void main(String[] args) {
                        System.out.println(42);
                    }
                }
                """;

        RunResult result = runner(
                test(10L, TestTag.SAMPLE, 1, "3\n1 2 3\n", "6\n"),
                test(20L, TestTag.EDGE, 1, "1\n42\n", "42\n")
        ).run(problem(2000), alwaysFortyTwo);

        assertEquals(2, result.outcomes().size());
        TestOutcome wrong = result.outcomes().get(0);
        assertFalse(wrong.passed());
        assertFalse(wrong.timedOut());
        assertNull(wrong.exceptionType());
        assertEquals("42", wrong.actualOutput().strip());
        assertTrue(result.outcomes().get(1).passed());
    }

    @Test
    void nonZeroExitWithoutExceptionIsReportedAsNonZeroExit() {
        String exitsWithThree = """
                public class Main {
                    public static void main(String[] args) {
                        System.exit(3);
                    }
                }
                """;

        RunResult result = runner(test(10L, TestTag.SAMPLE, 1, "", ""))
                .run(problem(2000), exitsWithThree);

        TestOutcome o = result.outcomes().get(0);
        assertFalse(o.passed());
        assertEquals("NonZeroExit", o.exceptionType());
    }

    // ---- helpers ----

    private static JavaProcessCodeRunner runner(TestCase... tests) {
        RunnerTestCaseRepository repo = mock(RunnerTestCaseRepository.class);
        when(repo.findByProblemId(PROBLEM_ID)).thenReturn(List.of(tests));
        return new JavaProcessCodeRunner(repo);
    }

    private static Problem problem(int timeLimitMs) {
        Problem p = mock(Problem.class);
        when(p.getId()).thenReturn(PROBLEM_ID);
        when(p.getTimeLimitMs()).thenReturn(timeLimitMs);
        return p;
    }

    private static TestCase test(Long id, TestTag tag, int ordinal, String input, String expected) {
        TestCase t = mock(TestCase.class);
        when(t.getId()).thenReturn(id);
        when(t.getTag()).thenReturn(tag);
        when(t.getOrdinal()).thenReturn(ordinal);
        when(t.getInput()).thenReturn(input);
        when(t.getExpectedOutput()).thenReturn(expected);
        return t;
    }
}
