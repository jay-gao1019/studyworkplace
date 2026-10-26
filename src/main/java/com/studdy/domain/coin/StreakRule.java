package com.studdy.domain.coin;

import com.studdy.domain.util.DayKey;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 连续打卡（docs/03 M1.5）：当日存在 ≥1 条"活动流水"即打卡日；断签归零。 */
public final class StreakRule {

    private StreakRule() {}

    /** 打卡日集合：MANUAL 仅正数计入（M1.5 排除惩罚性扣分）。 */
    public static Set<String> checkinDays(List<CoinTx> txs) {
        Set<String> days = new HashSet<>();
        for (CoinTx t : txs) {
            boolean counts = t.type().countsAsActivity() && !(t.type() == TxType.MANUAL && t.amount() < 0);
            if (counts) {
                days.add(t.dayKey());
            }
        }
        return days;
    }

    /** 截至 today 的连续天数；今天未打卡则从昨天往前数（显示"昨天为止"的值）。 */
    public static int streak(Set<String> checkinDays, String today) {
        LocalDate cursor = DayKey.parse(today);
        if (!checkinDays.contains(DayKey.of(cursor))) {
            cursor = cursor.minusDays(1);
            if (!checkinDays.contains(DayKey.of(cursor))) {
                return 0;
            }
        }
        int n = 0;
        while (checkinDays.contains(DayKey.of(cursor))) {
            n++;
            cursor = cursor.minusDays(1);
        }
        return n;
    }
}
