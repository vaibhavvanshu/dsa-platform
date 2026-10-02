package com.capstone.dsaplatform.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 1A stub: the route exists so the frontend can be wired against it,
 * but returns 501 until the runner and learner-model logic are built.
 * Request/response shapes: docs/CONTRACT.md section 5.1.
 */
@RestController
@RequestMapping("/api/submissions")
public class SubmissionController {

    @PostMapping
    public ResponseEntity<Void> submit() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
