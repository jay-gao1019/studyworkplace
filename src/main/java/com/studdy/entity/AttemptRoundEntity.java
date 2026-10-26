package com.studdy.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "attempt_rounds")
public class AttemptRoundEntity {

    @Id
    @Column(name = "id", length = 40)
    private String id;

    @Column(name = "profile_id", nullable = false, length = 40)
    private String profileId;

    @Column(name = "leaf_id", nullable = false, length = 40)
    private String leafId;

    @Column(name = "quiz_set_hash", nullable = false, length = 64)
    private String quizSetHash;

    /** [{quizId,given,correct,at}] */
    @Column(nullable = false, columnDefinition = "json")
    private String answers;

    @Column(nullable = false, precision = 4, scale = 3)
    private java.math.BigDecimal rate;

    @Column(nullable = false, length = 12)
    private String mastery;

    @Column(name = "finished_at", nullable = false)
    private Instant finishedAt;

    protected AttemptRoundEntity() {}

    public AttemptRoundEntity(String id, String profileId, String leafId, String quizSetHash,
                              String answers, java.math.BigDecimal rate, String mastery, Instant finishedAt) {
        this.id = id;
        this.profileId = profileId;
        this.leafId = leafId;
        this.quizSetHash = quizSetHash;
        this.answers = answers;
        this.rate = rate;
        this.mastery = mastery;
        this.finishedAt = finishedAt;
    }

    public String getId() { return id; }
    public String getProfileId() { return profileId; }
    public String getLeafId() { return leafId; }
    public String getQuizSetHash() { return quizSetHash; }
    public String getAnswers() { return answers; }
    public java.math.BigDecimal getRate() { return rate; }
    public String getMastery() { return mastery; }
    public Instant getFinishedAt() { return finishedAt; }
}
