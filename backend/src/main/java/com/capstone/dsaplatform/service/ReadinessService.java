package com.capstone.dsaplatform.service;

import com.capstone.dsaplatform.dto.ReadinessDtos.ComponentsView;
import com.capstone.dsaplatform.dto.ReadinessDtos.HistoryPoint;
import com.capstone.dsaplatform.dto.ReadinessDtos.OverallView;
import com.capstone.dsaplatform.dto.ReadinessDtos.ReadinessResponse;
import com.capstone.dsaplatform.dto.ReadinessDtos.SelfRatingRequest;
import com.capstone.dsaplatform.dto.ReadinessDtos.SelfRatingResponse;
import com.capstone.dsaplatform.dto.ReadinessDtos.SnapshotView;
import com.capstone.dsaplatform.dto.ReadinessDtos.TopicReadinessView;
import com.capstone.dsaplatform.entity.ReadinessSnapshot;
import com.capstone.dsaplatform.entity.Topic;
import com.capstone.dsaplatform.entity.TopicState;
import com.capstone.dsaplatform.entity.User;
import com.capstone.dsaplatform.error.BadRequestException;
import com.capstone.dsaplatform.error.NotFoundException;
import com.capstone.dsaplatform.learner.ReadinessCalculator;
import com.capstone.dsaplatform.learner.ReadinessCalculator.RecentAttempt;
import com.capstone.dsaplatform.repository.AttemptRepository;
import com.capstone.dsaplatform.repository.ReadinessSnapshotRepository;
import com.capstone.dsaplatform.repository.TopicRepository;
import com.capstone.dsaplatform.repository.TopicStateRepository;
import com.capstone.dsaplatform.service.TopicStatusService.TopicStatus;
import com.capstone.dsaplatform.support.Rounding;
import com.capstone.dsaplatform.support.Times;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Readiness = evidence-based score vs the learner's self-rating (CONTRACT.md section 4.4).
 * Per-topic numbers are always computed live; only the overall numbers are snapshotted,
 * after every submission and self-rating change, to draw the history chart.
 */
@Service
public class ReadinessService {

    private final AttemptRepository attempts;
    private final ReadinessSnapshotRepository snapshots;
    private final TopicRepository topics;
    private final TopicStateRepository topicStates;
    private final TopicStatusService topicStatusService;
    private final UserService userService;
    private final int recentAttempts;

    public ReadinessService(AttemptRepository attempts, ReadinessSnapshotRepository snapshots, TopicRepository topics,
                            TopicStateRepository topicStates, TopicStatusService topicStatusService,
                            UserService userService,
                            @Value("${app.readiness.recent-attempts}") int recentAttempts) {
        this.attempts = attempts;
        this.snapshots = snapshots;
        this.topics = topics;
        this.topicStates = topicStates;
        this.topicStatusService = topicStatusService;
        this.userService = userService;
        this.recentAttempts = recentAttempts;
    }

    /** Unrounded results; rounding happens only when building a response. */
    private record TopicReadiness(TopicStatus status, ComponentsView components, Double readiness, Double gap) {
    }

    private record Computation(List<TopicReadiness> topics,
                               double consistency,
                               Double overallComputed,
                               Double overallSelfRating,
                               Double overallGap,
                               double evidenceCoverage) {
    }

    @Transactional(readOnly = true)
    public ReadinessResponse report(Long userId) {
        userService.require(userId);
        LocalDateTime now = Times.nowUtc();
        Computation c = compute(userId, now);

        List<TopicReadinessView> topicViews = c.topics().stream()
                .map(t -> new TopicReadinessView(
                        t.status().topic().getId(),
                        t.status().topic().getName(),
                        t.status().attempted(),
                        Rounding.oneDecimal(t.readiness()),
                        t.components() == null ? null : roundComponents(t.components()),
                        t.status().selfRating(),
                        Rounding.oneDecimal(t.gap())))
                .toList();

        OverallView overall = new OverallView(
                Rounding.oneDecimal(c.overallComputed()),
                Rounding.twoDecimals(c.overallSelfRating()),
                Rounding.oneDecimal(c.overallSelfRating() == null ? null : c.overallSelfRating() * 20),
                Rounding.oneDecimal(c.overallGap()),
                c.evidenceCoverage());

        // Repository returns newest first (so "last 30" is a simple LIMIT); the chart wants oldest first.
        List<ReadinessSnapshot> latest = new ArrayList<>(snapshots.findTop30ByUserIdOrderByTakenAtDescIdDesc(userId));
        Collections.reverse(latest);
        List<HistoryPoint> history = latest.stream()
                .map(s -> new HistoryPoint(Times.toInstant(s.getTakenAt()),
                        Rounding.twoDecimals(s.getOverallSelfRating()),
                        Rounding.oneDecimal(s.getOverallComputed()),
                        Rounding.oneDecimal(s.getGap())))
                .toList();

        return new ReadinessResponse(userId, Times.toInstant(now), overall,
                Rounding.twoDecimals(c.consistency()), topicViews, history);
    }

