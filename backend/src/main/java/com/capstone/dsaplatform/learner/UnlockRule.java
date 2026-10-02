package com.capstone.dsaplatform.learner;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

/**
 * The hard prerequisite gate (CONTRACT.md section 2.3). Evaluated live on every request,
 * so a topic re-locks when a prerequisite's retention decays: the gate tracks current
 * mastery, not past achievement.
 */
@Component
public class UnlockRule {

    private final double minRetention;
    private final int minCorrectCount;

    public UnlockRule(@Value("${app.unlock.min-retention}") double minRetention,
                      @Value("${app.unlock.min-correct-count}") int minCorrectCount) {
        this.minRetention = minRetention;
        this.minCorrectCount = minCorrectCount;
    }

    /**
     * @param retentionByTopic predicted retention per topic id; missing or null means "never attempted"
     * @param correctCountByTopic correct_count per topic id; missing means no topic_state row
     */
    public boolean isUnlocked(Collection<Long> prerequisiteIds,
                              Map<Long, Double> retentionByTopic,
                              Map<Long, Integer> correctCountByTopic) {
        // No prerequisites (a root topic) -> the loop never runs -> unlocked.
        for (Long prerequisiteId : prerequisiteIds) {
            Double retention = retentionByTopic.get(prerequisiteId);
            int correct = correctCountByTopic.getOrDefault(prerequisiteId, 0);
            // A prerequisite with no evidence fails both checks, so it can never unlock anything.
            if (retention == null || retention < minRetention || correct < minCorrectCount) {
                return false;
            }
        }
        return true;
    }

    public double getMinRetention() {
        return minRetention;
    }

    public int getMinCorrectCount() {
        return minCorrectCount;
    }
}
