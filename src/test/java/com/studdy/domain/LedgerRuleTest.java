package com.studdy.domain;

import com.studdy.domain.coin.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LedgerRuleTest {

    private static CoinTx tx(String id, TxType type, int amount, String day, String srcId, String refTxId) {
        return new CoinTx(id, "p1", type, amount, day, LocalDateTime.now(), srcId, refTxId, null);
    }

    @Test
    void ledgerRecompute() {
        List<CoinTx> txs = List.of(
                tx("t1", TxType.TASK, 5, "2026-10-25", "read30", null),
                tx("t2", TxType.TASK, -10, "2026-10-25", "homework_late", null),
                tx("t3", TxType.REDEEM, -60, "2026-10-25", "shop cartoon", null));
        assertEquals(-65, LedgerRule.balance(txs));
        assertEquals(5, LedgerRule.totalEarned(txs));
    }

    @Test
    void doubleReverseReject() {
        CoinTx origin = tx("t1", TxType.TASK, 5, "2026-10-25", "read30", null);
        CoinTx rev = tx("t2", TxType.REVERSE, -5, "2026-10-25", null, "t1");
        List<CoinTx> all = List.of(origin, rev);
        assertTrue(LedgerRule.isReversed("t1", all));
        assertThrows(IllegalStateException.class,
                () -> LedgerRule.reverseOf(origin, all, "t3", LocalDateTime.now(), "2026-10-25"));
        // 冲正流水本身不可再冲正
        assertThrows(IllegalStateException.class,
                () -> LedgerRule.reverseOf(rev, all, "t4", LocalDateTime.now(), "2026-10-25"));
    }

    @Test
    void dedupConflictDetect() {
        CoinTx a = tx("t1", TxType.TASK, 5, "2026-10-25", "read30", null);
        CoinTx b = tx("t2", TxType.TASK, 5, "2026-10-25", "read30", null);
        List<CoinTx> all = List.of(a);
        assertTrue(LedgerRule.dedupConflict(b, all));
        // 已冲正后允许再勾
        CoinTx rev = tx("r1", TxType.REVERSE, -5, "2026-10-25", null, "t1");
        assertFalse(LedgerRule.dedupConflict(b, List.of(a, rev)));
        // 跨日不冲突
        CoinTx nextDay = tx("t3", TxType.TASK, 5, "2026-10-26", "read30", null);
        assertFalse(LedgerRule.dedupConflict(nextDay, all));
    }

    @Test
    void bucketFillZero() {
        List<CoinTx> txs = List.of(
                tx("t1", TxType.TASK, 5, "2026-10-24", "a", null),
                tx("t2", TxType.REDEEM, -60, "2026-10-26", "b", null));
        var buckets = LedgerRule.aggregate(txs,
                List.of("2026-10-24", "2026-10-25", "2026-10-26"),
                d -> d);
        assertEquals(3, buckets.size());
        assertEquals(5, buckets.get(0).income());
        assertEquals(0, buckets.get(1).income());
        assertEquals(0, buckets.get(1).expense());
        assertEquals(-60, buckets.get(2).expense());
    }

    @Test
    void levelBoundaries() {
        LevelRule r = LevelRule.DEFAULT;
        assertEquals("小嫩芽", r.levelName(199));
        assertEquals("绿幼苗", r.levelName(200));   // 阈值含下限
        assertEquals("向阳花", r.levelName(600));
        assertEquals("知识树", r.levelName(1500));
        assertEquals("小学霸", r.levelName(3000));
        assertEquals("小学霸", r.levelName(999999));
        assertEquals("绿幼苗", r.promote(199, 200));
        assertNull(r.promote(250, 300));            // 同级不播报
        assertNull(r.promote(300, 250));            // 累计减少不降级（M1.4）
        assertEquals(100, r.toNextLevel(100));
        assertEquals(-1, r.toNextLevel(3000));
    }

    @Test
    void streakBreakZero() {
        List<CoinTx> txs = List.of(
                tx("t1", TxType.TASK, 5, "2026-10-22", "a", null),
                tx("t2", TxType.TASK, 5, "2026-10-23", "a", null),
                tx("t3", TxType.TASK, 5, "2026-10-25", "a", null));
        var days = StreakRule.checkinDays(txs);
        assertEquals(2, StreakRule.streak(days, "2026-10-23")); // 今天没打，昨天为止=2? 22,23 → 2
        assertEquals(1, StreakRule.streak(days, "2026-10-25"));
        assertEquals(0, StreakRule.streak(days, "2026-10-27")); // 断签归零
        // 纯兑换与手动扣分不算打卡日
        List<CoinTx> onlyRedeem = List.of(tx("t9", TxType.REDEEM, -60, "2026-10-25", "x", null));
        assertEquals(0, StreakRule.streak(StreakRule.checkinDays(onlyRedeem), "2026-10-25"));
        List<CoinTx> manualNeg = List.of(tx("t10", TxType.MANUAL, -10, "2026-10-25", null, null));
        assertEquals(0, StreakRule.streak(StreakRule.checkinDays(manualNeg), "2026-10-25"));
    }
}
