package com.studdy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studdy.domain.coin.CoinTx;
import com.studdy.domain.coin.TxType;
import com.studdy.domain.life.ScreenQuota;
import com.studdy.domain.util.DayKey;
import com.studdy.entity.HabitChainEntity;
import com.studdy.entity.HabitEntity;
import com.studdy.entity.HabitLogEntity;
import com.studdy.entity.ScreenBankEntity;
import com.studdy.entity.ScreenSessionEntity;
import com.studdy.entity.SleepLogEntity;
import com.studdy.repo.HabitChainRepository;
import com.studdy.repo.HabitLogRepository;
import com.studdy.repo.HabitRepository;
import com.studdy.repo.ScreenBankRepository;
import com.studdy.repo.ScreenSessionRepository;
import com.studdy.repo.SleepLogRepository;
import com.studdy.util.Ids;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 生活管理（docs/03 M6）：作息 / 屏幕时间账户 / 习惯链；家务走 PlanService（M6-B）。 */
@Service
public class LifeService {

    private final SleepLogRepository sleepRepo;
    private final ScreenSessionRepository screenRepo;
    private final ScreenBankRepository bankRepo;
    private final HabitRepository habitRepo;
    private final HabitLogRepository habitLogRepo;
    private final HabitChainRepository chainRepo;
    private final LedgerService ledger;
    private final SettingsService settings;
    private final ObjectMapper mapper = new ObjectMapper();

    public LifeService(SleepLogRepository sleepRepo, ScreenSessionRepository screenRepo,
                       ScreenBankRepository bankRepo, HabitRepository habitRepo,
                       HabitLogRepository habitLogRepo, HabitChainRepository chainRepo,
                       LedgerService ledger, SettingsService settings) {
        this.sleepRepo = sleepRepo;
        this.screenRepo = screenRepo;
        this.bankRepo = bankRepo;
        this.habitRepo = habitRepo;
        this.habitLogRepo = habitLogRepo;
        this.chainRepo = chainRepo;
        this.ledger = ledger;
        this.settings = settings;
    }

    // ── M6-A 作息 ────────────────────────────────

