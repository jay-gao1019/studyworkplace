package com.studdy.entity;

import jakarta.persistence.*;

/** 扣分快速面板预设（docs/03 M12，v2.3/FR-28）。amount 存正数，落账取负。 */
@Entity
@Table(name = "penalty_presets")
public class PenaltyPresetEntity {

    @Id
    @Column(length = 40)
    private String id;

    @Column(nullable = false, length = 24)
    private String name;

    @Column(length = 24)
    private String icon;

    @Column(nullable = false)
    private int amount;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private int sort = 0;

    protected PenaltyPresetEntity() {}

    public PenaltyPresetEntity(String id, String name, String icon, int amount, boolean enabled, int sort) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.amount = amount;
        this.enabled = enabled;
        this.sort = sort;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public String getIcon() { return icon; }
    public void setIcon(String v) { icon = v; }
    public int getAmount() { return amount; }
    public void setAmount(int v) { amount = v; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { enabled = v; }
    public int getSort() { return sort; }
    public void setSort(int v) { sort = v; }
}
