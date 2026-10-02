package com.capstone.dsaplatform.dto;

import java.time.Instant;
import java.util.List;

/** GET /api/users/{id}/retention (CONTRACT.md section 5.3). */
public final class RetentionDtos {

    private RetentionDtos() {
    }

    public record RetentionResponse(Long userId, Instant computedAt, List<TopicRetentionView> topics) {
    }

    public record TopicRetentionView(Long topicId,
                                     String name,
                                     boolean attempted,
                                     int correctCount,
                                     int wrongCount,
                                     Double meanDifficulty,
                                     Instant lastPracticedAt,
                                     Double daysSinceLastPractice,
                                     Double halfLifeDays,
                                     Double predictedRetention) {
    }
}
