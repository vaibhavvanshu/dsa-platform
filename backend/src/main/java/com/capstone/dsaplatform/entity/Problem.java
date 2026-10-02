package com.capstone.dsaplatform.entity;

import com.capstone.dsaplatform.domain.Difficulty;
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
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "problems")
public class Problem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Exactly one topic per problem keeps retention/readiness attribution unambiguous.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, unique = true, length = 120)
    private String slug;

    // VARCHAR type code forced so Hibernate's H2 dialect doesn't expect a native ENUM column.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private Difficulty difficulty;

    @Column(nullable = false, length = 20000)
    private String statement;

    @Column(name = "input_format", nullable = false, length = 4000)
    private String inputFormat;

    @Column(name = "output_format", nullable = false, length = 4000)
    private String outputFormat;

    // Includes the stdin parsing boilerplate and an empty solve(), so the learner writes only the algorithm.
    @Column(name = "starter_code", nullable = false, length = 20000)
    private String starterCode;

    @Column(name = "time_limit_ms", nullable = false)
    private Integer timeLimitMs;

    // NULL until calibrated with the reference solution; while NULL, SUBOPTIMAL is never assigned.
    @Column(name = "optimal_runtime_ms")
    private Integer optimalRuntimeMs;

    @Column(name = "target_solve_seconds", nullable = false)
    private Integer targetSolveSeconds;

    protected Problem() {
        // Required by JPA.
    }

    public Long getId() { return id; }
    public Topic getTopic() { return topic; }
    public String getTitle() { return title; }
    public String getSlug() { return slug; }
    public Difficulty getDifficulty() { return difficulty; }
    public String getStatement() { return statement; }
    public String getInputFormat() { return inputFormat; }
    public String getOutputFormat() { return outputFormat; }
    public String getStarterCode() { return starterCode; }
    public Integer getTimeLimitMs() { return timeLimitMs; }
    public Integer getOptimalRuntimeMs() { return optimalRuntimeMs; }
    public Integer getTargetSolveSeconds() { return targetSolveSeconds; }
}
