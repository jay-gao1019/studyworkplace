package com.studdy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studdy.domain.coin.AdjustRule;
import com.studdy.domain.coin.CoinTx;
import com.studdy.domain.coin.LedgerRule;
import com.studdy.domain.coin.LevelRule;
import com.studdy.domain.coin.StreakRule;
import com.studdy.domain.coin.TxType;
import com.studdy.domain.util.DayKey;
import com.studdy.entity.CoinTxEntity;
import com.studdy.repo.CoinTxRepository;
import com.studdy.util.Ids;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 金币账本服务（docs/03 M9.1：全系统唯一流水写入口；M1.1 余额永远推导）。
 * 其余 service 一律经 appendTx/reverse 产生流水，禁止直插 coin_txs（T9 grep 门禁）。
 */
@Service
public class LedgerService {

    private final CoinTxRepository txs;
    private final SettingsService settings;
    private final ObjectMapper mapper = new ObjectMapper();

    public LedgerService(CoinTxRepository txs, SettingsService settings) {
        this.txs = txs;
        this.settings = settings;
    }

    /**
     * 唯一写入口（M9.1）。M1.2 去重服务层先查，数据库 uq_dedup 唯一索引为并发终防线。
     *
     * @param srcId 去重来源标识（taskItemId / treeNodeId / "checkin" / habitId+milestone 等），可为 null
     */
    @Transactional
    public CoinTx appendTx(String profileId, TxType type, int amount, String srcId,
                           String refTxId, String reason) {
        List<CoinTx> all = txsOf(profileId);
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        String dayKey = DayKey.ofToday(now);
        String id = Ids.next("tx");
        CoinTx candidate = new CoinTx(id, profileId, type, amount, dayKey, now, srcId, refTxId, reason);
        if (LedgerRule.dedupConflict(candidate, all)) {
            throw new ApiException(409, "今天已经记过啦（每日一次）");
        }
        try {
            txs.saveAndFlush(new CoinTxEntity(id, profileId, type.wire(), amount, dayKey,
                    now.atZone(ZoneId.systemDefault()).toInstant(), refJson(srcId, refTxId, reason)));
        } catch (org.springframework.dao.DataIntegrityViolationException dup) {
            throw new ApiException(409, "今天已经记过啦（每日一次）");
        }
        return candidate;
    }

    /** 取消勾选冲正（M1.3/M2.3）：LedgerRule 幂等校验 + 落库。 */
    @Transactional
    public CoinTx reverse(String profileId, String originTxId) {
        List<CoinTx> all = txsOf(profileId);
        CoinTx origin = all.stream().filter(t -> t.id().equals(originTxId)).findFirst()
                .orElseThrow(() -> new ApiException(404, "原流水不存在"));
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        CoinTx rev = LedgerRule.reverseOf(origin, all, Ids.next("tx"), now, DayKey.ofToday(now));
        txs.saveAndFlush(new CoinTxEntity(rev.id(), profileId, rev.type().wire(), rev.amount(),
                rev.dayKey(), now.atZone(ZoneId.systemDefault()).toInstant(), refJson(null, originTxId, null)));
        // 原流水置 revoked：数据库生成列 dedup_key 变 NULL，释放键位允许同日重新勾选
        txs.findById(originTxId).ifPresent(o -> {
            o.setRevoked(true);
            txs.saveAndFlush(o);
        });
        return rev;
    }

    /**
     * 家长数据修正（M7.6/FR-26）：冲正原流水 + 补录 MANUAL(newAmount) 成对，
     * 原流水永不改写；两步仍走本类唯一写入口，M9.1 不破。
     */
    @Transactional
    public Map<String, Object> adjustTx(String txId, int newAmount, String reason) {
        CoinTxEntity e = txs.findById(txId).orElseThrow(() -> new ApiException(404, "流水不存在"));
        String profileId = e.getProfileId();
        List<CoinTx> all = txsOf(profileId);
        CoinTx origin = all.stream().filter(t -> t.id().equals(txId)).findFirst()
                .orElseThrow(() -> new ApiException(404, "流水不存在"));
        String reject = AdjustRule.rejectReason(origin, all, newAmount, reason);
        if (reject != null) {
            throw new ApiException(400, reject);
        }
        CoinTx rev = reverse(profileId, txId);
        CoinTx adj = appendTx(profileId, TxType.MANUAL, newAmount, null, txId, AdjustRule.adjustReason(reason));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("reverse", rev);
        out.put("adjust", adj);
        out.put("balance", balance(profileId));
        return out;
    }

