package com.studdy.domain.quiz;

import java.util.List;
import java.util.Map;

/** 自测题领域对象（docs/05 §6；answer 永不随答题接口下发，docs/02 §5）。 */
public record Quiz(
        String id,
        String leafId,
        Type type,
        String stem,
        List<Option> options,
        String answer,
        List<String> answerAccepts,
        String explanation) {

    public enum Type { SINGLE, BLANK }

    public record Option(String id, String text) {}

    /** 有效可接受答案集合：answer + answerAccepts（去重规范化由 GraderEngine 做）。 */
    public List<String> acceptedAnswers() {
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        if (answer != null) {
            set.add(answer);
        }
        if (answerAccepts != null) {
            set.addAll(answerAccepts);
        }
        return List.copyOf(set);
    }

    public static Quiz single(String id, String leafId, String stem, List<Option> options, String answer, String explanation) {
        return new Quiz(id, leafId, Type.SINGLE, stem, options, answer, List.of(), explanation);
    }

    public static Quiz blank(String id, String leafId, String stem, String answer, List<String> accepts, String explanation) {
        return new Quiz(id, leafId, Type.BLANK, stem, List.of(), answer, accepts, explanation);
    }
}
