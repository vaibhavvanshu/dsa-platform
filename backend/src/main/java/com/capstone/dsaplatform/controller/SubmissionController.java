package com.capstone.dsaplatform.controller;

import com.capstone.dsaplatform.dto.SubmissionDtos.SubmissionRequest;
import com.capstone.dsaplatform.dto.SubmissionDtos.SubmissionResponse;
import com.capstone.dsaplatform.service.SubmissionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CONTRACT.md section 5.1. Thin by design: all pipeline logic is in SubmissionService. */
@RestController
@RequestMapping("/api/submissions")
public class SubmissionController {

    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @PostMapping
    public SubmissionResponse submit(@RequestBody SubmissionRequest request) {
        return submissionService.submit(request);
    }
}
