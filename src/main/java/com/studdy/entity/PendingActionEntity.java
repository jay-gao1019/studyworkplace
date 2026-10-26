package com.studdy.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** 金币待审批申请（docs/03 M11，v2.3/FR-29）。active_day 为数据库生成列，实体不映射。 */
@Entity
@Table(name = "pending_actions")
public class PendingActionEntity {

    @Id
    @Column(length = 40)
    private String id;

    @Column(name = "profile_id", nullable = false, length = 40)
    private String profileId;

    /** TASK_CHECK/CHECKIN/TEXTBOOK_CHECK/HABIT_MILESTONE/MASTERY_REWARD/LIFE_CHECK/MANUAL_TX/PENALTY */
    @Column(nullable = false, length = 16)
    private String kind;

    @Column(name = "ref_id", nullable = false, length = 64)
    private String refId;

    @Column(nullable = false)
    private int amount;

    @Column(length = 120)
    private String reason;

    @Column(name = "day_key", nullable = false, length = 10)
    private String dayKey;

    /** pending | approved | rejected | withdrawn */
    @Column(nullable = false, length = 10)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "tx_id", length = 40)
    private String txId;

    /** v2.4/M13.5：佐证照片 id 逗号分隔（≤3 张） */
    @Column(name = "evidence_ids", length = 200)
    private String evidenceIds;

    protected PendingActionEntity() {}

    public PendingActionEntity(String id, String profileId, String kind, String refId,
                               int amount, String reason, String dayKey) {
        this.id = id;
        this.profileId = profileId;
        this.kind = kind;
        this.refId = refId;
        this.amount = amount;
        this.reason = reason;
        this.dayKey = dayKey;
        this.status = "pending";
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getProfileId() { return profileId; }
    public String getKind() { return kind; }
    public String getRefId() { return refId; }
    public int getAmount() { return amount; }
    public String getReason() { return reason; }
    public void setReason(String v) { reason = v; }
    public String getDayKey() { return dayKey; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { createdAt = v; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant v) { decidedAt = v; }
    public String getTxId() { return txId; }
    public void setTxId(String v) { txId = v; }
    public String getEvidenceIds() { return evidenceIds; }
    public void setEvidenceIds(String v) { evidenceIds = v; }
}
