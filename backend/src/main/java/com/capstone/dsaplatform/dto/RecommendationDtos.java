package com.capstone.dsaplatform.dto;

import com.capstone.dsaplatform.domain.Difficulty;

import java.time.Instant;
import java.util.List;

/** GET /api/users/{id}/recommendations (CONTRACT.md section 5.4). */
public final class RecommendationDtos {

    private RecommendationDtos() {
    }

    public record RecommendationResponse(Long userId, Instant generatedAt, List<RecommendationItem> items) {
    }

    public record RecommendationItem(Long problemId,
                                     String title,
                                     Long topicId,
                                     String topicName,
                                     Difficulty difficulty,
                                     double score,
                                     String reason) {
    }
}
