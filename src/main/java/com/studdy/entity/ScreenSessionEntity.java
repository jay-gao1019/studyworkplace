package com.studdy.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "screen_sessions")
public class ScreenSessionEntity {

    @Id
    @Column(name = "id", length = 40)
    private String id;

    @Column(name = "profile_id", nullable = false, length = 40)
    private String profileId;

    @Column(name = "day_key", nullable = false, length = 10)
    private String dayKey;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    @Column(name = "minutes_used", precision = 6, scale = 2)
    private java.math.BigDecimal minutesUsed;

    protected ScreenSessionEntity() {}

    public ScreenSessionEntity(String id, String profileId, String dayKey, Instant startAt) {
        this.id = id;
        this.profileId = profileId;
        this.dayKey = dayKey;
        this.startAt = startAt;
    }

    public String getId() { return id; }
    public String getProfileId() { return profileId; }
    public String getDayKey() { return dayKey; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public void setEndAt(Instant v) { endAt = v; }
    public java.math.BigDecimal getMinutesUsed() { return minutesUsed; }
    public void setMinutesUsed(java.math.BigDecimal v) { minutesUsed = v; }
}
