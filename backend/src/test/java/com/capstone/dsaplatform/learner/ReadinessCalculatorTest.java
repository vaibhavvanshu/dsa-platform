package com.capstone.dsaplatform.learner;

import com.capstone.dsaplatform.domain.Verdict;
import com.capstone.dsaplatform.learner.ReadinessCalculator.RecentAttempt;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** CONTRACT.md section 4.4. */
class ReadinessCalculatorTest {

    private static final double EPS = 1e-9;

    @Test
    void topicReadinessFormulaMatchesContractExample() {
        // Arrays in CONTRACT 5.6: 0.8, 0.72, 0.5, 0.9 -> 73.1
        assertEquals(73.1, ReadinessCalculator.topicReadiness(0.8, 0.72, 0.5, 0.9), EPS);
        assertEquals(100.0, ReadinessCalculator.topicReadiness(1, 1, 1, 1), EPS);
        assertEquals(35.0, ReadinessCalculator.topicReadiness(1, 0, 0, 0), EPS);
        assertEquals(30.0, ReadinessCalculator.topicReadiness(0, 1, 0, 0), EPS);
        assertEquals(20.0, ReadinessCalculator.topicReadiness(0, 0, 1, 0), EPS);
        assertEquals(15.0, ReadinessCalculator.topicReadiness(0, 0, 0, 1), EPS);
    }

    @Test
    void recentAccuracyIsAcceptedShare() {
        List<RecentAttempt> recent = List.of(
                new RecentAttempt(Verdict.ACCEPTED, 100, 900),
                new RecentAttempt(Verdict.WRONG_ANSWER, 100, 900),
                new RecentAttempt(Verdict.COMPILE_ERROR, null, 900),
                new RecentAttempt(Verdict.ACCEPTED, 100, 900));
        assertEquals(0.5, ReadinessCalculator.recentAccuracy(recent), EPS);
    }

    @Test
    void speedVsTargetUsesAcceptedWithTimerAndCapsAtOne() {
        List<RecentAttempt> recent = List.of(
                new RecentAttempt(Verdict.ACCEPTED, 1800, 900),     // 0.5
                new RecentAttempt(Verdict.ACCEPTED, 300, 900),      // 3.0 capped to 1.0
                new RecentAttempt(Verdict.ACCEPTED, null, 900),     // ignored: no timer
                new RecentAttempt(Verdict.ACCEPTED, 0, 900),        // ignored: zero
                new RecentAttempt(Verdict.WRONG_ANSWER, 100, 900)); // ignored: not accepted
        assertEquals(0.75, ReadinessCalculator.speedVsTarget(recent), EPS);
        assertEquals(0.0, ReadinessCalculator.speedVsTarget(List.of(new RecentAttempt(Verdict.WRONG_ANSWER, 10, 900))), EPS);
    }

    @Test
    void consistencyCountsDistinctUtcDaysInTodayPlusThirteen() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 2, 9, 15);
        List<LocalDateTime> times = List.of(
                LocalDateTime.of(2026, 10, 2, 8, 0),   // today
                LocalDateTime.of(2026, 10, 2, 1, 0),   // today again (same day counts once)
                LocalDateTime.of(2026, 9, 30, 23, 59),
                LocalDateTime.of(2026, 9, 19, 0, 0),   // oldest day in the window (today - 13)
                LocalDateTime.of(2026, 9, 18, 23, 59)); // outside
        assertEquals(3 / 14.0, ReadinessCalculator.consistency(times, now), EPS);
    }

    @Test
    void overallValuesAndGap() {
        assertEquals(60.0, ReadinessCalculator.meanOfPresent(Arrays.asList(73.1, null, 46.9)), EPS);
        assertNull(ReadinessCalculator.meanOfPresent(Arrays.asList((Double) null, null)));
        assertEquals(3.5, ReadinessCalculator.meanOfPresent(Arrays.asList(4, null, 3)), EPS);

        assertEquals(10.0, ReadinessCalculator.gap(3.5, 60.0), EPS); // CONTRACT 5.6 overall example
        assertNull(ReadinessCalculator.gap(null, 60.0));
        assertNull(ReadinessCalculator.gap(3.5, null));
    }
}
