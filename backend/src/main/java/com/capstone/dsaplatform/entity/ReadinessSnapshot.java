package com.capstone.dsaplatform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Point-in-time overall readiness. Stored (unlike per-topic readiness) because the
 * history chart needs past values, and retention decay means they can't be recomputed later.
 */
@Entity
@Table(name = "readiness_snapshots")
public class ReadinessSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "taken_at", nullable = false)
    private LocalDateTime takenAt;

    // Mean of the per-topic ratings that are set (1..5); NULL if none are set.
    @Column(name = "overall_self_rating")
    private Double overallSelfRating;

    // 0..100; NULL if the learner has no attempts yet.
    @Column(name = "overall_computed")
    private Double overallComputed;

    // overall_self_rating * 20 - overall_computed; NULL if either side is NULL.
    @Column(name = "gap")
    private Double gap;

    // Public because the readiness service creates snapshots (JPA also needs a no-arg constructor).
    public ReadinessSnapshot() {
    }

    public Long getId() { return id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public LocalDateTime getTakenAt() { return takenAt; }
    public void setTakenAt(LocalDateTime takenAt) { this.takenAt = takenAt; }

    public Double getOverallSelfRating() { return overallSelfRating; }
    public void setOverallSelfRating(Double overallSelfRating) { this.overallSelfRating = overallSelfRating; }

    public Double getOverallComputed() { return overallComputed; }
    public void setOverallComputed(Double overallComputed) { this.overallComputed = overallComputed; }

    public Double getGap() { return gap; }
    public void setGap(Double gap) { this.gap = gap; }
}
