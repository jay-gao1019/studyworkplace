package com.studdy.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "task_items")
public class TaskItemEntity {

    @Id
    @Column(name = "id", length = 40)
    private String id;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(length = 24)
    private String icon;

    @Column(nullable = false)
    private int amount;

    /** study | life（家务复用本表，docs/03 M2/M6-B） */
    @Column(nullable = false, length = 8)
    private String category;

    /** 'all' 或 '1,3,4' */
    @Column(name = "grade_scope", length = 32)
    private String gradeScope = "all";

    @Column(length = 4)
    private String term = "all";

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private int sort;

    protected TaskItemEntity() {}

    public TaskItemEntity(String id, String name, String icon, int amount, String category,
                          String gradeScope, String term, boolean enabled, int sort) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.amount = amount;
        this.category = category;
        this.gradeScope = gradeScope;
        this.term = term;
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
    public String getCategory() { return category; }
    public void setCategory(String v) { category = v; }
    public String getGradeScope() { return gradeScope; }
    public void setGradeScope(String v) { gradeScope = v; }
    public String getTerm() { return term; }
    public void setTerm(String v) { term = v; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { enabled = v; }
    public int getSort() { return sort; }
    public void setSort(int v) { sort = v; }
}
