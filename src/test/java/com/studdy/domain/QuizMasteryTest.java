package com.studdy.domain;

import com.studdy.domain.mastery.Mastery;
import com.studdy.domain.mastery.MasteryEngine;
import com.studdy.domain.quiz.GraderEngine;
import com.studdy.domain.quiz.Quiz;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuizMasteryTest {

    private static Quiz single() {
        return Quiz.single("q1", "f1", "1+1=?",
                List.of(new Quiz.Option("A", "1"), new Quiz.Option("B", "2"),
                        new Quiz.Option("C", "3"), new Quiz.Option("D", "4")), "B", "略");
    }

    private static Quiz blank() {
        return Quiz.blank("q2", "f1", "写数：三百零五万零四十", "3050040", List.of("3050040"), "略");
    }

    @Test
    void blankEqualsNormalized() {
        assertTrue(GraderEngine.gradeBlank(blank(), "３０５００４０")); // 全角
        assertTrue(GraderEngine.gradeBlank(blank(), " 3050040 "));      // 空格
        assertFalse(GraderEngine.gradeBlank(blank(), "305004"));
        assertFalse(GraderEngine.gradeBlank(blank(), ""));
    }

    @Test
    void answerAcceptsAny() {
        Quiz q = Quiz.blank("q3", "f1", "苹果的英文", "apple", List.of("an apple"), "略");
        assertTrue(GraderEngine.grade(q, "Apple"));       // 规范化+任一可接受
        assertTrue(GraderEngine.grade(q, "an apple"));
        assertFalse(GraderEngine.grade(q, "apples"));
        assertEquals(2, q.acceptedAnswers().size());
    }

    @Test
    void hashStable() {
        List<Quiz> a = List.of(single(), blank());
        List<Quiz> b = List.of(blank(), single()); // 乱序同集合
        assertEquals(GraderEngine.quizSetHash(a), GraderEngine.quizSetHash(b));
        List<Quiz> c = List.of(single(),
                Quiz.blank("q9", "f1", "改题干", "1", List.of(), "改题"));
        assertNotEquals(GraderEngine.quizSetHash(a), GraderEngine.quizSetHash(c));
    }

    @Test
    void masteryBoundaries0809() {
        assertEquals(Mastery.PROFICIENT, MasteryEngine.fromRate(0.9));  // 含下限归高档
        assertEquals(Mastery.MASTERED, MasteryEngine.fromRate(0.8));
        assertEquals(Mastery.MASTERED, MasteryEngine.fromRate(0.899));
        assertEquals(Mastery.PARTIAL, MasteryEngine.fromRate(0.799));
        assertEquals(Mastery.PARTIAL, MasteryEngine.fromRate(0.3));
        assertEquals(Mastery.WEAK, MasteryEngine.fromRate(0.299));
    }

    @Test
    void hashMismatchRetest() {
        String cur = "hashA";
        assertEquals(Mastery.UNANSWERED, MasteryEngine.displayState(null, cur, 0));
        assertEquals(Mastery.NEEDS_RETEST, MasteryEngine.displayState("hashOld", cur, 1.0));
        assertEquals(Mastery.PROFICIENT, MasteryEngine.displayState(cur, cur, 0.95));
    }

    @Test
    void statsCalc() {
        var s = MasteryEngine.stats(3, 10,
                List.of(Mastery.PROFICIENT, Mastery.WEAK, Mastery.UNANSWERED));
        assertEquals(3, s.leafCount());
        assertEquals(2, s.answeredLeaves());
        assertEquals(0.5, s.proficientPlusMasteredRatio(), 1e-9);
    }
}
