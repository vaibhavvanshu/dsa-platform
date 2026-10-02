package com.capstone.dsaplatform.domain;

/**
 * Why a submission went wrong. This, not the verdict, drives the recurring-error profile
 * and resource links, because "wrong answer" alone says nothing about what to study.
 */
public enum ErrorCategory {
    SYNTAX_COMPILATION,
    OFF_BY_ONE,
    MISSED_EDGE_CASE,
    WRONG_ALGORITHM,
    STATE_TRANSITION,
    BAD_COMPLEXITY,
    SUBOPTIMAL
}
