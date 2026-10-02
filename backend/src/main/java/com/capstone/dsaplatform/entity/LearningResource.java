package com.capstone.dsaplatform.entity;

import com.capstone.dsaplatform.domain.ErrorCategory;
import com.capstone.dsaplatform.domain.ResourceSource;
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

/**
 * A curated GfG/YouTube link. Named LearningResource (table "resources") to avoid
 * clashing with Spring's org.springframework.core.io.Resource in imports.
 * Rows come only from a hand-written resources.sql, so every URL here is human-verified.
 */
@Entity
@Table(name = "resources")
public class LearningResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // NULL means "generic link for this error category, any topic".
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id")
    private Topic topic;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "error_category", nullable = false, length = 30)
    private ErrorCategory errorCategory;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 500)
    private String url;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private ResourceSource source;

    // Lower rank is shown first.
    @Column(nullable = false)
    private int rank;

    protected LearningResource() {
        // Required by JPA.
    }

    public Long getId() { return id; }
    public Topic getTopic() { return topic; }
    public ErrorCategory getErrorCategory() { return errorCategory; }
    public String getTitle() { return title; }
    public String getUrl() { return url; }
    public ResourceSource getSource() { return source; }
    public int getRank() { return rank; }
}
