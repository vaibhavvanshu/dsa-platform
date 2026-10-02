package com.capstone.dsaplatform.dto;

import com.capstone.dsaplatform.domain.ErrorCategory;
import com.capstone.dsaplatform.domain.TestTag;
import com.capstone.dsaplatform.domain.Verdict;

import java.time.Instant;
import java.util.List;

/** Request/response of POST /api/submissions (CONTRACT.md section 5.1). Field order = JSON order. */
public final class SubmissionDtos {

    private SubmissionDtos() {
    }

    public record SubmissionRequest(Long userId, Long problemId, String code, Integer timeSpentSeconds) {
    }

    // input / expectedOutput / actualOutput are null for hidden tags so hidden tests never leak.
    public record FailedTestView(Long testCaseId, TestTag tag, Integer ordinal,
                                 String input, String expectedOutput, String actualOutput) {
    }

    public record TestResultView(Integer ordinal, TestTag tag, Verdict verdict, long runtimeMs) {
    }

    public record SubmissionResponse(Long attemptId,
                                     Long userId,
                                     Long problemId,
                                     Long topicId,
                                     Verdict verdict,
                                     int passedCount,
                                     int totalCount,
                                     ErrorCategory errorCategory,
                                     FailedTestView failedTest,
                                     String message,
                                     Integer largeRuntimeMs,
                                     Integer optimalRuntimeMs,
                                     List<TestResultView> testResults,
                                     List<ResourceLink> resources,
                                     Instant submittedAt) {
    }
}
