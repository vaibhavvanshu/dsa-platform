package com.capstone.dsaplatform.controller;

import com.capstone.dsaplatform.dto.TopicGraphDtos.TopicGraphResponse;
import com.capstone.dsaplatform.service.TopicStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** CONTRACT.md section 5.8. */
@RestController
@RequestMapping("/api/topics")
public class TopicController {

    private final TopicStatusService topicStatusService;

    public TopicController(TopicStatusService topicStatusService) {
        this.topicStatusService = topicStatusService;
    }

    // userId is required because "unlocked" depends on that learner's retention.
    @GetMapping("/graph")
    public TopicGraphResponse graph(@RequestParam Long userId) {
        return topicStatusService.graph(userId);
    }
}
