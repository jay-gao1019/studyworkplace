package com.studdy.service;

import com.studdy.domain.coin.CoinTx;
import com.studdy.domain.coin.LedgerRule;
import com.studdy.domain.coin.TxType;
import com.studdy.domain.util.DayKey;
import com.studdy.entity.PlanCheckEntity;
import com.studdy.entity.ProfileEntity;
import com.studdy.entity.TaskItemEntity;
import com.studdy.repo.PlanCheckRepository;
import com.studdy.repo.ProfileRepository;
import com.studdy.repo.TaskItemRepository;
import com.studdy.util.Ids;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 每日计划 + 签到（docs/03 M2）。 */
@Service
public class PlanService {

    private final TaskItemRepository taskRepo;
    private final PlanCheckRepository planRepo;
    private final ProfileRepository profileRepo;
    private final LedgerService ledger;
    private final SettingsService settings;

    public PlanService(TaskItemRepository taskRepo, PlanCheckRepository planRepo,
                       ProfileRepository profileRepo, LedgerService ledger, SettingsService settings) {
        this.taskRepo = taskRepo;
        this.planRepo = planRepo;
        this.profileRepo = profileRepo;
        this.ledger = ledger;
        this.settings = settings;
    }

    /** M2.1 今日清单 = enabled 且年级/学期命中；附勾选状态与流水。 */
    public List<Map<String, Object>> todayView(String profileId) {
        ProfileEntity p = profileRepo.findById(profileId)
                .orElseThrow(() -> new ApiException(404, "档案不存在"));
        String dayKey = DayKey.ofToday(LocalDateTime.now(ZoneId.systemDefault()));
        Map<String, PlanCheckEntity> checks = planRepo.findByProfileIdAndDayKey(profileId, dayKey).stream()
                .collect(Collectors.toMap(PlanCheckEntity::getTaskItemId, Function.identity(), (a, b) -> a));
        List<Map<String, Object>> out = new ArrayList<>();
        for (TaskItemEntity t : taskRepo.findAllByOrderBySortAscIdAsc()) {
            if (!t.isEnabled() || !gradeHit(t, p.getGrade(), p.getTerm())) {
                continue;
            }
            PlanCheckEntity c = checks.get(t.getId());
            boolean done = c != null && "done".equals(c.getStatus());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("taskId", t.getId());
            m.put("name", t.getName());
            m.put("icon", t.getIcon());
            m.put("amount", t.getAmount());
            m.put("category", t.getCategory());
            m.put("done", done);
            m.put("txId", done ? c.getTxId() : null);
            out.add(m);
        }
        return out;
    }

    /** 今日已被扣/已完成的流水记录（含孩子端不可自勾的扣币项展示，M2.4）。 */
    public List<CoinTx> todayDeducted(String profileId) {
        String dayKey = DayKey.ofToday(LocalDateTime.now(ZoneId.systemDefault()));
        return ledger.txsOf(profileId).stream()
                .filter(t -> t.dayKey().equals(dayKey) && t.amount() < 0)
                // M2.4 v2.1：仅罚则类；冲正=取消勾选中和对，redeem=主动消费（P5 展示）
                .filter(t -> t.type() != TxType.REVERSE
                        && t.type() != TxType.REDEEM && t.type() != TxType.REDEEM_REFUND)
                .toList();
    }

    /**
     * 孩子端勾选切换（M2.2/2.3 原子四步）。扣币项（amount<0）孩子不可自勾（M2.4）。
     * 未勾→勾选：写流水+PlanCheck(done)；已勾→取消：冲正+置 undone。
     */
    @Transactional
    public Map<String, Object> toggleChild(String profileId, String taskItemId) {
        TaskItemEntity t = taskRepo.findById(taskItemId)
                .orElseThrow(() -> new ApiException(404, "任务项不存在"));
        if (!t.isEnabled()) {
            throw new ApiException(400, "该任务已停用");
        }
        if (t.getAmount() < 0) {
            throw new ApiException(403, "这项由家长在后台记录哦");
        }
        return toggle(profileId, t);
    }

    /** 家长侧：允许对扣币项发起记录（惩罚），仍遵守每日一次。 */
    @Transactional
    public Map<String, Object> toggleAdmin(String profileId, String taskItemId) {
        TaskItemEntity t = taskRepo.findById(taskItemId)
                .orElseThrow(() -> new ApiException(404, "任务项不存在"));
        return toggle(profileId, t);
    }

