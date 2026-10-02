package com.capstone.dsaplatform.learner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Cross-language parity: the real HlrPredictor must reproduce every value the Python side computed.
 * Copy fixtures/hlr_fixtures.json to backend/src/test/resources/ml/hlr_fixtures.json.
 */
class HlrFixtureParityTest {

    @Test
    void javaMatchesPythonOnEveryFixture() throws Exception {
        JsonNode root;
        try (InputStream in = getClass().getResourceAsStream("/ml/hlr_fixtures.json")) {
            root = new ObjectMapper().readTree(in);
        }
        double[] theta = new double[14];
        for (int i = 0; i < 14; i++) {
            theta[i] = root.get("theta").get(i).asDouble();
        }
        List<String> order = new ArrayList<>(HlrPredictor.FEATURE_ORDER);
        HlrPredictor p = new HlrPredictor(new HlrWeights(order, theta, root.get("min_h").asDouble(),
                root.get("max_h").asDouble(), null));
        double tol = root.get("tolerance").asDouble();
        for (JsonNode c : root.get("cases")) {
            double h = p.halfLifeDays(c.get("correct").asInt(), c.get("wrong").asInt(),
                    c.get("mean_difficulty").asDouble(), c.get("topic_id").asLong());
            assertEquals(c.get("half_life_days").asDouble(), h, tol * Math.max(1, h), "half-life " + c);
            assertEquals(c.get("recall").asDouble(), HlrPredictor.recall(h, c.get("delta_days").asDouble()), tol, "recall " + c);
        }
    }
}
