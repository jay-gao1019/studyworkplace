package com.studdy.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "sleep_logs")
@IdClass(SleepLogEntity.Pk.class)
public class SleepLogEntity {

    @Id
    @Column(name = "profile_id", length = 40)
    private String profileId;

    @Id
    @Column(name = "day_key", length = 10)
    private String dayKey;

    @Id
    @Column(length = 4)
    private String kind;

    @Column(name = "actual_at", nullable = false)
    private Instant actualAt;

    @Column(name = "on_time", nullable = false)
    private boolean onTime;

    @Column(name = "tx_id", length = 40)
    private String txId;

    public static class Pk implements Serializable {
        String profileId;
        String dayKey;
        String kind;

        public Pk() {}

        public Pk(String profileId, String dayKey, String kind) {
            this.profileId = profileId;
            this.dayKey = dayKey;
            this.kind = kind;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Pk pk)) return false;
            return java.util.Objects.equals(profileId, pk.profileId)
                    && java.util.Objects.equals(dayKey, pk.dayKey)
                    && java.util.Objects.equals(kind, pk.kind);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(profileId, dayKey, kind);
        }
    }

    protected SleepLogEntity() {}

    public SleepLogEntity(String profileId, String dayKey, String kind,
                          Instant actualAt, boolean onTime, String txId) {
        this.profileId = profileId;
        this.dayKey = dayKey;
        this.kind = kind;
        this.actualAt = actualAt;
        this.onTime = onTime;
        this.txId = txId;
    }

    public String getProfileId() { return profileId; }
    public String getDayKey() { return dayKey; }
    public String getKind() { return kind; }
    public Instant getActualAt() { return actualAt; }
    public boolean isOnTime() { return onTime; }
    public String getTxId() { return txId; }
}
