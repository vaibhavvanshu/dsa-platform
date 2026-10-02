package com.capstone.dsaplatform.dto;

import java.util.List;

/** GET /api/topics/graph?userId= (CONTRACT.md section 5.8). */
public final class TopicGraphDtos {

    private TopicGraphDtos() {
    }

    public record TopicGraphResponse(Long userId, Thresholds thresholds, List<NodeView> nodes, List<EdgeView> edges) {
    }

    public record Thresholds(double minRetention, int minCorrectCount) {
    }

    public record NodeView(Long id,
                           String name,
                           boolean unlocked,
                           boolean attempted,
                           Double predictedRetention,
                           int correctCount) {
    }

    // from = prerequisite, to = gated topic.
    public record EdgeView(Long from, Long to) {
    }
}
