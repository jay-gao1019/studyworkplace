package com.studdy.entity;

import jakarta.persistence.*;

/** AdminConfig 单例（code='singleton'）；JSON 段以字符串存，service 层用 Jackson 解析（docs/05 §3）。 */
@Entity
@Table(name = "settings")
public class SettingsEntity {

    @Id
    @Column(name = "code", length = 20)
    private String code = "singleton";

    @Column(name = "pin_hash", length = 120)
    private String pinHash;

    @Column(name = "level_thresholds", columnDefinition = "json")
    private String levelThresholds;

    @Column(name = "level_names", columnDefinition = "json")
    private String levelNames;

    @Column(name = "checkin_amount")
    private int checkinAmount = 20;

    @Column(name = "textbook_coin_value")
    private int textbookCoinValue = 5;

    @Column(name = "life_settings", columnDefinition = "json")
    private String lifeSettings;

    protected SettingsEntity() {}

    public SettingsEntity(String code) {
        this.code = code;
    }

    public String getCode() { return code; }
    public String getPinHash() { return pinHash; }
    public void setPinHash(String pinHash) { this.pinHash = pinHash; }
    public String getLevelThresholds() { return levelThresholds; }
    public void setLevelThresholds(String v) { this.levelThresholds = v; }
    public String getLevelNames() { return levelNames; }
    public void setLevelNames(String v) { this.levelNames = v; }
    public int getCheckinAmount() { return checkinAmount; }
    public void setCheckinAmount(int v) { this.checkinAmount = v; }
    public int getTextbookCoinValue() { return textbookCoinValue; }
    public void setTextbookCoinValue(int v) { this.textbookCoinValue = v; }
    public String getLifeSettings() { return lifeSettings; }
    public void setLifeSettings(String v) { this.lifeSettings = v; }
}
