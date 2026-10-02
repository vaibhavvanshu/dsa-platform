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
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * The persistent learner model for one (user, topic): exactly the inputs HLR needs,
 * plus the learner's own rating. Retention itself is NOT stored because it changes
 * every second; it is computed from half_life_days and last_practiced_at on request.
 */
@Entity
@Table(name = "topic_state",
        uniqueConstraints = @UniqueConstraint(name = "uq_topic_state_user_topic",
                columnNames = {"user_id", "topic_id"}))
public class TopicState {

    // Surrogate key (with a unique pair) is simpler to map in JPA than a composite key.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @Column(name = "correct_count", nullable = false)
    private int correctCount;

    @Column(name = "wrong_count", nullable = false)
    private int wrongCount;

    // NULL until the first attempt: a mean over zero attempts is undefined.
    @Column(name = "mean_difficulty")
    private Double meanDifficulty;

    @Column(name = "last_practiced_at")
    private LocalDateTime lastPracticedAt;

    @Column(name = "half_life_days")
    private Double halfLifeDays;

    // 1..5, NULL until the learner rates this topic.
    @Column(name = "self_rating")
    private Integer selfRating;

    // Public because rows are created lazily by services (JPA also needs a no-arg constructor).
    public TopicState() {
    }

    public Long getId() { return id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Topic getTopic() { return topic; }
    public void setTopic(Topic topic) { this.topic = topic; }

    public int getCorrectCount() { return correctCount; }
    public void setCorrectCount(int correctCount) { this.correctCount = correctCount; }

    public int getWrongCount() { return wrongCount; }
    public void setWrongCount(int wrongCount) { this.wrongCount = wrongCount; }

    public Double getMeanDifficulty() { return meanDifficulty; }
    public void setMeanDifficulty(Double meanDifficulty) { this.meanDifficulty = meanDifficulty; }

    public LocalDateTime getLastPracticedAt() { return lastPracticedAt; }
    public void setLastPracticedAt(LocalDateTime lastPracticedAt) { this.lastPracticedAt = lastPracticedAt; }

    public Double getHalfLifeDays() { return halfLifeDays; }
    public void setHalfLifeDays(Double halfLifeDays) { this.halfLifeDays = halfLifeDays; }

    public Integer getSelfRating() { return selfRating; }
    public void setSelfRating(Integer selfRating) { this.selfRating = selfRating; }
}
