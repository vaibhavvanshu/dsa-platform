package com.capstone.dsaplatform.service;

import com.capstone.dsaplatform.domain.Difficulty;
import com.capstone.dsaplatform.domain.TestTag;
import com.capstone.dsaplatform.domain.Verdict;
import com.capstone.dsaplatform.dto.ResourceLink;
import com.capstone.dsaplatform.dto.SubmissionDtos.FailedTestView;
import com.capstone.dsaplatform.dto.SubmissionDtos.SubmissionRequest;
import com.capstone.dsaplatform.dto.SubmissionDtos.SubmissionResponse;
import com.capstone.dsaplatform.dto.SubmissionDtos.TestResultView;
import com.capstone.dsaplatform.entity.Attempt;
import com.capstone.dsaplatform.entity.ErrorEvent;
import com.capstone.dsaplatform.entity.Problem;
import com.capstone.dsaplatform.entity.TestCase;
import com.capstone.dsaplatform.entity.Topic;
import com.capstone.dsaplatform.entity.TopicState;
import com.capstone.dsaplatform.entity.User;
import com.capstone.dsaplatform.error.BadRequestException;
import com.capstone.dsaplatform.error.NotFoundException;
import com.capstone.dsaplatform.learner.HlrPredictor;
import com.capstone.dsaplatform.learner.SubmissionEvaluator;
import com.capstone.dsaplatform.learner.SubmissionEvaluator.Evaluation;
import com.capstone.dsaplatform.learner.SubmissionEvaluator.TestInfo;
import com.capstone.dsaplatform.learner.SubmissionEvaluator.TestRun;
import com.capstone.dsaplatform.repository.AttemptRepository;
import com.capstone.dsaplatform.repository.ErrorEventRepository;
import com.capstone.dsaplatform.repository.ProblemRepository;
import com.capstone.dsaplatform.repository.TestCaseRepository;
import com.capstone.dsaplatform.repository.TopicStateRepository;
import com.capstone.dsaplatform.runner.CodeRunner;
import com.capstone.dsaplatform.runner.RunResult;
import com.capstone.dsaplatform.support.Times;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The submission pipeline (CONTRACT.md section 4.1): run -> verdict + category -> attempt ->
 * at most one error_event -> topic_state -> readiness snapshot -> response.
 */
@Service
public class SubmissionService {

    // DP is the only topic with a special category rule (precedence step 6).
    private static final long DP_TOPIC_ID = 10L;
    // Matches error_events.exception_type VARCHAR(120); a longer name must not fail the whole submission.
    private static final int EXCEPTION_TYPE_MAX = 120;

    private final UserService userService;
    private final ProblemRepository problems;
    private final TestCaseRepository testCases;
    private final AttemptRepository attempts;
    private final ErrorEventRepository errorEvents;
    private final TopicStateRepository topicStates;
    private final CodeRunner codeRunner;
    private final HlrPredictor hlr;
    private final ReadinessService readinessService;
    private final ResourceService resourceService;

    public SubmissionService(UserService userService, ProblemRepository problems, TestCaseRepository testCases,
                             AttemptRepository attempts, ErrorEventRepository errorEvents,
                             TopicStateRepository topicStates, CodeRunner codeRunner, HlrPredictor hlr,
                             ReadinessService readinessService, ResourceService resourceService) {
        this.userService = userService;
        this.problems = problems;
        this.testCases = testCases;
        this.attempts = attempts;
        this.errorEvents = errorEvents;
        this.topicStates = topicStates;
        this.codeRunner = codeRunner;
        this.hlr = hlr;
        this.readinessService = readinessService;
        this.resourceService = resourceService;
    }