    /** Called after every submission and every self-rating change. */
    @Transactional
    public ReadinessSnapshot writeSnapshot(User user, LocalDateTime nowUtc) {
        Computation c = compute(user.getId(), nowUtc);
        ReadinessSnapshot snapshot = new ReadinessSnapshot();
        snapshot.setUser(user);
        snapshot.setTakenAt(nowUtc);
        snapshot.setOverallSelfRating(c.overallSelfRating());
        snapshot.setOverallComputed(c.overallComputed());
        snapshot.setGap(c.overallGap());
        return snapshots.save(snapshot);
    }

    @Transactional
    public SelfRatingResponse setSelfRating(Long userId, SelfRatingRequest request) {
        User user = userService.require(userId);
        if (request == null || request.topicId() == null) {
            throw new BadRequestException("topicId is required");
        }
        if (request.rating() == null || request.rating() < 1 || request.rating() > 5) {
            throw new BadRequestException("rating must be an integer from 1 to 5");
        }
        Topic topic = topics.findById(request.topicId())
                .orElseThrow(() -> new NotFoundException("Topic " + request.topicId() + " does not exist"));

        // Rating a never-practised topic is allowed, so the row may not exist yet (created lazily).
        TopicState state = topicStates.findByUserIdAndTopicId(userId, topic.getId()).orElseGet(() -> {
            TopicState s = new TopicState();
            s.setUser(user);
            s.setTopic(topic);
            return s;
        });
        state.setSelfRating(request.rating());
        topicStates.save(state);

        ReadinessSnapshot snapshot = writeSnapshot(user, Times.nowUtc());
        SnapshotView snapshotView = new SnapshotView(snapshot.getId(), Times.toInstant(snapshot.getTakenAt()),
                Rounding.twoDecimals(snapshot.getOverallSelfRating()),
                Rounding.oneDecimal(snapshot.getOverallComputed()),
                Rounding.oneDecimal(snapshot.getGap()));
        return new SelfRatingResponse(userId, topic.getId(), request.rating(),
                Rounding.twoDecimals(snapshot.getOverallSelfRating()), snapshotView);
    }

    private Computation compute(Long userId, LocalDateTime nowUtc) {
        List<TopicStatus> statuses = topicStatusService.forUser(userId, nowUtc);

        // Consistency is about the learner's habit, not a topic, so it is computed once for all topics.
        LocalDateTime windowStart = nowUtc.toLocalDate().minusDays(13).atStartOfDay();
        double consistency = ReadinessCalculator.consistency(
                attempts.findSubmissionTimesSince(userId, windowStart), nowUtc);

        List<TopicReadiness> perTopic = new ArrayList<>();
        for (TopicStatus st : statuses) {
            if (!st.attempted()) {
                perTopic.add(new TopicReadiness(st, null, null, null));
                continue;
            }
            List<RecentAttempt> recent = attempts
                    .findRecentInTopic(userId, st.topic().getId(), PageRequest.of(0, recentAttempts)).stream()
                    .map(a -> new RecentAttempt(a.getVerdict(), a.getTimeSpentSeconds(),
                            a.getProblem().getTargetSolveSeconds()))
                    .toList();
            ComponentsView components = new ComponentsView(
                    ReadinessCalculator.recentAccuracy(recent),
                    st.predictedRetention(),
                    consistency,
                    ReadinessCalculator.speedVsTarget(recent));
            double readiness = ReadinessCalculator.topicReadiness(components.recentAccuracy(),
                    components.retention(), components.consistency(), components.speedVsTarget());
            Double gap = ReadinessCalculator.gap(st.selfRating() == null ? null : st.selfRating().doubleValue(), readiness);
            perTopic.add(new TopicReadiness(st, components, readiness, gap));
        }

        Double overallComputed = ReadinessCalculator.meanOfPresent(perTopic.stream().map(TopicReadiness::readiness).toList());
        // Ratings on unattempted topics still count: the learner's self-image is what is being compared.
        Double overallSelf = ReadinessCalculator.meanOfPresent(statuses.stream().map(TopicStatus::selfRating).toList());
        long attemptedCount = statuses.stream().filter(TopicStatus::attempted).count();

        return new Computation(perTopic, consistency, overallComputed, overallSelf,
                ReadinessCalculator.gap(overallSelf, overallComputed), attemptedCount / 10.0);
    }

    private static ComponentsView roundComponents(ComponentsView c) {
        return new ComponentsView(Rounding.twoDecimals(c.recentAccuracy()), Rounding.twoDecimals(c.retention()),
                Rounding.twoDecimals(c.consistency()), Rounding.twoDecimals(c.speedVsTarget()));
    }
}
