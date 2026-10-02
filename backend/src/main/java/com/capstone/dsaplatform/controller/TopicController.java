package com.capstone.dsaplatform.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Phase 1A stub. Shape: docs/CONTRACT.md section 5.8. */
@RestController
@RequestMapping("/api/topics")
public class TopicController {

    // userId is required because "unlocked" depends on that learner's retention.
    @GetMapping("/graph")
    public ResponseEntity<Void> graph(@RequestParam Long userId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
