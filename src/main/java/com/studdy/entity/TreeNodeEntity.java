package com.studdy.entity;

import jakarta.persistence.*;

/** 自定义/导入树节点（内置结构走 /seed 静态通道，docs/02 ADR-13）。 */
@Entity
@Table(name = "tree_nodes")
public class TreeNodeEntity {

    @Id
    @Column(name = "id", length = 40)
    private String id;

    @Column(nullable = false, length = 8)
    private String subject;

    @Column(nullable = false)
    private int grade;

    @Column(nullable = false, length = 2)
    private String term;

    @Column(name = "parent_ref", length = 40)
    private String parentRef;

    /** module|unit|leaf */
    @Column(nullable = false, length = 8)
    private String kind;

    @Column(nullable = false, length = 80)
    private String title;

    @Column(nullable = false)
    private int sort;

    @Column(length = 8)
    private String branch;

    /** custom|imported */
    @Column(nullable = false, length = 9)
    private String origin;

    protected TreeNodeEntity() {}

    public TreeNodeEntity(String id, String subject, int grade, String term, String parentRef,
                          String kind, String title, int sort, String branch, String origin) {
        this.id = id;
        this.subject = subject;
        this.grade = grade;
        this.term = term;
        this.parentRef = parentRef;
        this.kind = kind;
        this.title = title;
        this.sort = sort;
        this.branch = branch;
        this.origin = origin;
    }

    public String getId() { return id; }
    public String getSubject() { return subject; }
    public int getGrade() { return grade; }
    public String getTerm() { return term; }
    public String getParentRef() { return parentRef; }
    public void setParentRef(String v) { parentRef = v; }
    public String getKind() { return kind; }
    public String getTitle() { return title; }
    public void setTitle(String v) { title = v; }
    public int getSort() { return sort; }
    public void setSort(int v) { sort = v; }
    public String getBranch() { return branch; }
    public String getOrigin() { return origin; }
}
