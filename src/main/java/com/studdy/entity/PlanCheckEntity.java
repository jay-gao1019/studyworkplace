package com.studdy.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "plan_checks")
@IdClass(PlanCheckEntity.Pk.class)
public class PlanCheckEntity {

    @Id
    @Column(name = "profile_id", length = 40)
    private String profileId;

    @Id
    @Column(name = "task_item_id", length = 40)
    private String taskItemId;

    @Id
    @Column(name = "day_key", length = 10)
    private String dayKey;

    /** done | undone */
    @Column(nullable = false, length = 8)
    private String status;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    @Column(name = "tx_id", length = 40)
    private String txId;

    @Column(name = "reverse_tx_id", length = 40)
    private String reverseTxId;

    public static class Pk implements Serializable {
        String profileId;
        String taskItemId;
        String dayKey;

        public Pk() {}

        public Pk(String profileId, String taskItemId, String dayKey) {
            this.profileId = profileId;
            this.taskItemId = taskItemId;
            this.dayKey = dayKey;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Pk pk)) return false;
            return java.util.Objects.equals(profileId, pk.profileId)
                    && java.util.Objects.equals(taskItemId, pk.taskItemId)
                    && java.util.Objects.equals(dayKey, pk.dayKey);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(profileId, taskItemId, dayKey);
        }
    }

    protected PlanCheckEntity() {}

    public PlanCheckEntity(String profileId, String taskItemId, String dayKey,
                           String status, Instant checkedAt, String txId, String reverseTxId) {
        this.profileId = profileId;
        this.taskItemId = taskItemId;
        this.dayKey = dayKey;
        this.status = status;
        this.checkedAt = checkedAt;
        this.txId = txId;
        this.reverseTxId = reverseTxId;
    }

    public String getProfileId() { return profileId; }
    public String getTaskItemId() { return taskItemId; }
    public String getDayKey() { return dayKey; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public Instant getCheckedAt() { return checkedAt; }
    public void setCheckedAt(Instant v) { checkedAt = v; }
    public String getTxId() { return txId; }
    public void setTxId(String v) { txId = v; }
    public String getReverseTxId() { return reverseTxId; }
    public void setReverseTxId(String v) { reverseTxId = v; }
}
