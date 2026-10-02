package com.capstone.dsaplatform.learner;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the Java/Python feature-order agreement (CONTRACT.md sections 4.3 and 6). */
class HlrPredictorTest {

    private static final List<String> CONTRACT_ORDER = List.of(
            "bias", "sqrt_correct", "sqrt_wrong", "mean_difficulty",
            "topic_arrays", "topic_strings", "topic_two_pointers", "topic_sliding_window",
            "topic_hashmap", "topic_linkedlist", "topic_stack", "topic_trees",
            "topic_graphs", "topic_dp");

    private static HlrWeights weights(List<String> order, double[] theta) {
        return new HlrWeights(order, theta, 0.25, 180.0, null);
    }

    private static double[] zeros() {
        return new double[14];
    }

    @Test
    void featureOrderConstantMatchesContract() {
        assertEquals(CONTRACT_ORDER, HlrPredictor.FEATURE_ORDER);
    }

    @Test
    void featureVectorHasContractValuesInContractPositions() {
        // correct=3, wrong=8, meanDifficulty=1.5, topic 3 (Two Pointers)
        double[] x = HlrPredictor.features(3, 8, 1.5, 3);
        double[] expected = {1.0, 2.0, 3.0, 1.5, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0};
        assertArrayEquals(expected, x, 1e-12);
    }

    @Test
    void oneHotIndexIsThreePlusTopicIdForEveryTopic() {
        for (long topicId = 1; topicId <= 10; topicId++) {
            double[] x = HlrPredictor.features(0, 0, 1.0, topicId);
            for (int i = 4; i < 14; i++) {
                assertEquals(i == 3 + topicId ? 1.0 : 0.0, x[i], "topic " + topicId + ", index " + i);
            }
            // Name check: topic 1 -> topic_arrays, topic 10 -> topic_dp.
            assertTrue(HlrPredictor.FEATURE_ORDER.get((int) (3 + topicId)).startsWith("topic_"));
        }
        assertEquals("topic_arrays", HlrPredictor.FEATURE_ORDER.get(3 + 1));
        assertEquals("topic_dp", HlrPredictor.FEATURE_ORDER.get(3 + 10));
    }

    @Test
    void eachThetaWeightMultipliesItsNamedFeature() {
        // theta only on sqrt_wrong: h = 2^(theta[2] * sqrt(1 + wrong)) = 2^(1 * 3) = 8 days.
        double[] theta = zeros();
        theta[2] = 1.0;
        HlrPredictor predictor = new HlrPredictor(weights(CONTRACT_ORDER, theta));
        assertEquals(8.0, predictor.halfLifeDays(0, 8, 2.0, 5), 1e-9);

        // theta only on topic_dp: active for topic 10 (h = 2), inactive for topic 9 (h = 2^0 = 1).
        double[] dpTheta = zeros();
        dpTheta[13] = 1.0;
        HlrPredictor dp = new HlrPredictor(weights(CONTRACT_ORDER, dpTheta));
        assertEquals(2.0, dp.halfLifeDays(0, 0, 1.0, 10), 1e-9);
        assertEquals(1.0, dp.halfLifeDays(0, 0, 1.0, 9), 1e-9);
    }

    @Test
    void halfLifeIsClampedAndRecallHalvesPerHalfLife() {
        double[] big = zeros();
        big[0] = 50; // 2^50 days, far above max_h
        assertEquals(180.0, new HlrPredictor(weights(CONTRACT_ORDER, big)).halfLifeDays(0, 0, 1, 1), 1e-9);
        double[] small = zeros();
        small[0] = -50;
        assertEquals(0.25, new HlrPredictor(weights(CONTRACT_ORDER, small)).halfLifeDays(0, 0, 1, 1), 1e-9);

        assertEquals(0.5, HlrPredictor.recall(4.0, 4.0), 1e-12);
        assertEquals(0.25, HlrPredictor.recall(4.0, 8.0), 1e-12);
        assertEquals(1.0, HlrPredictor.recall(4.0, 0.0), 1e-12);
    }

    @Test
    void refusesReorderedFeatureOrder() {
        List<String> swapped = new ArrayList<>(CONTRACT_ORDER);
        swapped.set(1, "sqrt_wrong");
        swapped.set(2, "sqrt_correct");
        assertThrows(IllegalStateException.class, () -> new HlrPredictor(weights(swapped, zeros())));
    }

    @Test
    void refusesWrongThetaLength() {
        assertThrows(IllegalStateException.class,
                () -> new HlrPredictor(weights(CONTRACT_ORDER, Arrays.copyOf(zeros(), 13))));
    }

    @Test
    void shippedPlaceholderFileLoadsAndIsFlagged() throws Exception {
        InputStream in = HlrPredictorTest.class.getResourceAsStream("/ml/hlr_weights.json");
        assertNotNull(in, "ml/hlr_weights.json must be on the classpath");
        HlrPredictor predictor = new HlrPredictor(HlrPredictor.parse(in));
        assertTrue(predictor.isPlaceholder());
    }

    @Test
    void unknownKeysAreIgnoredAndPlaceholderIsOptional() throws Exception {
        String json = "{\"feature_order\":" + toJson(CONTRACT_ORDER)
                + ",\"theta\":[0,0,0,0,0,0,0,0,0,0,0,0,0,0],\"min_h\":0.25,\"max_h\":180,"
                + "\"metrics\":{\"auc\":0.7},\"trained_on\":\"2026-10-01\"}";
        HlrWeights parsed = HlrPredictor.parse(new java.io.ByteArrayInputStream(json.getBytes()));
        HlrPredictor predictor = new HlrPredictor(parsed);
        assertTrue(!predictor.isPlaceholder());
    }

    private static String toJson(List<String> names) {
        return names.stream().map(n -> "\"" + n + "\"").reduce((a, b) -> a + "," + b).map(s -> "[" + s + "]").orElse("[]");
    }
}
