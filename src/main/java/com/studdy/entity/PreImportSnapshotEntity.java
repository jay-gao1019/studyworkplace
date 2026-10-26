package com.studdy.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** 导入前整库快照（docs/05 §8 ADR-14）。 */
@Entity
@Table(name = "pre_import_snapshots")
public class PreImportSnapshotEntity {

    @Id
    @Column(name = "id", length = 40)
    private String id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false, columnDefinition = "longtext")
    private String payload;

    protected PreImportSnapshotEntity() {}

    public PreImportSnapshotEntity(String id, Instant createdAt, String payload) {
        this.id = id;
        this.createdAt = createdAt;
        this.payload = payload;
    }

    public String getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public String getPayload() { return payload; }
}