    /** 作息打卡：kind=sleep/wake；窗口判定（M6.2），每日一次（PK 兜底）。 */
    @Transactional
    public Map<String, Object> sleepWake(String profileId, String kind) {
        if (!List.of("sleep", "wake").contains(kind)) {
            throw new ApiException(400, "kind 限 sleep/wake");
        }
        String dayKey = DayKey.ofToday(LocalDateTime.now(ZoneId.systemDefault()));
        var pk = new SleepLogEntity.Pk(profileId, dayKey, kind);
        if (sleepRepo.existsById(pk)) {
            throw new ApiException(409, "今天已经打过卡啦");
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        var ls = settings.life();
        String target = "sleep".equals(kind) ? ls.sleepTarget() : ls.wakeTarget();
        LocalTime tt = LocalTime.parse(target);
        LocalTime nt = now.toLocalTime();
        boolean onTime;
        if ("sleep".equals(kind)) {
            // [target−60, target+宽限] 内 = 按时；早于窗口视为"还没到点"也记按时（提前睡不算过错）
            onTime = !nt.isAfter(tt.plusMinutes(ls.sleepGraceMin()));
        } else {
            onTime = !nt.isAfter(tt.plusMinutes(ls.wakeGraceMin()));
        }
        int coin = onTime ? ("sleep".equals(kind) ? ls.sleepCoin() : ls.wakeCoin()) : 0;
        String txId = null;
        if (coin > 0) {
            CoinTx tx = ledger.appendTx(profileId, TxType.TASK, coin,
                    "life_" + kind + "_" + dayKey, null, null);
            txId = tx.id();
        }
        sleepRepo.save(new SleepLogEntity(profileId, dayKey, kind,
                now.atZone(ZoneId.systemDefault()).toInstant(), onTime, txId));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("dayKey", dayKey);
        m.put("kind", kind);
        m.put("onTime", onTime);
        m.put("rewarded", coin);
        m.put("balance", ledger.balance(profileId));
        return m;
    }

    /** 今日作息状态（P4 视图需要，docs/04 §2 作息分区）。 */
    public Map<String, Object> sleepToday(String profileId) {
        String dayKey = DayKey.ofToday(LocalDateTime.now(ZoneId.systemDefault()));
        Map<String, Object> out = new LinkedHashMap<>();
        for (String kind : List.of("sleep", "wake")) {
            SleepLogEntity e = sleepRepo
                    .findById(new SleepLogEntity.Pk(profileId, dayKey, kind)).orElse(null);
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("done", e != null);
            s.put("onTime", e != null && e.isOnTime());
            out.put(kind, s);
        }
        out.put("dayKey", dayKey);
        return out;
    }

    /** P1 悬浮卡生活线（docs/02 /api/plans/weekly）：近 7 日作息(2/日)+习惯勾选 / (2+习惯数)×7。 */
    public Map<String, Object> weeklyLife(String profileId) {
        long habits = habitRepo.findAll().stream().filter(HabitEntity::isEnabled).count();
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        int done = 0;
        for (int i = 0; i < 7; i++) {
            String dk = DayKey.of(today.minusDays(i));
            done += sleepRepo.findByProfileIdAndDayKey(profileId, dk).size();
            done += habitLogRepo.findByProfileIdAndDayKey(profileId, dk).stream()
                    .filter(HabitLogEntity::isDone).count();
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("done", done);
        m.put("total", (int) ((2 + habits) * 7));
        return m;
    }

    public List<SleepLogEntity> sleepLogs(String profileId, int days) {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        List<SleepLogEntity> out = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            out.addAll(sleepRepo.findByProfileIdAndDayKey(profileId, DayKey.of(today.minusDays(i))));
        }
        return out;
    }

    // ── M6-C 屏幕时间账户 ────────────────────────────────

    public Map<String, Object> screenView(String profileId) {
        String dayKey = DayKey.ofToday(LocalDateTime.now(ZoneId.systemDefault()));
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        var ls = settings.life();
        List<ScreenQuota.Session> sessions = todaySessions(profileId, dayKey);
        double used = ScreenQuota.usedToday(sessions, now);
        int bankAvailable = bank(profileId).available();
        ScreenQuota.Session active = ScreenQuota.active(sessions);
        boolean penalizedToday = ledger.txsOf(profileId).stream().anyMatch(t ->
                t.type() == TxType.SCREEN_OVER && t.dayKey().equals(dayKey));
        boolean locked = penalizedToday || used >= ls.screenDailyMinutes() + bankAvailable;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("dayKey", dayKey);
        m.put("dailyMinutes", ls.screenDailyMinutes());
        m.put("bankAvailable", bankAvailable);
        m.put("usedMinutes", Math.round(used * 10) / 10.0);
        m.put("over", used > ls.screenDailyMinutes() + bankAvailable);
        m.put("locked", locked);
        m.put("activeSession", active == null ? null : Map.of(
                "id", active.id(), "startAt", active.startAt().toString(),
                "minutesNow", Math.round(active.minutesAsOf(now) * 10) / 10.0));
        return m;
    }

    @Transactional
    public Map<String, Object> screenStart(String profileId) {
        String dayKey = DayKey.ofToday(LocalDateTime.now(ZoneId.systemDefault()));
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        var ls = settings.life();
        List<ScreenQuota.Session> sessions = todaySessions(profileId, dayKey);
        if (ScreenQuota.active(sessions) != null) {
            throw new ApiException(409, "已经有在计时的会话啦");
        }
        boolean penalizedToday = ledger.txsOf(profileId).stream().anyMatch(t ->
                t.type() == TxType.SCREEN_OVER && t.dayKey().equals(dayKey));
        if (!ScreenQuota.canStartNew(new ScreenQuota.Settings(ls.screenDailyMinutes(),
                ls.screenWeeklyBankCap(), ls.screenOverPenalty()),
                sessions, now, bank(profileId).available(), penalizedToday)) {
            throw new ApiException(403, "今日额度已用完，明天再来吧");
        }
        ScreenSessionEntity e = new ScreenSessionEntity(Ids.next("sc"), profileId, dayKey,
                now.atZone(ZoneId.systemDefault()).toInstant());
        screenRepo.save(e);
        return Map.of("sessionId", e.getId(), "startAt", e.getStartAt().toString());
    }

    /** M6.8：超额度自动记一笔 screen_over 罚扣（当日一次）并锁定至次日。 */
    @Transactional
    public Map<String, Object> screenStop(String profileId) {
        String dayKey = DayKey.ofToday(LocalDateTime.now(ZoneId.systemDefault()));
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        var ls = settings.life();
        List<ScreenSessionEntity> entities = screenRepo.findByProfileIdAndDayKeyOrderByStartAtAsc(profileId, dayKey);
        ScreenSessionEntity active = entities.stream().filter(e -> e.getEndAt() == null).findFirst()
                .orElseThrow(() -> new ApiException(400, "当前没有在计时的会话"));
        double minutes = Duration.between(active.getStartAt(), now.atZone(ZoneId.systemDefault()).toInstant())
                .toMillis() / 60000.0;
        active.setEndAt(now.atZone(ZoneId.systemDefault()).toInstant());
        active.setMinutesUsed(BigDecimal.valueOf(minutes).setScale(2, RoundingMode.HALF_UP));
        screenRepo.save(active);

        List<ScreenQuota.Session> sessions = todaySessions(profileId, dayKey);
        double used = ScreenQuota.usedToday(sessions, now);
        ScreenBankEntity bank = bank(profileId);
        int bankAvailBefore = bank.available();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("minutes", Math.round(minutes * 10) / 10.0);
        m.put("usedToday", Math.round(used * 10) / 10.0);

        if (ScreenQuota.isOver(new ScreenQuota.Settings(ls.screenDailyMinutes(),
                ls.screenWeeklyBankCap(), ls.screenOverPenalty()), used, bankAvailBefore)) {
            // 结转消费（M6.9）
            int consume = ScreenQuota.bankConsume(new ScreenQuota.Settings(ls.screenDailyMinutes(),
                    ls.screenWeeklyBankCap(), ls.screenOverPenalty()), used, bankAvailBefore);
            if (consume > 0) {
                bank.setConsumedMinutes(bank.getConsumedMinutes() + consume);
                bankRepo.save(bank);
            }
            boolean penalizedToday = ledger.txsOf(profileId).stream().anyMatch(t ->
                    t.type() == TxType.SCREEN_OVER && t.dayKey().equals(dayKey));
            if (!penalizedToday) {
                ledger.appendTx(profileId, TxType.SCREEN_OVER, ls.screenOverPenalty(),
                        "screen_over_" + dayKey, null, "屏幕超时");
                m.put("penalized", ls.screenOverPenalty());
            }
            m.put("locked", true);
        }
        m.put("balance", ledger.balance(profileId));
        return m;
    }

    /** M6.9 家长发放额外分钟券（受周上限约束：累计授予 − 已消费 不超周上限的新增量）。 */
    @Transactional
    public Map<String, Object> bankGrant(String profileId, int minutes) {
        if (minutes <= 0 || minutes > 120) {
            throw new ApiException(400, "分钟券限 1–120 分钟");
        }
        var ls = settings.life();
        ScreenBankEntity bank = bank(profileId);
        if (bank.getGrantedMinutes() + minutes - bank.getConsumedMinutes() > ls.screenWeeklyBankCap()) {
            throw new ApiException(400, "超出结转周上限（" + ls.screenWeeklyBankCap() + " 分钟）");
        }
        bank.setGrantedMinutes(bank.getGrantedMinutes() + minutes);
        bankRepo.save(bank);
        return Map.of("grantedMinutes", bank.getGrantedMinutes(),
                "consumedMinutes", bank.getConsumedMinutes(), "available", bank.available());
    }

    private ScreenBankEntity bank(String profileId) {
        return bankRepo.findById(profileId).orElseGet(() -> {
            ScreenBankEntity b = new ScreenBankEntity(profileId, 0, 0);
            return bankRepo.save(b);
        });
    }

    private List<ScreenQuota.Session> todaySessions(String profileId, String dayKey) {
        List<ScreenQuota.Session> out = new ArrayList<>();
        for (ScreenSessionEntity e : screenRepo.findByProfileIdAndDayKeyOrderByStartAtAsc(profileId, dayKey)) {
            out.add(new ScreenQuota.Session(e.getId(),
                    e.getStartAt().atZone(ZoneId.systemDefault()).toLocalDateTime(),
                    e.getEndAt() == null ? null : e.getEndAt().atZone(ZoneId.systemDefault()).toLocalDateTime()));
        }
        return out;
    }

    // ── M6-D 习惯链 ────────────────────────────────

    /** 习惯每日勾选成链（M6.11–6.13）；里程碑按 (habitId,milestone,chainId) 去重发放。 */
    @Transactional
    public Map<String, Object> habitToggle(String profileId, String habitId) {
        HabitEntity h = habitRepo.findById(habitId).orElseThrow(() -> new ApiException(404, "习惯不存在"));
        if (!h.isEnabled()) {
            throw new ApiException(400, "该习惯已停用");
        }
        String today = DayKey.ofToday(LocalDateTime.now(ZoneId.systemDefault()));
        String yesterday = DayKey.yesterday(today);
        var logPk = new HabitLogEntity.Pk(profileId, habitId, today);
        var existing = habitLogRepo.findById(logPk);
        if (existing.isPresent() && existing.get().isDone()) {
            // 取消：删日志；链保留（简化：当日取消视作未发生，链回退由下轮重算——里程碑不回收 M6.12）
            habitLogRepo.delete(existing.get());
            return Map.of("done", false, "balance", ledger.balance(profileId));
        }
        // 找活动链：昨天有记录则续链
        var yesterdayLog = habitLogRepo.findById(new HabitLogEntity.Pk(profileId, habitId, yesterday));
        String chainId;
        HabitChainEntity chain;
        if (yesterdayLog.isPresent()) {
            chainId = yesterdayLog.get().getChainId();
            chain = chainRepo.findById(new HabitChainEntity.Pk(profileId, habitId, chainId))
                    .orElse(null);
        } else {
            chain = null;
            chainId = null;
        }
        if (chain == null) {
            chainId = Ids.next("hc");
            chain = new HabitChainEntity(profileId, habitId, chainId, 0, today, "{}");
        }
        int consecutive = chain.getConsecutive() + 1;
        chain.setConsecutive(consecutive);
        chainRepo.save(chain);
        habitLogRepo.save(new HabitLogEntity(profileId, habitId, today, chainId, true));

        // 里程碑判定（M6.13）
        int rewarded = 0;
        try {
            JsonNode rewards = mapper.readTree(h.getMilestoneRewards() == null ? "{}" : h.getMilestoneRewards());
            JsonNode awarded = mapper.readTree(chain.getAwardedMilestones() == null ? "{}" : chain.getAwardedMilestones());
            var aw = (com.fasterxml.jackson.databind.node.ObjectNode) awarded;
            var fields = rewards.fields();
            while (fields.hasNext()) {
                var f = fields.next();
                int milestone = Integer.parseInt(f.getKey());
                if (consecutive >= milestone && !aw.has(f.getKey())) {
                    CoinTx tx = ledger.appendTx(profileId, TxType.HABIT_MILESTONE, f.getValue().asInt(),
                            "habit:" + habitId + ":" + milestone + ":" + chainId, null, null);
                    aw.put(f.getKey(), tx.id());
                    rewarded += f.getValue().asInt();
                }
            }
            chain.setAwardedMilestones(aw.toString());
            chainRepo.save(chain);
        } catch (Exception e) {
            throw new ApiException(500, "里程碑配置解析失败");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("done", true);
        m.put("consecutive", consecutive);
        m.put("chainId", chainId);
        m.put("milestoneRewarded", rewarded);
        m.put("balance", ledger.balance(profileId));
        return m;
    }

    public List<Map<String, Object>> habitViews(String profileId) {
        String today = DayKey.ofToday(LocalDateTime.now(ZoneId.systemDefault()));
        List<Map<String, Object>> out = new ArrayList<>();
        for (HabitEntity h : habitRepo.findAll()) {
            if (!h.isEnabled()) {
                continue;
            }
            boolean doneToday = habitLogRepo.findById(new HabitLogEntity.Pk(profileId, h.getId(), today))
                    .map(HabitLogEntity::isDone).orElse(false);
            // 当前连续天数 = 今天(或昨天)所在链的 consecutive，且链未断
            int consecutive = 0;
            for (HabitChainEntity c : chainRepo.findByProfileIdAndHabitId(profileId, h.getId())) {
                // 链最新一天 = startedAt + consecutive -1；若该天 ∈ {今天,昨天} 视为活着
                LocalDate last = DayKey.parse(c.getStartedAt()).plusDays(c.getConsecutive() - 1L);
                if (last.toString().equals(today) || last.toString().equals(DayKey.yesterday(today))) {
                    consecutive = c.getConsecutive();
                }
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", h.getId());
            m.put("name", h.getName());
            m.put("icon", h.getIcon());
            m.put("cycleDays", h.getCycleDays());
            m.put("doneToday", doneToday);
            m.put("consecutive", consecutive);
            out.add(m);
        }
        return out;
    }
}