    /**
     * One transaction for everything, so an attempt is never stored without its learner-model update.
     * The runner executes inside it; for a single-user demo, holding a transaction for a few seconds is fine.
     */
    @Transactional
    public SubmissionResponse submit(SubmissionRequest request) {
        validate(request);
        User user = userService.require(request.userId());
        Problem problem = problems.findById(request.problemId())
                .orElseThrow(() -> new NotFoundException("Problem " + request.problemId() + " does not exist"));
        Topic topic = problem.getTopic();

        Map<Long, TestCase> testById = testCases.findByProblemId(problem.getId()).stream()
                .collect(Collectors.toMap(TestCase::getId, Function.identity()));
        List<TestInfo> tests = testById.values().stream()
                .map(tc -> new TestInfo(tc.getId(), tc.getTag(), tc.getOrdinal(), tc.getErrorCategoryOverride()))
                .toList();

        RunResult result = codeRunner.run(problem, request.code());
        Evaluation ev = SubmissionEvaluator.evaluate(result, tests, problem.getOptimalRuntimeMs(),
                topic.getId() == DP_TOPIC_ID);
        LocalDateTime now = Times.nowUtc();

        Attempt attempt = new Attempt();
        attempt.setUser(user);
        attempt.setProblem(problem);
        attempt.setCode(request.code());
        attempt.setVerdict(ev.verdict());
        attempt.setPassedCount(ev.passedCount());
        attempt.setTotalCount(ev.totalCount());
        attempt.setLargeRuntimeMs(ev.largeRuntimeMs());
        attempt.setTimeSpentSeconds(request.timeSpentSeconds());
        attempt.setSubmittedAt(now);
        attempts.save(attempt);

        if (ev.category() != null) {
            saveErrorEvent(attempt, user, topic, ev, testById, now);
        }
        updateTopicState(user, topic, problem.getDifficulty(), ev.verdict() == Verdict.ACCEPTED, now);
        readinessService.writeSnapshot(user, now);

        List<ResourceLink> resources = ev.category() == null
                ? List.of()
                : resourceService.linksFor(ev.category(), topic.getId(), topic.getName());

        return new SubmissionResponse(attempt.getId(), user.getId(), problem.getId(), topic.getId(),
                ev.verdict(), ev.passedCount(), ev.totalCount(), ev.category(),
                failedTestView(ev.flaggedRun(), testById), ev.message(),
                ev.largeRuntimeMs(), problem.getOptimalRuntimeMs(),
                ev.runs().stream().map(r -> new TestResultView(r.test().ordinal(), r.test().tag(), r.verdict(),
                        r.outcome().runtimeMs())).toList(),
                resources, Times.toInstant(now));
    }

    private static void validate(SubmissionRequest request) {
        if (request == null || request.userId() == null || request.problemId() == null) {
            throw new BadRequestException("userId and problemId are required");
        }
        if (request.code() == null || request.code().isBlank()) {
            throw new BadRequestException("code must not be blank");
        }
        // The DB rejects negatives anyway; checking here turns a 500 into a clear 400.
        if (request.timeSpentSeconds() != null && request.timeSpentSeconds() < 0) {
            throw new BadRequestException("timeSpentSeconds must be >= 0");
        }
    }

    private void saveErrorEvent(Attempt attempt, User user, Topic topic, Evaluation ev,
                                Map<Long, TestCase> testById, LocalDateTime now) {
        TestRun flagged = ev.flaggedRun(); // null only for SYNTAX_COMPILATION
        ErrorEvent event = new ErrorEvent();
        event.setAttempt(attempt);
        event.setUser(user);
        event.setTopic(topic);
        event.setErrorCategory(ev.category());
        event.setOccurredAt(now);
        if (flagged != null) {
            event.setFailedTag(flagged.test().tag());
            event.setFailedTestCase(testById.get(flagged.test().id()));
            event.setExceptionType(truncate(flagged.outcome().exceptionType()));
        }
        errorEvents.save(event);
    }

    /** CONTRACT.md section 4.3 update order: counts, mean difficulty, last practiced, half-life. */
    private void updateTopicState(User user, Topic topic, Difficulty difficulty, boolean accepted, LocalDateTime now) {
        TopicState state = topicStates.findByUserIdAndTopicId(user.getId(), topic.getId()).orElseGet(() -> {
            TopicState s = new TopicState();
            s.setUser(user);
            s.setTopic(topic);
            return s;
        });

        int previousAttempts = state.getCorrectCount() + state.getWrongCount(); // n BEFORE this submission
        if (accepted) {
            state.setCorrectCount(state.getCorrectCount() + 1);
        } else {
            state.setWrongCount(state.getWrongCount() + 1);
        }
        // Running mean, so the full attempt history never has to be re-read.
        double weight = difficulty.getWeight();
        Double oldMean = state.getMeanDifficulty();
        state.setMeanDifficulty(oldMean == null ? weight : (oldMean * previousAttempts + weight) / (previousAttempts + 1));
        state.setLastPracticedAt(now);
        // Display copy only (e.g. for the H2 console); predictions always recompute half-life live.
        state.setHalfLifeDays(hlr.halfLifeDays(state.getCorrectCount(), state.getWrongCount(),
                state.getMeanDifficulty(), topic.getId()));
        topicStates.save(state);
    }

    private static FailedTestView failedTestView(TestRun flagged, Map<Long, TestCase> testById) {
        if (flagged == null) {
            return null;
        }
        // Only SAMPLE tests are visible to the learner; hidden test data must not leak through errors.
        boolean visible = flagged.test().tag() == TestTag.SAMPLE;
        TestCase tc = testById.get(flagged.test().id());
        return new FailedTestView(tc.getId(), tc.getTag(), tc.getOrdinal(),
                visible ? tc.getInput() : null,
                visible ? tc.getExpectedOutput() : null,
                visible ? flagged.outcome().actualOutput() : null);
    }

    private static String truncate(String s) {
        return s == null || s.length() <= EXCEPTION_TYPE_MAX ? s : s.substring(0, EXCEPTION_TYPE_MAX);
    }
}
