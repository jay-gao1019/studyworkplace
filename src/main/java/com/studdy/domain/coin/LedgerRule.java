package com.studdy.domain.coin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 账本规则（docs/03 M1.1–1.3、1.6）：余额/累计永远由流水推导；冲正成对且不可重复。 */
public final class LedgerRule {

    private LedgerRule() {}

    public static int balance(List<CoinTx> txs) {
        return txs.stream().mapToInt(CoinTx::amount).sum();
    }

    public static int totalEarned(List<CoinTx> txs) {
        return txs.stream().mapToInt(CoinTx::amount).filter(a -> a > 0).sum();
    }

    /**
     * 生成冲正流水（M1.3）：金额取反、引用原流水。
     * @throws IllegalStateException 原流水已被冲正（幂等拒绝）或本身就是冲正流水
     */
    public static CoinTx reverseOf(CoinTx origin, List<CoinTx> all, String newId, java.time.LocalDateTime now, String dayKey) {
        if (origin.isReverse()) {
            throw new IllegalStateException("冲正流水不可再冲正");
        }
        boolean already = all.stream().anyMatch(t ->
                t.type() == TxType.REVERSE && origin.id().equals(t.refTxId()));
        if (already) {
            throw new IllegalStateException("该笔流水已经取消过啦");
        }
        return new CoinTx(newId, origin.profileId(), TxType.REVERSE, -origin.amount(),
                dayKey, now, null, origin.id(), null);
    }

    /** 某流水是否已被冲正。 */
    public static boolean isReversed(String txId, List<CoinTx> all) {
        return all.stream().anyMatch(t -> t.type() == TxType.REVERSE && txId.equals(t.refTxId()));
    }

    /** M1.2 去重键：(profileId,type,srcId,dayKey)。 */
    public static String dedupKey(CoinTx tx) {
        return String.join("|", tx.profileId(), tx.type().wire(),
                tx.srcId() == null ? "" : tx.srcId(), tx.dayKey());
    }

    /** 同键是否已有活跃（未被冲正的）流水。 */
    public static boolean dedupConflict(CoinTx candidate, List<CoinTx> all) {
        if (!candidate.type().dedupEligible() || candidate.srcId() == null) {
            return false;
        }
        String key = dedupKey(candidate);
        Set<String> reversedIds = new HashSet<>();
        for (CoinTx t : all) {
            if (t.type() == TxType.REVERSE && t.refTxId() != null) {
                reversedIds.add(t.refTxId());
            }
        }
        return all.stream().anyMatch(t -> !t.id().equals(candidate.id())
                && dedupKey(t).equals(key)
                && !reversedIds.contains(t.id()));
    }

    public record Bucket(String label, int income, int expense) {}

    /** M1.6 按桶聚合；labels 为有序桶标签（日/月/年），缺失补零。 */
    public static List<Bucket> aggregate(List<CoinTx> txs, List<String> labels,
                                         java.util.function.Function<String, String> dayToLabel) {
        int[] inc = new int[labels.size()];
        int[] exp = new int[labels.size()];
        java.util.Map<String, Integer> pos = new java.util.HashMap<>();
        for (int i = 0; i < labels.size(); i++) {
            pos.put(labels.get(i), i);
        }
        for (CoinTx t : txs) {
            Integer p = pos.get(dayToLabel.apply(t.dayKey()));
            if (p == null) {
                continue;
            }
            if (t.amount() >= 0) {
                inc[p] += t.amount();
            } else {
                exp[p] += t.amount();
            }
        }
        java.util.List<Bucket> out = new java.util.ArrayList<>(labels.size());
        for (int i = 0; i < labels.size(); i++) {
            out.add(new Bucket(labels.get(i), inc[i], exp[i]));
        }
        return out;
    }
}
