package com.studdy.service;

import com.studdy.domain.coin.CoinTx;
import com.studdy.domain.coin.TxType;
import com.studdy.domain.util.DayKey;
import com.studdy.entity.*;
import com.studdy.repo.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * 学习台统计报表（docs/03 M10，v2.2/FR-25）。纯读侧聚合，不产生流水。
 * 口径：week=近 7 自然日（含今天），month=近 30 自然日；被冲正流水与其冲正成对剔除（M10.1）。
 */
@Service
public class StatsService {

    private final LedgerService ledger;
    private final ProfileService profiles;
    private final TextbookService textbook;
    private final KnowledgeService knowledge;
    private final TaskItemRepository taskRepo;
    private final PlanCheckRepository planRepo;
    private final AttemptRoundRepository attemptRepo;
    private final SleepLogRepository sleepRepo;
    private final ScreenSessionRepository screenRepo;
    private final HabitLogRepository habitRepo;

    public StatsService(LedgerService ledger, ProfileService profiles, TextbookService textbook,
                        KnowledgeService knowledge, TaskItemRepository taskRepo, PlanCheckRepository planRepo,
                        AttemptRoundRepository attemptRepo, SleepLogRepository sleepRepo,
                        ScreenSessionRepository screenRepo, HabitLogRepository habitRepo) {
        this.ledger = ledger;
        this.profiles = profiles;
        this.textbook = textbook;
        this.knowledge = knowledge;
        this.taskRepo = taskRepo;
        this.planRepo = planRepo;
        this.attemptRepo = attemptRepo;
        this.sleepRepo = sleepRepo;
        this.screenRepo = screenRepo;
        this.habitRepo = habitRepo;
    }

    public Map<String, Object> report(String pid, String period) {
        int days = "month".equals(period) ? 30 : 7;
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        String startKey = DayKey.of(today.minusDays(days - 1));
        String endKey = DayKey.of(today);
        ProfileService.ProfileView prof = profiles.get(pid);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("period", "month".equals(period) ? "month" : "week");
        out.put("from", startKey);
        out.put("to", endKey);
        out.put("coins", coinsWindow(pid, startKey, endKey));
        out.put("plan", planCompletion(pid, prof, days));
        out.put("textbook", textbookProgress(pid, prof));
        out.put("quiz", quizStats(pid, startKey, endKey, prof));
        out.put("life", lifeStats(pid, startKey, endKey));
        return out;
    }

    // ── 金币：剔除冲正对（M10.1）后分类汇总 ──
    private Map<String, Object> coinsWindow(String pid, String startKey, String endKey) {
        List<CoinTx> all = ledger.txsOf(pid);
        Set<String> reversedIds = new HashSet<>();
        for (CoinTx t : all) {
            if (t.type() == TxType.REVERSE && t.refTxId() != null) {
                reversedIds.add(t.refTxId());
            }
        }
        int income = 0, expense = 0;
        Map<String, Integer> bySource = new LinkedHashMap<>();
        for (CoinTx t : all) {
            if (t.dayKey().compareTo(startKey) < 0 || t.dayKey().compareTo(endKey) > 0) continue;
            if (t.type() == TxType.REVERSE || reversedIds.contains(t.id())) continue;
            if (t.amount() >= 0) income += t.amount(); else expense += -t.amount();
            bySource.merge(categoryOf(t), t.amount(), Integer::sum);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("income", income);
        m.put("expense", expense);
        m.put("net", income - expense);
        m.put("bySource", bySource);
        m.put("balance", ledger.balance(pid));
        return m;
    }

    private static String categoryOf(CoinTx t) {
        return switch (t.type()) {
            case DAILY_CHECKIN -> "签到";
            case TASK -> t.amount() >= 0 ? "学习任务" : "惩罚";
            case TEXTBOOK -> "教材打卡";
            case HABIT_MILESTONE -> "习惯里程碑";
            case SCREEN_OVER -> "屏幕超时";
            case REDEEM -> "商店兑换";
            case REDEEM_REFUND -> "兑换退款";
            case MANUAL -> t.reason() != null && t.reason().startsWith("修正") ? "家长修正" : "家长记录";
            case REVERSE -> "冲正"; // 已在上面剔除，防御保留
        };
    }

    // ── 计划完成率（同 weeklyStudy 口径，窗口可配天数）──
    private Map<String, Object> planCompletion(String pid, ProfileService.ProfileView prof, int days) {
        List<TaskItemEntity> study = taskRepo.findAllByOrderBySortAscIdAsc().stream()
                .filter(t -> t.isEnabled() && t.getAmount() > 0 && "study".equals(t.getCategory())
                        && PlanService.gradeHit(t, prof.grade(), prof.term()))
                .toList();
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        int done = 0;
        for (int i = 0; i < days; i++) {
            String dk = DayKey.of(today.minusDays(i));
            done += planRepo.findByProfileIdAndDayKey(pid, dk).stream()
                    .filter(c -> "done".equals(c.getStatus())
                            && study.stream().anyMatch(t -> t.getId().equals(c.getTaskItemId())))
                    .count();
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("done", done);
        m.put("total", study.size() * days);
        return m;
    }

    // ── 教材四进度（语/数/奥/英，FR-25 常显）──
    private Map<String, Object> textbookProgress(String pid, ProfileService.ProfileView prof) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String s : List.of("cn", "math", "en")) {
            Map<String, Object> tree = textbook.tree(pid, s, prof.grade(), prof.term());
            @SuppressWarnings("unchecked")
            Map<String, Object> prog = (Map<String, Object>) tree.get("progress");
            out.put(s, prog);
        }
        return out;
    }

