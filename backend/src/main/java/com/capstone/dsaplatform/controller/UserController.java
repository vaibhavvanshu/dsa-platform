package com.capstone.dsaplatform.controller;

import com.capstone.dsaplatform.dto.ReadinessDtos.ReadinessResponse;
import com.capstone.dsaplatform.dto.ReadinessDtos.SelfRatingRequest;
import com.capstone.dsaplatform.dto.ReadinessDtos.SelfRatingResponse;
import com.capstone.dsaplatform.dto.RecommendationDtos.RecommendationResponse;
import com.capstone.dsaplatform.dto.RetentionDtos.RetentionResponse;
import com.capstone.dsaplatform.dto.UserView;
import com.capstone.dsaplatform.dto.WeaknessDtos.WeaknessResponse;
import com.capstone.dsaplatform.service.ReadinessService;
import com.capstone.dsaplatform.service.RecommendationService;
import com.capstone.dsaplatform.service.TopicStatusService;
import com.capstone.dsaplatform.service.UserService;
import com.capstone.dsaplatform.service.WeaknessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Everything scoped to one learner. Shapes: CONTRACT.md sections 5.2 - 5.7. */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final TopicStatusService topicStatusService;
    private final RecommendationService recommendationService;
    private final WeaknessService weaknessService;
    private final ReadinessService readinessService;

    public UserController(UserService userService, TopicStatusService topicStatusService,
                          RecommendationService recommendationService, WeaknessService weaknessService,
                          ReadinessService readinessService) {
        this.userService = userService;
        this.topicStatusService = topicStatusService;
        this.recommendationService = recommendationService;
        this.weaknessService = weaknessService;
        this.readinessService = readinessService;
    }

    @GetMapping
    public List<UserView> listUsers() {
        return userService.list();
    }

    @GetMapping("/{id}/retention")
    public RetentionResponse retention(@PathVariable Long id) {
        return topicStatusService.retention(id);
    }

    // Default 5 and cap 20 per contract; the cap is applied in the service.
    @GetMapping("/{id}/recommendations")
    public RecommendationResponse recommendations(@PathVariable Long id,
                                                  @RequestParam(defaultValue = "5") int limit) {
        return recommendationService.recommend(id, limit);
    }

    @GetMapping("/{id}/weakness")
    public WeaknessResponse weakness(@PathVariable Long id) {
        return weaknessService.profile(id);
    }

    @GetMapping("/{id}/readiness")
    public ReadinessResponse readiness(@PathVariable Long id) {
        return readinessService.report(id);
    }

    @PostMapping("/{id}/self-rating")
    public SelfRatingResponse selfRating(@PathVariable Long id, @RequestBody SelfRatingRequest request) {
        return readinessService.setSelfRating(id, request);
    }
}
