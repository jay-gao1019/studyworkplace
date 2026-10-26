package com.studdy.domain.coin;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** docs/03 M7.6 家长数据修正规则（v2.2/FR-26）。 */
class AdjustRuleTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 10, 25, 10, 0);

    private static CoinTx tx(String id, TxType type, int amount, String refTxId) {
        return new CoinTx(id, "p1", type, amount, "2026-10-25", T, null, refTxId, null);
    }

    @Test
    void allowNormalTask() {
        CoinTx origin = tx("t1", TxType.TASK, 5, null);
        assertNull(AdjustRule.rejectReason(origin, List.of(origin), 3, "算错了应该给3"));
        assertEquals("修正:算错了应该给3", AdjustRule.adjustReason(" 算错了应该给3 "));
    }

    @Test
    void rejectCategories() {
        CoinTx rev = tx("r1", TxType.REVERSE, -5, "t1");
        CoinTx redeem = tx("d1", TxType.REDEEM, -60, null);
        CoinTx origin = tx("t1", TxType.TASK, 5, null);
        List<CoinTx> allWithRev = List.of(origin, rev);
        assertTrue(AdjustRule.rejectReason(rev, allWithRev, 1, "试试").contains("冲正"));
        assertTrue(AdjustRule.rejectReason(redeem, allWithRev, 1, "试试").contains("撤销退款"));
        assertTrue(AdjustRule.rejectReason(origin, allWithRev, 1, "试试").contains("已被冲正"));
    }

    @Test
    void rejectBadParams() {
        CoinTx origin = tx("t1", TxType.MANUAL, 20, null);
        List<CoinTx> all = List.of(origin);
        assertTrue(AdjustRule.rejectReason(origin, all, 0, "合理原因").contains("0"));
        assertTrue(AdjustRule.rejectReason(origin, all, 1001, "合理原因").contains("1000"));
        assertTrue(AdjustRule.rejectReason(origin, all, 5, "短").contains("2 个字"));
        assertTrue(AdjustRule.rejectReason(origin, all, 5, null).contains("2 个字"));
    }

    @Test
    void negativeAdjustAllowedForPenaltyCorrection() {
        CoinTx origin = tx("t1", TxType.SCREEN_OVER, -10, null);
        assertNull(AdjustRule.rejectReason(origin, List.of(origin), -3, "只超了3分钟"));
    }
}
