package com.capstone.dsaplatform.dto;

import java.time.Instant;
import java.util.List;

/** Readiness and self-rating endpoints (CONTRACT.md sections 5.6 - 5.7). */
public final class ReadinessDtos {

    private ReadinessDtos() {
    }

    public record ReadinessResponse(Long userId,
                                    Instant computedAt,
                                    OverallView overall,
                                    double consistency,
                                    List<TopicReadinessView> topics,
                                    List<HistoryPoint> history) {
    }

    public record OverallView(Double computed,
                              Double selfRating,
                              Double selfRatingScaled,
                              Double gap,
                              double evidenceCoverage) {
    }

    // components, readiness and gap are null for an unattempted topic.
    public record TopicReadinessView(Long topicId,
                                     String name,
                                     boolean attempted,
                                     Double readiness,
                                     ComponentsView components,
                                     Integer selfRating,
                                     Double gap) {
    }

    public record ComponentsView(double recentAccuracy, double retention, double consistency, double speedVsTarget) {
    }

    public record HistoryPoint(Instant takenAt, Double overallSelfRating, Double overallComputed, Double gap) {
    }

    public record SelfRatingRequest(Long topicId, Integer rating) {
    }

    public record SelfRatingResponse(Long userId,
                                     Long topicId,
                                     int selfRating,
                                     Double overallSelfRating,
                                     SnapshotView snapshot) {
    }

    public record SnapshotView(Long id, Instant takenAt, Double overallSelfRating, Double overallComputed, Double gap) {
    }
}
