package com.studdy.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "redemptions")
public class RedemptionEntity {

    @Id
    @Column(name = "id", length = 40)
    private String id;

    @Column(name = "profile_id", nullable = false, length = 40)
    private String profileId;

    /** 商品快照（M3 边界：删除商品不影响历史记录） */
    @Column(name = "item_name", nullable = false, length = 60)
    private String itemName;

    @Column(nullable = false)
    private int price;

    @Column(length = 24)
    private String icon;

    /** pending | redeemed | cancelled */
    @Column(nullable = false, length = 12)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "redeemed_at")
    private Instant redeemedAt;

    @Column(name = "tx_id", nullable = false, length = 40)
    private String txId;

    protected RedemptionEntity() {}

    public RedemptionEntity(String id, String profileId, String itemName, int price,
                            String icon, String status, Instant createdAt, String txId) {
        this.id = id;
        this.profileId = profileId;
        this.itemName = itemName;
        this.price = price;
        this.icon = icon;
        this.status = status;
        this.createdAt = createdAt;
        this.txId = txId;
    }

    public String getId() { return id; }
    public String getProfileId() { return profileId; }
    public String getItemName() { return itemName; }
    public int getPrice() { return price; }
    public String getIcon() { return icon; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getRedeemedAt() { return redeemedAt; }
    public void setRedeemedAt(Instant v) { redeemedAt = v; }
    public String getTxId() { return txId; }
}
