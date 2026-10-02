package com.capstone.dsaplatform.controller;

import com.capstone.dsaplatform.domain.Difficulty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Phase 1A stubs. Shapes: docs/CONTRACT.md sections 5.9 - 5.10. */
@RestController
@RequestMapping("/api/problems")
public class ProblemController {

    // Filters are optional: the problem list is browsable regardless of topic lock state.
    @GetMapping
    public ResponseEntity<Void> list(@RequestParam(required = false) Long topicId,
                                     @RequestParam(required = false) Difficulty difficulty) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Void> get(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
