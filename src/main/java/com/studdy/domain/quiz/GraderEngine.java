package com.studdy.domain.quiz;

import com.studdy.domain.util.TextUtil;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * 判分与题目集指纹（docs/03 M5.1/5.2/5.6）。
 */
public final class GraderEngine {

    private GraderEngine() {}

    /** M5.1 单选：选项 id 精确匹配。 */
    public static boolean gradeSingle(Quiz quiz, String givenOptionId) {
        return givenOptionId != null && givenOptionId.equals(quiz.answer());
    }

    /** M5.2 填空：规范化（trim/全半角/空白折叠）后与任一可接受答案全等。 */
    public static boolean gradeBlank(Quiz quiz, String given) {
        String g = TextUtil.normalize(given);
        if (g.isEmpty()) {
            return false;
        }
        return quiz.acceptedAnswers().stream().anyMatch(a -> TextUtil.normalize(a).equals(g));
    }

    public static boolean grade(Quiz quiz, String given) {
        return quiz.type() == Quiz.Type.SINGLE ? gradeSingle(quiz, given) : gradeBlank(quiz, given);
    }

    /** M5.6 quizSetHash = SHA-256(按 id 排序的 "id|stem|answer" 连接)。 */
    public static String quizSetHash(List<Quiz> quizzes) {
        String joined = quizzes.stream()
                .map(q -> q.id() + "|" + TextUtil.normalize(q.stem()) + "|" + TextUtil.normalize(q.answer()))
                .sorted()
                .reduce("", (a, b) -> a + "\n" + b);
        return sha256Hex(joined);
    }

    public static String sha256Hex(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
