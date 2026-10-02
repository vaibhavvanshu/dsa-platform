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
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "test_cases")
public class TestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    // The tag is what turns a failed test into a diagnosis (see CONTRACT.md section 4.2).
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private TestTag tag;

    @Column(nullable = false)
    private Integer ordinal;

    // Raw stdin fed to the learner's Main class.
    @Column(nullable = false, length = 1000000)
    private String input;

    @Column(name = "expected_output", nullable = false, length = 1000000)
    private String expectedOutput;

    // Curator override for tests whose meaning is more specific than their tag.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "error_category_override", length = 30)
    private ErrorCategory errorCategoryOverride;

    protected TestCase() {
        // Required by JPA.
    }

    public Long getId() { return id; }
    public Problem getProblem() { return problem; }
    public TestTag getTag() { return tag; }
    public Integer getOrdinal() { return ordinal; }
    public String getInput() { return input; }
    public String getExpectedOutput() { return expectedOutput; }
    public ErrorCategory getErrorCategoryOverride() { return errorCategoryOverride; }
}
