package com.studdy.entity;

import jakarta.persistence.*;

/** 自定义/导入题目（内置题目在 /seed 静态通道）。 */
@Entity
@Table(name = "quizzes")
public class QuizEntity {

    @Id
    @Column(name = "id", length = 40)
    private String id;

    @Column(name = "leaf_id", nullable = false, length = 40)
    private String leafId;

    @Column(nullable = false, length = 8)
    private String subject;

    /** single|blank */
    @Column(nullable = false, length = 8)
    private String type;

    @Column(nullable = false, length = 500)
    private String stem;

    /** [{"id":"A","text":"…"}] */
    @Column(columnDefinition = "json")
    private String options;

    @Column(nullable = false, length = 120)
    private String answer;

    @Column(name = "answer_accepts", columnDefinition = "json")
    private String answerAccepts;

    @Column(length = 800)
    private String explanation;

    @Column(nullable = false, length = 9)
    private String origin;

    protected QuizEntity() {}

    public QuizEntity(String id, String leafId, String subject, String type, String stem,
                      String options, String answer, String answerAccepts, String explanation, String origin) {
        this.id = id;
        this.leafId = leafId;
        this.subject = subject;
        this.type = type;
        this.stem = stem;
        this.options = options;
        this.answer = answer;
        this.answerAccepts = answerAccepts;
        this.explanation = explanation;
        this.origin = origin;
    }

    public String getId() { return id; }
    public String getLeafId() { return leafId; }
    public String getSubject() { return subject; }
    public String getType() { return type; }
    public String getStem() { return stem; }
    public void setStem(String v) { stem = v; }
    public String getOptions() { return options; }
    public String getAnswer() { return answer; }
    public String getAnswerAccepts() { return answerAccepts; }
    public String getExplanation() { return explanation; }
    public String getOrigin() { return origin; }
}
