package com.studdy.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "screen_banks")
public class ScreenBankEntity {

    @Id
    @Column(name = "profile_id", length = 40)
    private String profileId;

    @Column(name = "granted_minutes", nullable = false)
    private int grantedMinutes;

    @Column(name = "consumed_minutes", nullable = false)
    private int consumedMinutes;

    protected ScreenBankEntity() {}

    public ScreenBankEntity(String profileId, int grantedMinutes, int consumedMinutes) {
        this.profileId = profileId;
        this.grantedMinutes = grantedMinutes;
        this.consumedMinutes = consumedMinutes;
    }

    public String getProfileId() { return profileId; }
    public int getGrantedMinutes() { return grantedMinutes; }
    public void setGrantedMinutes(int v) { grantedMinutes = v; }
    public int getConsumedMinutes() { return consumedMinutes; }
    public void setConsumedMinutes(int v) { consumedMinutes = v; }

    public int available() {
        return Math.max(0, grantedMinutes - consumedMinutes);
    }
}
