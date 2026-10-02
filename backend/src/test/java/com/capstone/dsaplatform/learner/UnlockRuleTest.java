package com.capstone.dsaplatform.learner;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CONTRACT.md section 2.3, with the default thresholds 0.6 and 2. */
class UnlockRuleTest {

    private final UnlockRule rule = new UnlockRule(0.6, 2);

    @Test
    void rootTopicIsAlwaysUnlocked() {
        assertTrue(rule.isUnlocked(List.of(), Map.of(), Map.of()));
    }

    @Test
    void unlockedWhenEveryPrerequisiteMeetsBothThresholds() {
        // HashMap (5) needs Arrays (1) and Strings (2).
        assertTrue(rule.isUnlocked(List.of(1L, 2L), Map.of(1L, 0.72, 2L, 0.61), Map.of(1L, 5, 2L, 3)));
    }

    @Test
    void thresholdsAreInclusive() {
        assertTrue(rule.isUnlocked(List.of(1L), Map.of(1L, 0.6), Map.of(1L, 2)));
    }

    @Test
    void oneFadedPrerequisiteLocksTheTopic() {
        // The contract example: Strings retention 0.55 locks HashMap even though Arrays is fine.
        assertFalse(rule.isUnlocked(List.of(1L, 2L), Map.of(1L, 0.72, 2L, 0.55), Map.of(1L, 5, 2L, 3)));
    }

    @Test
    void tooFewCorrectAnswersLocksEvenWithHighRetention() {
        assertFalse(rule.isUnlocked(List.of(1L), Map.of(1L, 0.95), Map.of(1L, 1)));
    }

    @Test
    void prerequisiteWithoutTopicStateFailsBothChecks() {
        // LinkedList (6) never attempted -> Trees (8) locked even if Stack (7) is mastered.
        assertFalse(rule.isUnlocked(List.of(6L, 7L), Map.of(7L, 0.9), Map.of(7L, 4)));
    }

    @Test
    void stateRowFromSelfRatingOnlyHasNoRetentionAndFails() {
        java.util.Map<Long, Double> retention = new java.util.HashMap<>();
        retention.put(1L, null); // row exists, zero attempts
        assertFalse(rule.isUnlocked(List.of(1L), retention, Map.of(1L, 0)));
    }
}