    public List<CoinTx> txsOf(String profileId) {
        List<CoinTx> out = new ArrayList<>();
        for (CoinTxEntity e : txs.findByProfileIdOrderByCreatedAtAsc(profileId)) {
            out.add(toDomain(e, mapper));
        }
        return out;
    }

    /** 家长数据修正列表（M7.6）：最近 limit 条，倒序，带 revoked 标记。 */
    public List<Map<String, Object>> recentForAdmin(String profileId, int limit) {
        List<CoinTx> all = txsOf(profileId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0 && out.size() < limit; i--) {
            CoinTx t = all.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.id());
            m.put("type", t.type().wire());
            m.put("amount", t.amount());
            m.put("dayKey", t.dayKey());
            m.put("createdAt", t.createdAt().toString());
            m.put("reason", t.reason());
            m.put("revoked", LedgerRule.isReversed(t.id(), all));
            out.add(m);
        }
        return out;
    }

    public int balance(String profileId) {
        return LedgerRule.balance(txsOf(profileId));
    }

    /** 概览：余额/累计/等级/streak（派生值不持久化，M9.4）。 */
    public Map<String, Object> summary(String profileId) {
        List<CoinTx> all = txsOf(profileId);
        int balance = LedgerRule.balance(all);
        int earned = LedgerRule.totalEarned(all);
        LevelRule lr = settings.levelRule();
        String today = DayKey.ofToday(LocalDateTime.now(ZoneId.systemDefault()));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("balance", balance);
        m.put("totalEarned", earned);
        m.put("levelIndex", lr.levelIndex(earned));
        m.put("levelName", lr.levelName(earned));
        m.put("toNextLevel", lr.toNextLevel(earned));
        m.put("streak", StreakRule.streak(StreakRule.checkinDays(all), today));
        return m;
    }

    /** M1.6 日/月/年柱状图：最近 30 天 / 12 月 / 5 年，缺失补零。 */
    public List<LedgerRule.Bucket> stats(String profileId, String period) {
        List<CoinTx> all = txsOf(profileId);
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        List<String> labels = new ArrayList<>();
        Function<String, String> dayToLabel;
        switch (period) {
            case "month" -> {
                for (int i = 11; i >= 0; i--) {
                    labels.add(java.time.YearMonth.from(today).minusMonths(i).toString());
                }
                dayToLabel = DayKey::monthOf;
            }
            case "year" -> {
                for (int i = 4; i >= 0; i--) {
                    labels.add(String.valueOf(today.getYear() - i));
                }
                dayToLabel = DayKey::yearOf;
            }
            default -> {
                for (int i = 29; i >= 0; i--) {
                    labels.add(DayKey.of(today.minusDays(i)));
                }
                dayToLabel = d -> d;
            }
        }
        return LedgerRule.aggregate(all, labels, dayToLabel);
    }

    static CoinTx toDomain(CoinTxEntity e, ObjectMapper mapper) {
        String srcId = null, refTxId = null, reason = null;
        if (e.getRef() != null && !e.getRef().isBlank()) {
            try {
                var n = mapper.readTree(e.getRef());
                srcId = n.path("srcId").asText(null);
                refTxId = n.path("refTxId").asText(null);
                reason = n.path("reason").asText(null);
            } catch (Exception ignored) {
            }
        }
        LocalDateTime ldt = e.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDateTime();
        return new CoinTx(e.getId(), e.getProfileId(), TxType.fromWire(e.getType()), e.getAmount(),
                e.getDayKey(), ldt, srcId, refTxId, reason);
    }

    private String refJson(String srcId, String refTxId, String reason) {
        ObjectNode n = mapper.createObjectNode();
        if (srcId != null) n.put("srcId", srcId);
        if (refTxId != null) n.put("refTxId", refTxId);
        if (reason != null) n.put("reason", reason);
        return n.size() == 0 ? null : n.toString();
    }
}
