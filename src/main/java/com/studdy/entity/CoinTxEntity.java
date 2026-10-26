package com.studdy.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** 金币流水（不可变；docs/05 §4）。ref 存 JSON 字符串（srcId/refTxId/reason 等）。 */
@Entity
@Table(name = "coin_txs")
public class CoinTxEntity {

    @Id
    @Column(name = "id", length = 40)
    private String id;

    @Column(name = "profile_id", nullable = false, length = 40)
    private String profileId;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false)
    private int amount;

    @Column(name = "day_key", nullable = false, length = 10)
    private String dayKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(columnDefinition = "json")
    private String ref;

    /** 已被冲正标记：参与 dedup_key 生成列（冲正后原键位释放，允许同日重新勾选）。 */
    @Column(name = "revoked", columnDefinition = "tinyint", nullable = false)
    private Boolean revoked = false;

    // dedup_key 为数据库生成列（schema.sql），实体只读不映射，插入后由 DB 维护唯一约束。

    protected CoinTxEntity() {}

    public CoinTxEntity(String id, String profileId, String type, int amount,
                        String dayKey, Instant createdAt, String ref) {
        this.id = id;
        this.profileId = profileId;
        this.type = type;
        this.amount = amount;
        this.dayKey = dayKey;
        this.createdAt = createdAt;
        this.ref = ref;
    }

    public String getId() { return id; }
    public String getProfileId() { return profileId; }
    public String getType() { return type; }
    public int getAmount() { return amount; }
    public String getDayKey() { return dayKey; }
    public Instant getCreatedAt() { return createdAt; }
    public String getRef() { return ref; }
    public Boolean getRevoked() { return revoked; }
    public void setRevoked(Boolean v) { revoked = v; }
}
