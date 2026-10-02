package com.capstone.dsaplatform.learner;

import com.capstone.dsaplatform.domain.Difficulty;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Pure recommendation scoring. Every term is a 0..1 number with a one-line meaning,
 * so a recommendation can be explained from its reason string alone:
 *
 *   weakness      = (1 + wrong) / (2 + correct + wrong)    smoothed error rate; 0.5 with no data
 *   urgency       = 1 - predicted_retention (0.5 if never attempted)
 *   difficultyFit = 1 - |difficulty - target| / 2
 *   score         = 0.4*weakness + 0.4*urgency + 0.2*difficultyFit
 */
public final class RecommendationScorer {

    // Neutral urgency for an unattempted topic: lets new topics appear without outranking a fading weak one.
    static final double UNKNOWN_URGENCY = 0.5;

    private RecommendationScorer() {
    }

    /** Everything needed to score one problem for one learner. */
    public record Candidate(Long problemId,
                            String title,
                            Long topicId,
                            String topicName,
                            Difficulty difficulty,
                            boolean topicUnlocked,
                            int correctCount,
                            int wrongCount,
                            Double retention,
                            boolean alreadyAccepted,
                            List<String> gatedTopicNames) {
    }

    public record Scored(Candidate candidate, double score, String reason) {
    }

    /** Filters, scores and returns the top `limit`, best first; ties go to the lower problem id. */
    public static List<Scored> rank(List<Candidate> candidates, double minRetention, int limit) {
        return candidates.stream()
                .filter(Candidate::topicUnlocked)
                // A solved problem only comes back once its topic has faded below the unlock threshold.
                .filter(c -> !c.alreadyAccepted() || (c.retention() != null && c.retention() < minRetention))
                .map(c -> new Scored(c, score(c), reason(c, minRetention)))
                .sorted(Comparator.comparingDouble(Scored::score).reversed()
                        .thenComparing(s -> s.candidate().problemId()))
                .limit(limit)
                .toList();
    }

    static double score(Candidate c) {
        Difficulty target = targetDifficulty(c.correctCount());
        return 0.4 * weakness(c.correctCount(), c.wrongCount())
                + 0.4 * urgency(c.retention())
                + 0.2 * difficultyFit(c.difficulty(), target);
    }

    static double weakness(int correct, int wrong) {
        return (1.0 + wrong) / (2.0 + correct + wrong);
    }

    static double urgency(Double retention) {
        return retention == null ? UNKNOWN_URGENCY : 1 - retention;
    }

    /** Step up difficulty only after enough correct answers in the topic. */
    static Difficulty targetDifficulty(int correct) {
        if (correct < 2) {
            return Difficulty.EASY;
        }
        return correct < 5 ? Difficulty.MEDIUM : Difficulty.HARD;
    }

    /** 1 on target, 0.5 one level away, 0 two levels away. */
    static double difficultyFit(Difficulty problem, Difficulty target) {
        return 1 - Math.abs(problem.getWeight() - target.getWeight()) / 2.0;
    }

    static String reason(Candidate c, double minRetention) {
        StringBuilder sb = new StringBuilder();
        if (c.retention() == null) {
            sb.append(c.topicName()).append(" is unlocked and not yet attempted.");
        } else {
            sb.append(String.format(Locale.ROOT, "%s retention is %.2f with %d correct and %d wrong.",
                    c.topicName(), c.retention(), c.correctCount(), c.wrongCount()));
        }
        if (c.alreadyAccepted()) {
            sb.append(" You solved this before, but retention is below ").append(minRetention).append(", so it is worth redoing.");
        }
        Difficulty target = targetDifficulty(c.correctCount());
        if (c.difficulty() == target) {
            sb.append(' ').append(c.difficulty()).append(" matches your current level in this topic.");
        } else {
            sb.append(" Your target level here is ").append(target).append("; this one is ").append(c.difficulty()).append('.');
        }
        if (!c.gatedTopicNames().isEmpty()) {
            sb.append(" It gates ").append(joinNames(c.gatedTopicNames())).append('.');
        }
        return sb.toString();
    }

    // "A", "A and B", "A, B and C"
    private static String joinNames(List<String> names) {
        if (names.size() == 1) {
            return names.get(0);
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
    }
}
