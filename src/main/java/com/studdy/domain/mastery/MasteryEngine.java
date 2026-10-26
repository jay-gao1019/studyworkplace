package com.studdy.domain.mastery;

import java.util.List;

/**
 * 掌握度引擎（docs/03 M5.4–5.7）。
 * 分级：rate≥0.9 精通；0.8≤rate<0.9 掌握；0.3≤rate<0.8 部分掌握；rate<0.3 未掌握（边界含下限）。
 */
public final class MasteryEngine {

    private MasteryEngine() {}

    public static Mastery fromRate(double rate) {
        if (rate >= 0.9) {
            return Mastery.PROFICIENT;
        }
        if (rate >= 0.8) {
            return Mastery.MASTERED;
        }
        if (rate >= 0.3) {
            return Mastery.PARTIAL;
        }
        return Mastery.WEAK;
    }

    /**
     * 节点展示态：无有效轮次→未答；有最近轮次但 hash 与当前题目集不符→待重测。
     * @param latestRoundHash 该档案该叶子最近一轮的 quizSetHash（可为 null）
     * @param currentHash     当前题目集指纹
     */
    public static Mastery displayState(String latestRoundHash, String currentHash, double latestRate) {
        if (latestRoundHash == null) {
            return Mastery.UNANSWERED;
        }
        if (!latestRoundHash.equals(currentHash)) {
            return Mastery.NEEDS_RETEST;
        }
        return fromRate(latestRate);
    }

    /** M5.7 统计四项中的"已答知识点数"与"精通+掌握占比"（分母=有效轮次节点数）。 */
    public record Stats(int leafCount, int nodeCount, int answeredLeaves, double proficientPlusMasteredRatio) {}

    public static Stats stats(int leafCount, int nodeCount, List<Mastery> leafMasters) {
        int effective = 0;
        int good = 0;
        int answered = 0;
        for (Mastery m : leafMasters) {
            if (m == Mastery.PROFICIENT || m == Mastery.MASTERED || m == Mastery.PARTIAL || m == Mastery.WEAK) {
                effective++;
                answered++;
                if (m == Mastery.PROFICIENT || m == Mastery.MASTERED) {
                    good++;
                }
            }
        }
        double ratio = effective == 0 ? 0 : (double) good / effective;
        return new Stats(leafCount, nodeCount, answered, ratio);
    }
}
