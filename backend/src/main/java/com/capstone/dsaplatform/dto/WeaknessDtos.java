package com.capstone.dsaplatform.dto;

import com.capstone.dsaplatform.domain.ErrorCategory;

import java.time.Instant;
import java.util.List;

/** GET /api/users/{id}/weakness (CONTRACT.md section 5.5). */
public final class WeaknessDtos {

    private WeaknessDtos() {
    }

    public record WeaknessResponse(Long userId, int totalErrorEvents, List<CategoryView> categories) {
    }

    public record CategoryView(ErrorCategory category,
                               int count,
                               double share,
                               Instant lastSeenAt,
                               List<TopicCountView> topics,
                               List<ResourceLink> resources) {
    }

    public record TopicCountView(Long topicId, String name, int count) {
    }
}
