package com.capstone.dsaplatform.service;

import com.capstone.dsaplatform.dto.RetentionDtos.RetentionResponse;
import com.capstone.dsaplatform.dto.RetentionDtos.TopicRetentionView;
import com.capstone.dsaplatform.dto.TopicGraphDtos.EdgeView;
import com.capstone.dsaplatform.dto.TopicGraphDtos.NodeView;
import com.capstone.dsaplatform.dto.TopicGraphDtos.Thresholds;
import com.capstone.dsaplatform.dto.TopicGraphDtos.TopicGraphResponse;
import com.capstone.dsaplatform.entity.Topic;
import com.capstone.dsaplatform.entity.TopicState;
import com.capstone.dsaplatform.learner.HlrPredictor;
import com.capstone.dsaplatform.learner.UnlockRule;
import com.capstone.dsaplatform.repository.TopicRepository;
import com.capstone.dsaplatform.repository.TopicStateRepository;
import com.capstone.dsaplatform.support.Rounding;
import com.capstone.dsaplatform.support.Times;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The live learner model per topic: retention (HLR) and unlock status. Retention changes every
 * second and unlocks depend on retention, so neither is stored; both are computed here on request.
 * Retention, readiness, recommendations and the topic map all read from this one place.
 */
@Service
public class TopicStatusService {

    private final TopicRepository topics;
    private final TopicStateRepository topicStates;
    private final HlrPredictor hlr;
    private final UnlockRule unlockRule;
    private final UserService userService;

    public TopicStatusService(TopicRepository topics, TopicStateRepository topicStates, HlrPredictor hlr,
                              UnlockRule unlockRule, UserService userService) {
        this.topics = topics;
        this.topicStates = topicStates;
        this.hlr = hlr;
        this.unlockRule = unlockRule;
        this.userService = userService;
    }

    /**
     * One topic as seen by one learner right now.
     * state is null when there is no topic_state row; model fields are null when there are no attempts.
     */
    public record TopicStatus(Topic topic,
                              List<Long> prerequisiteIds,
                              TopicState state,
                              boolean attempted,
                              Double halfLifeDays,
                              Double daysSinceLastPractice,
                              Double predictedRetention,
                              boolean unlocked) {

        public int correctCount() {
            return state == null ? 0 : state.getCorrectCount();
        }

        public int wrongCount() {
            return state == null ? 0 : state.getWrongCount();
        }

        public Integer selfRating() {
            return state == null ? null : state.getSelfRating();
        }
    }

    /** All 10 topics in fixed order. */
    @Transactional(readOnly = true)
    public List<TopicStatus> forUser(Long userId, LocalDateTime nowUtc) {
        List<Topic> allTopics = topics.findAllWithPrerequisites();
        Map<Long, TopicState> stateByTopic = topicStates.findByUserId(userId).stream()
                .collect(Collectors.toMap(s -> s.getTopic().getId(), Function.identity()));

        // Pass 1: retention for every topic, because unlocking a topic needs its prerequisites' retention.
        Map<Long, Double> retentionByTopic = new HashMap<>();
        Map<Long, Integer> correctByTopic = new HashMap<>();
        Map<Long, double[]> modelByTopic = new HashMap<>(); // {halfLife, daysSince}
        for (Topic topic : allTopics) {
            TopicState s = stateByTopic.get(topic.getId());
            if (s == null) {
                continue;
            }
            correctByTopic.put(topic.getId(), s.getCorrectCount());
            if (isAttempted(s)) {
                // Live from the loaded theta; the stored half_life_days column is deliberately ignored.
                double h = hlr.halfLifeDays(s.getCorrectCount(), s.getWrongCount(), s.getMeanDifficulty(), topic.getId());
                double days = HlrPredictor.daysBetween(s.getLastPracticedAt(), nowUtc);
                retentionByTopic.put(topic.getId(), HlrPredictor.recall(h, days));
                modelByTopic.put(topic.getId(), new double[]{h, days});
            }
        }

        // Pass 2: unlock status from the prerequisites' numbers.
        List<TopicStatus> result = new ArrayList<>();
        for (Topic topic : allTopics) {
            List<Long> prerequisiteIds = topic.getPrerequisites().stream().map(Topic::getId).sorted().toList();
            TopicState s = stateByTopic.get(topic.getId());
            double[] model = modelByTopic.get(topic.getId());
            result.add(new TopicStatus(
                    topic,
                    prerequisiteIds,
                    s,
                    s != null && isAttempted(s),
                    model == null ? null : model[0],
                    model == null ? null : model[1],
                    retentionByTopic.get(topic.getId()),
                    unlockRule.isUnlocked(prerequisiteIds, retentionByTopic, correctByTopic)));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public RetentionResponse retention(Long userId) {
        userService.require(userId);
        LocalDateTime now = Times.nowUtc();
        List<TopicRetentionView> views = forUser(userId, now).stream()
                .map(st -> new TopicRetentionView(
                        st.topic().getId(),
                        st.topic().getName(),
                        st.attempted(),
                        st.correctCount(),
                        st.wrongCount(),
                        st.attempted() ? Rounding.twoDecimals(st.state().getMeanDifficulty()) : null,
                        st.attempted() ? Times.toInstant(st.state().getLastPracticedAt()) : null,
                        Rounding.twoDecimals(st.daysSinceLastPractice()),
                        Rounding.twoDecimals(st.halfLifeDays()),
                        Rounding.twoDecimals(st.predictedRetention())))
                .toList();
        return new RetentionResponse(userId, Times.toInstant(now), views);
    }

    @Transactional(readOnly = true)
    public TopicGraphResponse graph(Long userId) {
        userService.require(userId);
        List<TopicStatus> statuses = forUser(userId, Times.nowUtc());

        List<NodeView> nodes = statuses.stream()
                .map(st -> new NodeView(st.topic().getId(), st.topic().getName(), st.unlocked(), st.attempted(),
                        Rounding.twoDecimals(st.predictedRetention()), st.correctCount()))
                .toList();

        // Sorted by gated topic, then prerequisite, so the edge list is stable between calls.
        List<EdgeView> edges = statuses.stream()
                .flatMap(st -> st.prerequisiteIds().stream().map(p -> new EdgeView(p, st.topic().getId())))
                .sorted(Comparator.comparing(EdgeView::to).thenComparing(EdgeView::from))
                .toList();

        Thresholds thresholds = new Thresholds(unlockRule.getMinRetention(), unlockRule.getMinCorrectCount());
        return new TopicGraphResponse(userId, thresholds, nodes, edges);
    }

    // A row created only by a self-rating has zero attempts and therefore no HLR evidence.
    private static boolean isAttempted(TopicState s) {
        return s.getCorrectCount() + s.getWrongCount() > 0;
    }
}
