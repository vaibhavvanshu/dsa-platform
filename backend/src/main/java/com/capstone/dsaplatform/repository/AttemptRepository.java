package com.capstone.dsaplatform.repository;

import com.capstone.dsaplatform.domain.Verdict;
import com.capstone.dsaplatform.entity.Attempt;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {

    // Newest first; the Pageable carries the window size (app.readiness.recent-attempts).
    // The problem is fetched too because speed_vs_target needs its target_solve_seconds.
    @Query("select a from Attempt a join fetch a.problem p "
            + "where a.user.id = :userId and p.topic.id = :topicId "
            + "order by a.submittedAt desc, a.id desc")
    List<Attempt> findRecentInTopic(@Param("userId") Long userId, @Param("topicId") Long topicId, Pageable window);

    // Only timestamps: consistency needs the active days, not the attempts themselves.
    @Query("select a.submittedAt from Attempt a where a.user.id = :userId and a.submittedAt >= :since")
    List<LocalDateTime> findSubmissionTimesSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    // Used to skip already-solved problems in recommendations.
    @Query("select distinct a.problem.id from Attempt a where a.user.id = :userId and a.verdict = :verdict")
    List<Long> findProblemIdsWithVerdict(@Param("userId") Long userId, @Param("verdict") Verdict verdict);
}
