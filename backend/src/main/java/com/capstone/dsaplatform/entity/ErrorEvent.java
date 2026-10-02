package com.capstone.dsaplatform.entity;

import com.capstone.dsaplatform.domain.ErrorCategory;
import com.capstone.dsaplatform.domain.TestTag;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * The diagnosis of one submission. At most one per attempt (first failure wins),
 * so a single bug that fails five tests counts once in the recurring-error profile.
 */
@Entity
@Table(name = "error_events")
public class ErrorEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false, unique = true)
    private Attempt attempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Denormalised from the problem so the weakness profile groups by topic without a join.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "error_category", nullable = false, length = 30)
    private ErrorCategory errorCategory;

    // NULL only for SYNTAX_COMPILATION, where no test ran.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "failed_tag", length = 10)
    private TestTag failedTag;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "failed_test_case_id")
    private TestCase failedTestCase;

    // Kept so an index exception can be shown as evidence for OFF_BY_ONE.
    @Column(name = "exception_type", length = 120)
    private String exceptionType;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    // Public because the submission service creates events (JPA also needs a no-arg constructor).
    public ErrorEvent() {
    }

    public Long getId() { return id; }

    public Attempt getAttempt() { return attempt; }
    public void setAttempt(Attempt attempt) { this.attempt = attempt; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Topic getTopic() { return topic; }
    public void setTopic(Topic topic) { this.topic = topic; }

    public ErrorCategory getErrorCategory() { return errorCategory; }
    public void setErrorCategory(ErrorCategory errorCategory) { this.errorCategory = errorCategory; }

    public TestTag getFailedTag() { return failedTag; }
    public void setFailedTag(TestTag failedTag) { this.failedTag = failedTag; }

    public TestCase getFailedTestCase() { return failedTestCase; }
    public void setFailedTestCase(TestCase failedTestCase) { this.failedTestCase = failedTestCase; }

    public String getExceptionType() { return exceptionType; }
    public void setExceptionType(String exceptionType) { this.exceptionType = exceptionType; }

    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
}
