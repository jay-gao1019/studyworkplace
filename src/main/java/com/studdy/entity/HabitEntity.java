package com.studdy.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "habits")
public class HabitEntity {

    @Id
    @Column(name = "id", length = 40)
    private String id;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(length = 24)
    private String icon;

    @Column(name = "cycle_days", nullable = false)
    private int cycleDays = 21;

    /** {"7":10,"14":20,"21":50} */
    @Column(name = "milestone_rewards", columnDefinition = "json")
    private String milestoneRewards;

    @Column(nullable = false)
    private boolean enabled = true;

    protected HabitEntity() {}

    public HabitEntity(String id, String name, String icon, int cycleDays, String milestoneRewards, boolean enabled) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.cycleDays = cycleDays;
        this.milestoneRewards = milestoneRewards;
        this.enabled = enabled;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public String getIcon() { return icon; }
    public void setIcon(String v) { icon = v; }
    public int getCycleDays() { return cycleDays; }
    public void setCycleDays(int v) { cycleDays = v; }
    public String getMilestoneRewards() { return milestoneRewards; }
    public void setMilestoneRewards(String v) { milestoneRewards = v; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { enabled = v; }
}
