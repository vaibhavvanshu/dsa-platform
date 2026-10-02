package com.capstone.dsaplatform.service;

import com.capstone.dsaplatform.domain.Verdict;
import com.capstone.dsaplatform.dto.RecommendationDtos.RecommendationItem;
import com.capstone.dsaplatform.dto.RecommendationDtos.RecommendationResponse;
import com.capstone.dsaplatform.entity.Problem;
import com.capstone.dsaplatform.error.BadRequestException;
import com.capstone.dsaplatform.learner.RecommendationScorer;
import com.capstone.dsaplatform.learner.RecommendationScorer.Candidate;
import com.capstone.dsaplatform.learner.UnlockRule;
import com.capstone.dsaplatform.repository.AttemptRepository;
import com.capstone.dsaplatform.repository.ProblemRepository;
import com.capstone.dsaplatform.service.TopicStatusService.TopicStatus;
import com.capstone.dsaplatform.support.Rounding;
import com.capstone.dsaplatform.support.Times;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gathers the numbers for every problem; the scoring itself is in RecommendationScorer
 * (pure, unit-tested) so this class is only data plumbing.
 */
@Service
public class RecommendationService {

    private static final int MAX_LIMIT = 20;

    private final UserService userService;
    private final TopicStatusService topicStatusService;
    private final ProblemRepository problems;
    private final AttemptRepository attempts;
    private final UnlockRule unlockRule;

    public RecommendationService(UserService userService, TopicStatusService topicStatusService,
                                 ProblemRepository problems, AttemptRepository attempts, UnlockRule unlockRule) {
        this.userService = userService;
        this.topicStatusService = topicStatusService;
        this.problems = problems;
        this.attempts = attempts;
        this.unlockRule = unlockRule;
    }

    @Transactional(readOnly = true)
    public RecommendationResponse recommend(Long userId, int limit) {
        userService.require(userId);
        if (limit < 1) {
            throw new BadRequestException("limit must be at least 1");
        }
        LocalDateTime now = Times.nowUtc();
        List<TopicStatus> statuses = topicStatusService.forUser(userId, now);

        Map<Long, TopicStatus> statusByTopic = new HashMap<>();
        // Reverse edges: which topics does each topic gate? Used only for the reason text.
        Map<Long, List<String>> gatedNames = new HashMap<>();
        for (TopicStatus st : statuses) {
            statusByTopic.put(st.topic().getId(), st);
            for (Long prerequisiteId : st.prerequisiteIds()) {
                gatedNames.computeIfAbsent(prerequisiteId, k -> new ArrayList<>()).add(st.topic().getName());
            }
        }
        Set<Long> acceptedProblemIds = new HashSet<>(attempts.findProblemIdsWithVerdict(userId, Verdict.ACCEPTED));

        List<Candidate> candidates = new ArrayList<>();
        for (Problem p : problems.findAllWithTopic()) {
            TopicStatus st = statusByTopic.get(p.getTopic().getId());
            candidates.add(new Candidate(p.getId(), p.getTitle(), st.topic().getId(), st.topic().getName(),
                    p.getDifficulty(), st.unlocked(), st.correctCount(), st.wrongCount(), st.predictedRetention(),
                    acceptedProblemIds.contains(p.getId()),
                    gatedNames.getOrDefault(st.topic().getId(), List.of())));
        }

        List<RecommendationItem> items = RecommendationScorer
                .rank(candidates, unlockRule.getMinRetention(), Math.min(limit, MAX_LIMIT)).stream()
                .map(s -> new RecommendationItem(s.candidate().problemId(), s.candidate().title(),
                        s.candidate().topicId(), s.candidate().topicName(), s.candidate().difficulty(),
                        Rounding.twoDecimals(s.score()), s.reason()))
                .toList();
        return new RecommendationResponse(userId, Times.toInstant(now), items);
    }
}
