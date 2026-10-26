package com.studdy.domain.coin;

import java.util.List;

/**
 * 家长数据修正规则（docs/03 M7.6，v2.2/FR-26）。
 * 修正=冲正原流水+补录 MANUAL 成对；本类只做"可否修正/参数合法"的纯判定。
 */
public final class AdjustRule {

    private AdjustRule() {}

    /** 返回拒绝原因；null=允许修正。 */
    public static String rejectReason(CoinTx origin, List<CoinTx> all, int newAmount, String reason) {
        if (origin.isReverse()) {
            return "冲正流水本身不能再修正";
        }
        if (origin.type() == TxType.REDEEM || origin.type() == TxType.REDEEM_REFUND) {
            return "兑换类请在商店用「撤销退款」，保持兑换记录一致";
        }
        if (LedgerRule.isReversed(origin.id(), all)) {
            return "该流水已被冲正，无需修正";
        }
        if (newAmount == 0) {
            return "新金额不能为 0";
        }
        if (Math.abs(newAmount) > 1000) {
            return "单次修正金额需在 ±1000 以内";
        }
        if (reason == null || reason.trim().length() < 2) {
            return "修正原因至少 2 个字";
        }
        return null;
    }

    /** 补录流水的 reason 前缀（审计可读）。 */
    public static String adjustReason(String rawReason) {
        return "修正:" + rawReason.trim();
    }
}
