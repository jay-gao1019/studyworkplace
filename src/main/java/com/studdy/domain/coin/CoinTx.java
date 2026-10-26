package com.studdy.domain.coin;

import java.time.LocalDateTime;

/** 不可变金币流水（docs/05 §4）。srcId 为 M1.2 去重来源标识（taskItemId/treeNodeId/checkin/habit+milestone 等）。 */
public record CoinTx(
        String id,
        String profileId,
        TxType type,
        int amount,
        String dayKey,
        LocalDateTime createdAt,
        String srcId,
        String refTxId,
        String reason) {

    public boolean isReverse() {
        return type == TxType.REVERSE;
    }
}
