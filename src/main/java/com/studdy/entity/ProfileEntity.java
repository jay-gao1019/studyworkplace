package com.studdy.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "profiles")
public class ProfileEntity {

    @Id
    @Column(name = "id", length = 40)
    private String id;

    @Column(nullable = false, length = 24)
    private String name;

    @Column(nullable = false, length = 24)
    private String avatar;

    @Column(nullable = false)
    private int grade;

    @Column(nullable = false, length = 2)
    private String term;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProfileEntity() {}

    public ProfileEntity(String id, String name, String avatar, int grade, String term, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.avatar = avatar;
        this.grade = grade;
        this.term = term;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public int getGrade() { return grade; }
    public void setGrade(int grade) { this.grade = grade; }
    public String getTerm() { return term; }
    public void setTerm(String term) { this.term = term; }
    public Instant getCreatedAt() { return createdAt; }
}
