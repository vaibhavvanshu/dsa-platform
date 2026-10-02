package com.capstone.dsaplatform.learner;

import com.capstone.dsaplatform.domain.Verdict;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Pure readiness math (CONTRACT.md section 4.4). Kept free of Spring and JPA so each
 * formula can be unit-tested and read on its own during the viva.
 */
public final class ReadinessCalculator {

    private static final int CONSISTENCY_WINDOW_DAYS = 14;

    private ReadinessCalculator() {
    }

    /** Just the fields of one recent attempt that readiness needs. */
    public record RecentAttempt(Verdict verdict, Integer timeSpentSeconds, int targetSolveSeconds) {
    }

    /** readiness = 100 * (0.35*acc + 0.30*retention + 0.20*consistency + 0.15*speed). */
    public static double topicReadiness(double recentAccuracy, double retention, double consistency, double speedVsTarget) {
        return 100 * (0.35 * recentAccuracy + 0.30 * retention + 0.20 * consistency + 0.15 * speedVsTarget);
    }

    /** ACCEPTED / attempts over the recent window. */
    public static double recentAccuracy(List<RecentAttempt> recent) {
        if (recent.isEmpty()) {
            return 0;
        }
        long accepted = recent.stream().filter(a -> a.verdict() == Verdict.ACCEPTED).count();
        return (double) accepted / recent.size();
    }

    /**
     * Mean of min(1, target / spent) over ACCEPTED attempts that have a timer value.
     * Capped at 1 so solving very fast cannot hide poor accuracy elsewhere.
     */
    public static double speedVsTarget(List<RecentAttempt> recent) {
        return recent.stream()
                .filter(a -> a.verdict() == Verdict.ACCEPTED)
                // NULL = client sent no timer; 0 would divide by zero. Neither is evidence of speed.
                .filter(a -> a.timeSpentSeconds() != null && a.timeSpentSeconds() > 0)
                .mapToDouble(a -> Math.min(1.0, (double) a.targetSolveSeconds() / a.timeSpentSeconds()))
                .average()
                .orElse(0);
    }

    /**
     * Distinct UTC calendar days with any attempt, among today and the 13 days before it, / 14.
     * Calendar days (not a rolling 14x24h window) so the value can never exceed 1.
     */
    public static double consistency(Collection<LocalDateTime> attemptTimesUtc, LocalDateTime nowUtc) {
        LocalDate today = nowUtc.toLocalDate();
        LocalDate firstDay = today.minusDays(CONSISTENCY_WINDOW_DAYS - 1);
        long activeDays = attemptTimesUtc.stream()
                .map(LocalDateTime::toLocalDate)
                .filter(d -> !d.isBefore(firstDay) && !d.isAfter(today))
                .distinct()
                .count();
        return (double) activeDays / CONSISTENCY_WINDOW_DAYS;
    }

    /** Mean of the non-null values, or null when there are none (e.g. no topic attempted). */
    public static Double meanOfPresent(Collection<? extends Number> values) {
        List<Double> present = values.stream().filter(Objects::nonNull).map(Number::doubleValue).toList();
        return present.isEmpty() ? null : present.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    }

    /** self_rating*20 - computed; positive = the learner rates themselves above the evidence. */
    public static Double gap(Double selfRating1to5, Double computed0to100) {
        if (selfRating1to5 == null || computed0to100 == null) {
            return null;
        }
        return selfRating1to5 * 20 - computed0to100;
    }
}
