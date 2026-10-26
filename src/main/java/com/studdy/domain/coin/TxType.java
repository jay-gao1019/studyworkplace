package com.studdy.domain.coin;

/** 金币流水类型（docs/03 M1 表）。 */
public enum TxType {
    DAILY_CHECKIN("daily_checkin"),
    TASK("task"),
    TEXTBOOK("textbook"),
    REDEEM("redeem"),
    REDEEM_REFUND("redeem_refund"),
    REVERSE("reverse"),
    SCREEN_OVER("screen_over"),
    HABIT_MILESTONE("habit_milestone"),
    MANUAL("manual");

    private final String wire;

    TxType(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static TxType fromWire(String wire) {
        for (TxType t : values()) {
            if (t.wire.equals(wire)) {
                return t;
            }
        }
        throw new IllegalArgumentException("未知流水类型: " + wire);
    }

    /** M1.2：这些类型参与"每日一次"数据库去重兜底；reverse/redeem/refund/manual 豁免。 */
    public boolean dedupEligible() {
        return this != REVERSE && this != REDEEM && this != REDEEM_REFUND && this != MANUAL;
    }

    /** M1.5：可建立打卡日的流水（兑换与手动惩罚不算"打卡"）。 */
    public boolean countsAsActivity() {
        return switch (this) {
            case REDEEM, REDEEM_REFUND -> false;
            case MANUAL -> true; // 手动正数算活动；负数由调用方按金额过滤
            default -> true;
        };
    }
}
