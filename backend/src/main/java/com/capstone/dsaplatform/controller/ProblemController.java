package com.capstone.dsaplatform.controller;

import com.capstone.dsaplatform.domain.Difficulty;
import com.capstone.dsaplatform.dto.ProblemDtos.ProblemDetail;
import com.capstone.dsaplatform.dto.ProblemDtos.ProblemSummary;
import com.capstone.dsaplatform.service.ProblemService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** CONTRACT.md sections 5.9 - 5.10. */
@RestController
@RequestMapping("/api/problems")
public class ProblemController {

    private final ProblemService problemService;

    public ProblemController(ProblemService problemService) {
        this.problemService = problemService;
    }

    // Filters are optional: the problem list is browsable regardless of topic lock state.
    // An invalid difficulty fails enum conversion and becomes a 400 in ApiExceptionHandler.
    @GetMapping
    public List<ProblemSummary> list(@RequestParam(required = false) Long topicId,
                                     @RequestParam(required = false) Difficulty difficulty) {
        return problemService.list(topicId, difficulty);
    }

    @GetMapping("/{id}")
    public ProblemDetail get(@PathVariable Long id) {
        return problemService.detail(id);
    }
}
