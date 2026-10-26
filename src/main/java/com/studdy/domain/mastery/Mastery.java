package com.studdy.domain.mastery;

/** 掌握度语义值（wire 与 docs/05 一致：proficient/mastered/partial/weak/unanswered/needs_retest）。 */
public enum Mastery {
    PROFICIENT("proficient", "精通"),
    MASTERED("mastered", "掌握"),
    PARTIAL("partial", "部分掌握"),
    WEAK("weak", "未掌握"),
    UNANSWERED("unanswered", "未答"),
    NEEDS_RETEST("needs_retest", "待重测");

    private final String wire;
    private final String label;

    Mastery(String wire, String label) {
        this.wire = wire;
        this.label = label;
    }

    public String wire() {
        return wire;
    }

    public String label() {
        return label;
    }

    public static Mastery fromWire(String wire) {
        for (Mastery m : values()) {
            if (m.wire.equals(wire)) {
                return m;
            }
        }
        throw new IllegalArgumentException("未知掌握度: " + wire);
    }
}
