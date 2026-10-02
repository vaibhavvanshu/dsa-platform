package com.capstone.dsaplatform.learner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The contents of hlr_weights.json (CONTRACT.md section 6).
 * Unknown keys such as "metrics" are ignored, so the Python side can add diagnostics
 * without breaking Java.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HlrWeights(@JsonProperty("feature_order") List<String> featureOrder,
                         @JsonProperty("theta") double[] theta,
                         @JsonProperty("min_h") double minH,
                         @JsonProperty("max_h") double maxH,
                         // Optional; true only in the hand-written stand-in file. The trained file omits it.
                         @JsonProperty("placeholder") Boolean placeholder) {
}
