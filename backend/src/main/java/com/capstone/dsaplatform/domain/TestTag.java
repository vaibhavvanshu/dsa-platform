package com.capstone.dsaplatform.domain;

/**
 * What a test case is designed to probe.
 * Declaration order matters: it is the order used to pick the "first failure"
 * (SAMPLE -> EDGE -> BOUNDARY -> LARGE), so do not reorder these constants.
 */
public enum TestTag {
    SAMPLE,
    EDGE,
    BOUNDARY,
    LARGE
}