    private Map<String, Object> toggle(String profileId, TaskItemEntity t) {
        String dayKey = DayKey.ofToday(LocalDateTime.now(ZoneId.systemDefault()));
        PlanCheckEntity c = planRepo.findById(new PlanCheckEntity.Pk(profileId, t.getId(), dayKey)).orElse(null);
        boolean doneNow = c != null && "done".equals(c.getStatus());
        if (doneNow) {
            List<CoinTx> all = ledger.txsOf(profileId);
            if (c.getTxId() != null && LedgerRule.isReversed(c.getTxId(), all)) {
                // M7.6：家长修正已冲正该笔流水——取消勾选不再二次冲正，仅回退勾选态（补录保留）
                c.setStatus("undone");
                c.setCheckedAt(Instant.now());
                planRepo.save(c);
                return result(false, null, profileId);
            }
            CoinTx rev = ledger.reverse(profileId, c.getTxId());
            c.setStatus("undone");
            c.setReverseTxId(rev.id());
            c.setCheckedAt(Instant.now());
            planRepo.save(c);
            return result(false, rev, profileId);
        }
        CoinTx tx = ledger.appendTx(profileId, TxType.TASK, t.getAmount(), t.getId(), null, null);
        if (c == null) {
            planRepo.save(new PlanCheckEntity(profileId, t.getId(), dayKey, "done",
                    Instant.now(), tx.id(), null));
        } else {
            c.setStatus("done");
            c.setCheckedAt(Instant.now());
            c.setTxId(tx.id());
            c.setReverseTxId(null);
            planRepo.save(c);
        }
        return result(true, tx, profileId);
    }

    /** M2.5 签到（每日一次，服务端去重兜底）。 */
    @Transactional
    public Map<String, Object> checkin(String profileId) {
        String dayKey = DayKey.ofToday(LocalDateTime.now(ZoneId.systemDefault()));
        boolean already = ledger.txsOf(profileId).stream().anyMatch(t ->
                t.type() == TxType.DAILY_CHECKIN && t.dayKey().equals(dayKey));
        if (already) {
            throw new ApiException(409, "今天已经签过到啦");
        }
        CoinTx tx = ledger.appendTx(profileId, TxType.DAILY_CHECKIN,
                settings.checkinAmount(), "checkin", null, null);
        return result(true, tx, profileId);
    }

    private Map<String, Object> result(boolean done, CoinTx tx, String profileId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("done", done);
        m.put("tx", tx == null ? null : Map.of(
                "id", tx.id(), "type", tx.type().wire(), "amount", tx.amount(), "dayKey", tx.dayKey()));
        m.put("balance", ledger.balance(profileId));
        return m;
    }

    static boolean gradeHit(TaskItemEntity t, int grade, String term) {
        String gs = t.getGradeScope() == null ? "all" : t.getGradeScope();
        boolean gradeOk = gs.equals("all") || gs.contains(String.valueOf(grade));
        String ts = t.getTerm() == null ? "all" : t.getTerm();
        boolean termOk = ts.equals("all") || ts.equals(term);
        return gradeOk && termOk;
    }

    /** P1 悬浮卡学习线（docs/02 /api/plans/weekly）：近 7 日学习类任务勾选 done / 启用学习项×7。 */
    public Map<String, Object> weeklyStudy(String profileId, int grade, String term) {
        List<TaskItemEntity> study = taskRepo.findAllByOrderBySortAscIdAsc().stream()
                .filter(t -> t.isEnabled() && t.getAmount() > 0 && "study".equals(t.getCategory())
                        && gradeHit(t, grade, term))
                .toList();
        java.time.LocalDate today = java.time.LocalDate.now(ZoneId.systemDefault());
        int done = 0;
        for (int i = 0; i < 7; i++) {
            String dk = DayKey.of(today.minusDays(i));
            done += planRepo.findByProfileIdAndDayKey(profileId, dk).stream()
                    .filter(c -> "done".equals(c.getStatus())
                            && study.stream().anyMatch(t -> t.getId().equals(c.getTaskItemId())))
                    .count();
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("done", done);
        m.put("total", study.size() * 7);
        return m;
    }
}