    // ── 自测：窗口内轮数/平均正确率 + 六档分布（当前快照）──
    private Map<String, Object> quizStats(String pid, String startKey, String endKey, ProfileService.ProfileView prof) {
        Instant start = LocalDateTime.of(LocalDate.parse(startKey), java.time.LocalTime.MIN)
                .atZone(ZoneId.systemDefault()).toInstant();
        List<AttemptRoundEntity> rounds = attemptRepo.findByProfileIdOrderByFinishedAtDesc(pid).stream()
                .filter(a -> a.getFinishedAt().isAfter(start))
                .toList();
        double rateSum = 0;
        for (AttemptRoundEntity a : rounds) {
            BigDecimal r = a.getRate();
            rateSum += r == null ? 0 : r.doubleValue();
        }
        Map<String, Integer> mastery = new LinkedHashMap<>();
        for (String k : List.of("proficient", "mastered", "partial", "weak", "unanswered", "needs_retest")) {
            mastery.put(k, 0);
        }
        int leaves = 0;
        for (String s : List.of("cn", "math", "en")) {
            Map<String, Object> tree = knowledge.tree(pid, s, prof.grade(), prof.term(), null);
            for (Object mo : (List<Object>) tree.getOrDefault("modules", List.of())) {
                Map<String, Object> mod = (Map<String, Object>) mo;
                for (Object uo : (List<Object>) mod.getOrDefault("units", List.of())) {
                    Map<String, Object> unit = (Map<String, Object>) uo;
                    for (Object lo : (List<Object>) unit.getOrDefault("leaves", List.of())) {
                        Map<String, Object> leaf = (Map<String, Object>) lo;
                        leaves++;
                        String st = String.valueOf(leaf.getOrDefault("mastery", "unanswered"));
                        mastery.merge(st, 1, Integer::sum);
                    }
                }
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rounds", rounds.size());
        m.put("avgRate", rounds.isEmpty() ? 0 : Math.round(rateSum / rounds.size() * 100));
        m.put("leafTotal", leaves);
        m.put("mastery", mastery);
        return m;
    }

    // ── 生活：作息按时、屏幕分钟、习惯次数 ──
    private Map<String, Object> lifeStats(String pid, String startKey, String endKey) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate cursor = LocalDate.parse(startKey);
        LocalDate end = LocalDate.parse(endKey);
        int sleepOnTime = 0, sleepRecords = 0, habitDone = 0;
        double screenMinutes = 0;
        while (!cursor.isAfter(end)) {
            String dk = DayKey.of(cursor);
            for (SleepLogEntity s : sleepRepo.findByProfileIdAndDayKey(pid, dk)) {
                sleepRecords++;
                if (s.isOnTime()) sleepOnTime++;
            }
            for (ScreenSessionEntity s : screenRepo.findByProfileIdAndDayKeyOrderByStartAtAsc(pid, dk)) {
                screenMinutes += s.getMinutesUsed() == null ? 0 : s.getMinutesUsed().doubleValue();
            }
            for (HabitLogEntity h : habitRepo.findByProfileIdAndDayKey(pid, dk)) {
                if (h.isDone()) habitDone++;
            }
            cursor = cursor.plusDays(1);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sleepRecords", sleepRecords);
        m.put("sleepOnTime", sleepOnTime);
        m.put("screenMinutes", Math.round(screenMinutes));
        m.put("habitDone", habitDone);
        return m;
    }
}
