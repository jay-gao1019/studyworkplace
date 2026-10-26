package com.studdy.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;

/** 教材条目打卡（docs/05 §5）：主键 (profile, nodeId)——教材是长期进度不按日重置。 */
@Entity
@Table(name = "textbook_checks")
@IdClass(TextbookCheckEntity.Pk.class)
public class TextbookCheckEntity {

    @Id
    @Column(name = "profile_id", length = 40)
    private String profileId;

    @Id
    @Column(name = "node_id", length = 40)
    private String nodeId;

    @Column(name = "day_key", nullable = false, length = 10)
    private String dayKey;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    @Column(name = "tx_id", length = 40)
    private String txId;

    public static class Pk implements Serializable {
        String profileId;
        String nodeId;

        public Pk() {}

        public Pk(String profileId, String nodeId) {
            this.profileId = profileId;
            this.nodeId = nodeId;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Pk pk)) return false;
            return java.util.Objects.equals(profileId, pk.profileId)
                    && java.util.Objects.equals(nodeId, pk.nodeId);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(profileId, nodeId);
        }
    }

    protected TextbookCheckEntity() {}

    public TextbookCheckEntity(String profileId, String nodeId, String dayKey, Instant checkedAt, String txId) {
        this.profileId = profileId;
        this.nodeId = nodeId;
        this.dayKey = dayKey;
        this.checkedAt = checkedAt;
        this.txId = txId;
    }

    public String getProfileId() { return profileId; }
    public String getNodeId() { return nodeId; }
    public String getDayKey() { return dayKey; }
    public Instant getCheckedAt() { return checkedAt; }
    public String getTxId() { return txId; }
}
