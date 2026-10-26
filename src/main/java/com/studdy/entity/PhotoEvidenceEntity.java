package com.studdy.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** 佐证照片元数据（docs/03 M13，v2.4/FR-32）。文件本体在 data/evidence，表只记水印成品路径与备注。 */
@Entity
@Table(name = "photo_evidences")
public class PhotoEvidenceEntity {

    @Id
    @Column(length = 40)
    private String id;

    @Column(name = "profile_id", nullable = false, length = 40)
    private String profileId;

    /** task/textbook/manual/penalty/checkin/life/habit 等佐证场景 */
    @Column(nullable = false, length = 24)
    private String kind;

    @Column(name = "file_path", length = 200)
    private String filePath;

    @Column(name = "orig_name", length = 120)
    private String origName;

    @Column(length = 120)
    private String note;

    @Column(name = "taken_at", nullable = false)
    private Instant takenAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PhotoEvidenceEntity() {}

    public PhotoEvidenceEntity(String id, String profileId, String kind, String filePath,
                               String origName, String note, Instant takenAt) {
        this.id = id;
        this.profileId = profileId;
        this.kind = kind;
        this.filePath = filePath;
        this.origName = origName;
        this.note = note;
        this.takenAt = takenAt;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getProfileId() { return profileId; }
    public String getKind() { return kind; }
    public String getFilePath() { return filePath; }
    public String getOrigName() { return origName; }
    public String getNote() { return note; }
    public Instant getTakenAt() { return takenAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { createdAt = v; }
}
