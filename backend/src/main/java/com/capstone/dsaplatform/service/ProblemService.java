package com.capstone.dsaplatform.service;

import com.capstone.dsaplatform.domain.Difficulty;
import com.capstone.dsaplatform.domain.TestTag;
import com.capstone.dsaplatform.dto.ProblemDtos.ProblemDetail;
import com.capstone.dsaplatform.dto.ProblemDtos.ProblemSummary;
import com.capstone.dsaplatform.dto.ProblemDtos.SampleTestView;
import com.capstone.dsaplatform.dto.ProblemDtos.TopicRef;
import com.capstone.dsaplatform.entity.Problem;
import com.capstone.dsaplatform.entity.TestCase;
import com.capstone.dsaplatform.error.NotFoundException;
import com.capstone.dsaplatform.repository.ProblemRepository;
import com.capstone.dsaplatform.repository.TestCaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/** Problem browsing. Lock state is ignored on purpose: learners may open any problem manually. */
@Service
public class ProblemService {

    private final ProblemRepository problems;
    private final TestCaseRepository testCases;

    public ProblemService(ProblemRepository problems, TestCaseRepository testCases) {
        this.problems = problems;
        this.testCases = testCases;
    }

    /** Both filters optional; ordered by topic id, then difficulty (EASY first), then id. */
    @Transactional(readOnly = true)
    public List<ProblemSummary> list(Long topicId, Difficulty difficulty) {
        return problems.findAllWithTopic().stream()
                .filter(p -> topicId == null || p.getTopic().getId().equals(topicId))
                .filter(p -> difficulty == null || p.getDifficulty() == difficulty)
                .sorted(Comparator.comparing((Problem p) -> p.getTopic().getId())
                        .thenComparing(Problem::getDifficulty) // enum order = EASY, MEDIUM, HARD
                        .thenComparing(Problem::getId))
                .map(p -> new ProblemSummary(p.getId(), p.getTitle(), p.getSlug(), p.getTopic().getId(),
                        p.getTopic().getName(), p.getDifficulty()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProblemDetail detail(Long id) {
        Problem p = problems.findById(id)
                .orElseThrow(() -> new NotFoundException("Problem " + id + " does not exist"));
        List<TestCase> tests = testCases.findByProblemId(id);
        List<SampleTestView> samples = tests.stream()
                .filter(tc -> tc.getTag() == TestTag.SAMPLE)
                .sorted(Comparator.comparing(TestCase::getOrdinal))
                .map(tc -> new SampleTestView(tc.getOrdinal(), tc.getInput(), tc.getExpectedOutput()))
                .toList();
        return new ProblemDetail(p.getId(), p.getTitle(), p.getSlug(),
                new TopicRef(p.getTopic().getId(), p.getTopic().getName()), p.getDifficulty(),
                p.getStatement(), p.getInputFormat(), p.getOutputFormat(), p.getStarterCode(),
                p.getTimeLimitMs(), p.getTargetSolveSeconds(), samples, tests.size());
    }
}
