package com.capstone.dsaplatform.dto;

import com.capstone.dsaplatform.domain.Difficulty;

import java.util.List;

/** Problem endpoints (CONTRACT.md sections 5.9 - 5.10). */
public final class ProblemDtos {

    private ProblemDtos() {
    }

    public record ProblemSummary(Long id, String title, String slug, Long topicId, String topicName, Difficulty difficulty) {
    }

    public record TopicRef(Long id, String name) {
    }

    public record SampleTestView(Integer ordinal, String input, String expectedOutput) {
    }

    // Only SAMPLE tests are exposed; testCount includes hidden ones so the UI can show "2 of 7 visible".
    public record ProblemDetail(Long id,
                                String title,
                                String slug,
                                TopicRef topic,
                                Difficulty difficulty,
                                String statement,
                                String inputFormat,
                                String outputFormat,
                                String starterCode,
                                Integer timeLimitMs,
                                Integer targetSolveSeconds,
                                List<SampleTestView> sampleTests,
                                long testCount) {
    }
}
