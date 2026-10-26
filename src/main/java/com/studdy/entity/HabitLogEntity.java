package com.studdy.entity;

import jakarta.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "habit_logs")
@IdClass(HabitLogEntity.Pk.class)
public class HabitLogEntity {

    @Id
    @Column(name = "profile_id", length = 40)
    private String profileId;

    @Id
    @Column(name = "habit_id", length = 40)
    private String habitId;

    @Id
    @Column(name = "day_key", length = 10)
    private String dayKey;

    @Column(name = "chain_id", nullable = false, length = 12)
    private String chainId;

    @Column(nullable = false)
    private boolean done;

    public static class Pk implements Serializable {
        String profileId;
        String habitId;
        String dayKey;

        public Pk() {}

        public Pk(String profileId, String habitId, String dayKey) {
            this.profileId = profileId;
            this.habitId = habitId;
            this.dayKey = dayKey;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Pk pk)) return false;
            return java.util.Objects.equals(profileId, pk.profileId)
                    && java.util.Objects.equals(habitId, pk.habitId)
                    && java.util.Objects.equals(dayKey, pk.dayKey);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(profileId, habitId, dayKey);
        }
    }

    protected HabitLogEntity() {}

    public HabitLogEntity(String profileId, String habitId, String dayKey, String chainId, boolean done) {
        this.profileId = profileId;
        this.habitId = habitId;
        this.dayKey = dayKey;
        this.chainId = chainId;
        this.done = done;
    }

    public String getProfileId() { return profileId; }
    public String getHabitId() { return habitId; }
    public String getDayKey() { return dayKey; }
    public String getChainId() { return chainId; }
    public boolean isDone() { return done; }
    public void setDone(boolean v) { done = v; }
}
