package com.studdy.domain.coin;

import java.util.List;

/** 等级规则（docs/03 M1.4）：按累计金币（只增不减）判级，只升不降。 */
public record LevelRule(int[] thresholds, String[] names) {

    public static final LevelRule DEFAULT = new LevelRule(
            new int[]{0, 200, 600, 1500, 3000},
            new String[]{"小嫩芽", "绿幼苗", "向阳花", "知识树", "小学霸"});

    public int levelIndex(int totalEarned) {
        int idx = 0;
        for (int i = 0; i < thresholds.length; i++) {
            if (totalEarned >= thresholds[i]) {
                idx = i;
            }
        }
        return idx;
    }

    public String levelName(int totalEarned) {
        return names[levelIndex(totalEarned)];
    }

    /** 升级到 next 时返回新等级名；未跨阈值返回 null。M1.4：等级不可回落。 */
    public String promote(int prevTotal, int newTotal) {
        int prev = levelIndex(Math.max(0, prevTotal));
        int now = levelIndex(Math.max(0, newTotal));
        return now > prev ? names[now] : null;
    }

    /** 距下一级还差多少金币；已满级返回 -1。 */
    public int toNextLevel(int totalEarned) {
        int idx = levelIndex(totalEarned);
        if (idx >= thresholds.length - 1) {
            return -1;
        }
        return thresholds[idx + 1] - totalEarned;
    }
}
