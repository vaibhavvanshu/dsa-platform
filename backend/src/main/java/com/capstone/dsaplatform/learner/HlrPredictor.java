package com.capstone.dsaplatform.learner;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Half-life regression inference (CONTRACT.md section 4.3). Training happens offline in
 * Python; Java only does a dot product, so the model stays explainable: every prediction
 * is 2^(theta . x) with 14 named features.
 *
 * Half-life is always computed LIVE from the loaded theta, so predictions match whatever
 * weights file is deployed. topic_state.half_life_days is a display copy and is never read here.
 */
@Component
public class HlrPredictor {

    /** Must match hlr_weights.json exactly; a mismatch means the weights were trained on other features. */
    public static final List<String> FEATURE_ORDER = List.of(
            "bias", "sqrt_correct", "sqrt_wrong", "mean_difficulty",
            "topic_arrays", "topic_strings", "topic_two_pointers", "topic_sliding_window",
            "topic_hashmap", "topic_linkedlist", "topic_stack", "topic_trees",
            "topic_graphs", "topic_dp");

    private static final Logger log = LoggerFactory.getLogger(HlrPredictor.class);
    private static final double MILLIS_PER_DAY = 86_400_000.0;

    private final HlrWeights weights;

    // Spring passes the file named by app.hlr.weights-path; a bad file throws, so the app refuses to start.
    @Autowired
    public HlrPredictor(@Value("${app.hlr.weights-path}") Resource weightsFile) throws IOException {
        this(parse(weightsFile.getInputStream()));
    }

    // Used directly by unit tests, which build weights in code instead of from a file.
    HlrPredictor(HlrWeights weights) {
        if (!FEATURE_ORDER.equals(weights.featureOrder())) {
            throw new IllegalStateException("hlr_weights.json feature_order must be exactly " + FEATURE_ORDER
                    + " (CONTRACT.md section 6) but was " + weights.featureOrder());
        }
        if (weights.theta() == null || weights.theta().length != FEATURE_ORDER.size()) {
            throw new IllegalStateException("hlr_weights.json theta must have " + FEATURE_ORDER.size() + " values but has "
                    + (weights.theta() == null ? "none" : weights.theta().length));
        }
        if (Boolean.TRUE.equals(weights.placeholder())) {
            // Loud on purpose: retention numbers are meaningless until the trained file replaces this one.
            log.warn("=================================================================");
            log.warn("HLR WEIGHTS ARE A PLACEHOLDER (\"placeholder\": true).");
            log.warn("Retention, unlocks and recommendations are NOT based on a trained model.");
            log.warn("Replace src/main/resources/ml/hlr_weights.json with the trained file.");
            log.warn("=================================================================");
        }
        this.weights = weights;
    }

    static HlrWeights parse(InputStream in) throws IOException {
        try (in) {
            return new ObjectMapper().readValue(in, HlrWeights.class);
        }
    }

    /** The 14-value feature vector, in FEATURE_ORDER. */
    static double[] features(int correctCount, int wrongCount, double meanDifficulty, long topicId) {
        if (topicId < 1 || topicId > 10) {
            throw new IllegalArgumentException("topicId must be 1..10 but was " + topicId);
        }
        double[] x = new double[FEATURE_ORDER.size()];
        x[0] = 1.0;
        // sqrt dampens counts: the 20th correct answer should matter less than the 2nd.
        x[1] = Math.sqrt(1 + correctCount);
        x[2] = Math.sqrt(1 + wrongCount);
        x[3] = meanDifficulty;
        // Topic id doubles as the one-hot position (topic 1 -> index 4, topic 10 -> index 13).
        x[3 + (int) topicId] = 1.0;
        return x;
    }

    /** h = clamp(2^(theta . x), min_h, max_h), in days. */
    public double halfLifeDays(int correctCount, int wrongCount, double meanDifficulty, long topicId) {
        double[] x = features(correctCount, wrongCount, meanDifficulty, topicId);
        double dot = 0;
        for (int i = 0; i < x.length; i++) {
            dot += weights.theta()[i] * x[i];
        }
        double h = Math.pow(2, dot);
        return Math.max(weights.minH(), Math.min(weights.maxH(), h));
    }

    /** p = 2^(-delta/h): exactly 0.5 after one half-life. */
    public static double recall(double halfLifeDays, double deltaDays) {
        return Math.pow(2, -deltaDays / halfLifeDays);
    }

    /** Fractional days; never negative, so a clock skew cannot produce retention above 1. */
    public static double daysBetween(LocalDateTime from, LocalDateTime to) {
        return Math.max(0, Duration.between(from, to).toMillis() / MILLIS_PER_DAY);
    }

    public boolean isPlaceholder() {
        return Boolean.TRUE.equals(weights.placeholder());
    }
}
