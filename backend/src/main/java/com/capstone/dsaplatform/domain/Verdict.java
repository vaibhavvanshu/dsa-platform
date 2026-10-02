package com.capstone.dsaplatform.domain;

/** Outcome of a whole submission (and of each individual test run). */
public enum Verdict {
    ACCEPTED,
    WRONG_ANSWER,
    COMPILE_ERROR,
    RUNTIME_ERROR,
    TIME_LIMIT_EXCEEDED
}
