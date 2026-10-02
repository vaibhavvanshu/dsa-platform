package com.capstone.dsaplatform.learner;

import com.capstone.dsaplatform.domain.Difficulty;
import com.capstone.dsaplatform.learner.RecommendationScorer.Candidate;
import com.capstone.dsaplatform.learner.RecommendationScorer.Scored;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationScorerTest {

    private static final double EPS = 1e-9;

    private static Candidate candidate(long id, Difficulty d, boolean unlocked, int correct, int wrong,
                                       Double retention, boolean accepted) {
        return new Candidate(id, "P" + id, 3L, "Two Pointers", d, unlocked, correct, wrong, retention, accepted,
                List.of("Sliding Window"));
    }

    @Test
    void termsMatchDefinitions() {
        assertEquals(0.5, RecommendationScorer.weakness(0, 0), EPS);
        assertEquals(5.0 / 8, RecommendationScorer.weakness(2, 4), EPS);
        assertEquals(0.59, RecommendationScorer.urgency(0.41), EPS);
        assertEquals(0.5, RecommendationScorer.urgency(null), EPS);

        assertEquals(Difficulty.EASY, RecommendationScorer.targetDifficulty(1));
        assertEquals(Difficulty.MEDIUM, RecommendationScorer.targetDifficulty(2));
        assertEquals(Difficulty.MEDIUM, RecommendationScorer.targetDifficulty(4));
        assertEquals(Difficulty.HARD, RecommendationScorer.targetDifficulty(5));

        assertEquals(1.0, RecommendationScorer.difficultyFit(Difficulty.MEDIUM, Difficulty.MEDIUM), EPS);
        assertEquals(0.5, RecommendationScorer.difficultyFit(Difficulty.EASY, Difficulty.MEDIUM), EPS);
        assertEquals(0.0, RecommendationScorer.difficultyFit(Difficulty.HARD, Difficulty.EASY), EPS);
    }

    @Test
    void scoreIsWeightedSum() {
        // correct 2, wrong 4 -> weakness 0.625; retention 0.41 -> urgency 0.59; target MEDIUM, EASY -> fit 0.5
        Candidate c = candidate(1, Difficulty.EASY, true, 2, 4, 0.41, false);
        assertEquals(0.4 * 0.625 + 0.4 * 0.59 + 0.2 * 0.5, RecommendationScorer.score(c), EPS);
    }

    @Test
    void lockedTopicsAreNeverReturned() {
        List<Scored> ranked = RecommendationScorer.rank(List.of(
                candidate(1, Difficulty.EASY, false, 0, 9, 0.1, false),
                candidate(2, Difficulty.EASY, true, 5, 0, 0.9, false)), 0.6, 5);
        assertEquals(1, ranked.size());
        assertEquals(2L, ranked.get(0).candidate().problemId());
    }

    @Test
    void acceptedProblemsSkippedUnlessRetentionBelowThreshold() {
        List<Scored> ranked = RecommendationScorer.rank(List.of(
                candidate(1, Difficulty.EASY, true, 3, 1, 0.7, true),   // solved, still remembered -> skip
                candidate(2, Difficulty.EASY, true, 3, 1, 0.59, true),  // solved, faded -> keep
                candidate(3, Difficulty.EASY, true, 3, 1, 0.6, true)),  // exactly 0.6 is not "below" -> skip
                0.6, 5);
        assertEquals(List.of(2L), ranked.stream().map(s -> s.candidate().problemId()).toList());
        assertTrue(ranked.get(0).reason().contains("solved this before"));
    }

    @Test
    void returnsTopFiveBestFirstWithTiesByProblemId() {
        List<Candidate> cs = new ArrayList<>();
        for (long id = 1; id <= 8; id++) {
            cs.add(candidate(id, Difficulty.EASY, true, 0, (int) id % 3, 0.5, false));
        }
        List<Scored> ranked = RecommendationScorer.rank(cs, 0.6, 5);
        assertEquals(5, ranked.size());
        for (int i = 1; i < ranked.size(); i++) {
            Scored prev = ranked.get(i - 1);
            Scored cur = ranked.get(i);
            assertTrue(prev.score() > cur.score()
                    || (prev.score() == cur.score() && prev.candidate().problemId() < cur.candidate().problemId()));
        }
        // wrong = id % 3 is highest (2) for ids 2, 5, 8 -> those come first, in id order.
        assertEquals(List.of(2L, 5L, 8L), ranked.subList(0, 3).stream().map(s -> s.candidate().problemId()).toList());
    }

    @Test
    void reasonIsBuiltFromTheNumbers() {
        String reason = RecommendationScorer.reason(candidate(1, Difficulty.EASY, true, 2, 4, 0.41, false), 0.6);
        assertEquals("Two Pointers retention is 0.41 with 2 correct and 4 wrong. "
                + "Your target level here is MEDIUM; this one is EASY. It gates Sliding Window.", reason);

        Candidate fresh = new Candidate(9L, "Reverse a Linked List", 6L, "LinkedList", Difficulty.EASY, true,
                0, 0, null, false, List.of("Trees"));
        assertEquals("LinkedList is unlocked and not yet attempted. EASY matches your current level in this topic. "
                + "It gates Trees.", RecommendationScorer.reason(fresh, 0.6));
    }
}
