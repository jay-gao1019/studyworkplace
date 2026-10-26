package com.studdy.entity;

import jakarta.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "habit_chains")
@IdClass(HabitChainEntity.Pk.class)
public class HabitChainEntity {

    @Id
    @Column(name = "profile_id", length = 40)
    private String profileId;

    @Id
    @Column(name = "habit_id", length = 40)
    private String habitId;

    @Id
    @Column(name = "chain_id", length = 12)
    private String chainId;

    @Column(nullable = false)
    private int consecutive;

    @Column(name = "started_at", nullable = false, length = 10)
    private String startedAt;

    /** [7,14] 已发里程碑 */
    @Column(name = "awarded_milestones", columnDefinition = "json")
    private String awardedMilestones;

    public static class Pk implements Serializable {
        String profileId;
        String habitId;
        String chainId;

        public Pk() {}

        public Pk(String profileId, String habitId, String chainId) {
            this.profileId = profileId;
            this.habitId = habitId;
            this.chainId = chainId;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Pk pk)) return false;
            return java.util.Objects.equals(profileId, pk.profileId)
                    && java.util.Objects.equals(habitId, pk.habitId)
                    && java.util.Objects.equals(chainId, pk.chainId);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(profileId, habitId, chainId);
        }
    }

    protected HabitChainEntity() {}

    public HabitChainEntity(String profileId, String habitId, String chainId,
                            int consecutive, String startedAt, String awardedMilestones) {
        this.profileId = profileId;
        this.habitId = habitId;
        this.chainId = chainId;
        this.consecutive = consecutive;
        this.startedAt = startedAt;
        this.awardedMilestones = awardedMilestones;
    }

    public String getProfileId() { return profileId; }
    public String getHabitId() { return habitId; }
    public String getChainId() { return chainId; }
    public int getConsecutive() { return consecutive; }
    public void setConsecutive(int v) { consecutive = v; }
    public String getStartedAt() { return startedAt; }
    public String getAwardedMilestones() { return awardedMilestones; }
    public void setAwardedMilestones(String v) { awardedMilestones = v; }
}
