package com.studdy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studdy.domain.approval.ApprovalRule;
import com.studdy.domain.coin.CoinTx;
import com.studdy.domain.coin.TxType;
import com.studdy.domain.util.DayKey;
import com.studdy.entity.*;
import com.studdy.repo.*;
import com.studdy.util.Ids;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 金币审批中心（docs/03 M11，v2.3/FR-29）。
 * 孩子端全部加分动作与家长记录（手动奖惩/扣分面板）先落 pending_actions，审批通过才经
 * LedgerService.appendTx 落账并补源表 done 行；商店兑换与屏幕超时强制扣减豁免（M11.6）。
 * 流水按"审批通过当日"落 dayKey（appendTx 恒取今日，M11.8）。
 */
@Service
public class ApprovalService {

    private final PendingActionRepository pendingRepo;
    private final ApprovalConfigRepository configRepo;
    private final PenaltyPresetRepository presetRepo;
    private final LedgerService ledger;
    private final PlanCheckRepository planRepo;
    private final TextbookCheckRepository textbookCheckRepo;
    private final HabitChainRepository chainRepo;
    private final TaskItemRepository taskRepo;
    private final TreeNodeRepository nodeRepo;
    private final HabitRepository habitRepo;
    private final ProfileRepository profileRepo;
    private final SeedService seed;
    private final PhotoService photos;
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile Map<String, String> seedTitlesCache;

    public ApprovalService(PendingActionRepository pendingRepo, ApprovalConfigRepository configRepo,
                           PenaltyPresetRepository presetRepo, LedgerService ledger,
                           PlanCheckRepository planRepo, TextbookCheckRepository textbookCheckRepo,
                           HabitChainRepository chainRepo, TaskItemRepository taskRepo,
                           TreeNodeRepository nodeRepo, HabitRepository habitRepo,
                           ProfileRepository profileRepo, SeedService seed, PhotoService photos) {
        this.pendingRepo = pendingRepo;
        this.configRepo = configRepo;
        this.presetRepo = presetRepo;
        this.ledger = ledger;
        this.planRepo = planRepo;
        this.textbookCheckRepo = textbookCheckRepo;
        this.chainRepo = chainRepo;
        this.taskRepo = taskRepo;
        this.nodeRepo = nodeRepo;
        this.habitRepo = habitRepo;
        this.profileRepo = profileRepo;
        this.seed = seed;
        this.photos = photos;
    }

    // ── 开关（M11.7） ────────────────────────────────

    /** 单行配置读取；缺失即首启自动补默认（开启、无免审批 kind）。 */
    public ApprovalConfigEntity configEntity() {
        return configRepo.findById("approval").orElseGet(() -> configRepo.save(
                new ApprovalConfigEntity("approval", true, "[]")));
    }

    public Map<String, Object> configView() {
        ApprovalConfigEntity c = configEntity();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", c.isEnabled());
        m.put("autoKinds", autoKinds(c));
        m.put("configurableKinds", new ArrayList<>(ApprovalRule.CONFIGURABLE_KINDS));
        return m;
    }

    @Transactional
    public Map<String, Object> updateConfig(Boolean enabled, List<String> autoKinds) {
        ApprovalConfigEntity c = configEntity();
        if (enabled != null) {
            c.setEnabled(enabled);
        }
        if (autoKinds != null) {
            for (String k : autoKinds) {
                if (!ApprovalRule.isConfigurableKind(k)) {
                    throw new ApiException(400, "不支持的免审批类型: " + k);
                }
            }
            try {
                c.setAutoKinds(mapper.writeValueAsString(new ArrayList<>(new LinkedHashSet<>(autoKinds))));
            } catch (Exception e) {
                throw new ApiException(500, "配置序列化失败");
            }
        }
        configRepo.save(c);
        return configView();
    }

    private Set<String> autoKinds(ApprovalConfigEntity c) {
        Set<String> out = new LinkedHashSet<>();
        try {
            if (c.getAutoKinds() != null && !c.getAutoKinds().isBlank()) {
                for (JsonNode n : mapper.readTree(c.getAutoKinds())) {
                    if (ApprovalRule.isConfigurableKind(n.asText())) {
                        out.add(n.asText());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** 该类动作是否需要审批（M11.7 + 非正额直接放行）。 */
    public boolean needsApproval(String kind, int amount) {
        ApprovalConfigEntity c = configEntity();
        return ApprovalRule.needsApproval(c.isEnabled(), autoKinds(c), kind, amount);
    }

    /** 该类动作是否需要审批（PENALTY 恒审批，amount 已取正数快照）。 */
    public boolean requiresKind(String kind) {
        ApprovalConfigEntity c = configEntity();
        return c.isEnabled() && !autoKinds(c).contains(kind);
    }

    // ── 申请（M11.4/M11.5） ────────────────────────────────

    /**
     * 提交申请；dayKey 为占位键（当日类=今日，终身类="ALL"）。
     * 服务层先查 pending 占位，数据库 uq_pending 唯一索引为并发终防线。
     */
    @Transactional
    public PendingActionEntity submit(String profileId, String kind, String refId,
                                      String dayKey, int amount, String reason) {
        return submit(profileId, kind, refId, dayKey, amount, reason, null, null);
    }

    /**
     * v2.4/M11.10+M13.5：申请可携带 0–3 张已上传佐证照片（photoIds 由 /api/photo/evidence 先行换取）
     * 与备注；照片 id 存 evidence_ids CSV，备注并入 reason（若未含）。
     */
    @Transactional
    public PendingActionEntity submit(String profileId, String kind, String refId,
                                      String dayKey, int amount, String reason,
                                      List<String> photoIds, String note) {
        if (!ApprovalRule.isKind(kind)) {
            throw new ApiException(400, "未知申请类型");
        }
        String evidence = evidenceCsv(photoIds);
        String finalReason = withNote(reason, note);
        var dup = pendingRepo.findByProfileIdAndKindAndRefIdAndDayKeyAndStatus(profileId, kind, refId, dayKey, "pending");
        if (dup.isPresent()) {
            throw new ApiException(409, "已有申请正在等待审批");
        }
        PendingActionEntity pa = new PendingActionEntity(Ids.next("pa"), profileId, kind, refId,
                Math.abs(amount), finalReason, dayKey);
        pa.setEvidenceIds(evidence);
        try {
            return pendingRepo.saveAndFlush(pa);
        } catch (org.springframework.dao.DataIntegrityViolationException race) {
            throw new ApiException(409, "已有申请正在等待审批");
        }
    }

    /** photoIds 校验（≤3、id 形制）→ evidence_ids CSV；空返回 null。 */
    private static String evidenceCsv(List<String> photoIds) {
        if (photoIds == null || photoIds.isEmpty()) {
            return null;
        }
        List<String> ids = new ArrayList<>(new LinkedHashSet<>(photoIds));
        if (ids.size() > 3) {
            throw new ApiException(400, "一次最多附 3 张佐证照片");
        }
        for (String id : ids) {
            if (id == null || !id.matches("[A-Za-z0-9_]{1,40}")) {
                throw new ApiException(400, "佐证照片标识不合法");
            }
        }
        String csv = String.join(",", ids);
        return csv.length() > 200 ? null : csv;
    }

    /** 备注并入 reason（孩子/家长填的备注在审批中心随文案展示）。 */
    private static String withNote(String reason, String note) {
        String r = reason == null ? "" : reason.trim();
        if (note == null || note.isBlank()) {
            return r.isEmpty() ? null : r;
        }
        String n = "备注：" + note.trim();
        String joined = r.isEmpty() ? n : r + "｜" + n;
        return joined.length() > 120 ? joined.substring(0, 120) : joined;
    }

    /** 当日占位键快捷方式。 */
    public String todayKey() {
        return DayKey.ofToday(LocalDateTime.now(ZoneId.systemDefault()));
    }

    public Optional<PendingActionEntity> findPending(String profileId, String kind, String refId, String dayKey) {
        return pendingRepo.findByProfileIdAndKindAndRefIdAndDayKeyAndStatus(profileId, kind, refId, dayKey, "pending");
    }

    /** 联查推导用：某档案某类动作在指定占位日的所有 refId（今日待审项）。 */
    public List<String> pendingRefIds(String profileId, String kind, String dayKey) {
        return pendingRepo.findByProfileIdAndStatusOrderByCreatedAtDesc(profileId, "pending").stream()
                .filter(pa -> pa.getKind().equals(kind) && pa.getDayKey().equals(dayKey))
                .map(PendingActionEntity::getRefId).toList();
    }

    // ── 审批动作（M11.2） ────────────────────────────────

    /** 通过：幂等（非 pending 直接回原结果），落账 + 补源表 done 行（单事务）。 */
    @Transactional
    public Map<String, Object> approve(String id) {
        PendingActionEntity pa = get(id);
        if (!"pending".equals(pa.getStatus())) {
            return view(pa, true); // 幂等重放
        }
        String txId = finalize(pa);
        pa.setStatus("approved");
        pa.setDecidedAt(Instant.now());
        pa.setTxId(txId);
        pendingRepo.save(pa);
        return view(pa, true);
    }

    @Transactional
    public Map<String, Object> reject(String id, String reason) {
        PendingActionEntity pa = get(id);
        String bad = ApprovalRule.transitionReject(pa.getStatus(), "rejected");
        if (bad != null) {
            throw new ApiException(409, bad);
        }
        pa.setStatus("rejected");
        pa.setDecidedAt(Instant.now());
        if (reason != null && !reason.isBlank()) {
            pa.setReason(reason.trim().length() > 120 ? reason.substring(0, 120) : reason.trim());
        }
        pendingRepo.save(pa);
        return view(pa, true);
    }

    /** 孩子撤回自己的 pending（再点半透明勾）。 */
    @Transactional
    public Map<String, Object> withdraw(String id, String profileId) {
        PendingActionEntity pa = get(id);
        if (!pa.getProfileId().equals(profileId)) {
            throw new ApiException(403, "只能撤回自己的申请");
        }
        String bad = ApprovalRule.transitionReject(pa.getStatus(), "withdrawn");
        if (bad != null) {
            throw new ApiException(409, bad);
        }
        pa.setStatus("withdrawn");
        pa.setDecidedAt(Instant.now());
        pendingRepo.save(pa);
        return view(pa, true);
    }

    /** 全部通过：按创建时间升序；同占位键重复项自动驳回（防并发重复申请卡死批量）。 */
    @Transactional
    public Map<String, Object> approveAll(String profileId) {
        List<PendingActionEntity> list = profileId == null || profileId.isBlank()
                ? new ArrayList<>(pendingRepo.findByStatusOrderByCreatedAtDesc("pending"))
                : new ArrayList<>(pendingRepo.findByProfileIdAndStatusOrderByCreatedAtDesc(profileId, "pending"));
        java.util.Collections.reverse(list);
        int approved = 0, rejected = 0, failed = 0;
        List<String> errors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (PendingActionEntity pa : list) {
            String k = pa.getKind() + "|" + pa.getRefId() + "|" + pa.getDayKey();
            if (!seen.add(k)) {
                reject(pa.getId(), "重复申请（自动清理）");
                rejected++;
                continue;
            }
            try {
                approve(pa.getId());
                approved++;
            } catch (ApiException e) {
                reject(pa.getId(), "无法落账：" + e.getMessage());
                failed++;
                errors.add(label(pa) + ": " + e.getMessage());
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("approved", approved);
        m.put("autoRejected", rejected);
        m.put("failed", failed);
        m.put("errors", errors);
        m.put("balanceAfter", profileId == null || profileId.isBlank() ? null : ledger.balance(profileId));
        return m;
    }

    // ── 落账分派（M11.9 finalize 映射） ────────────────────────────────

    private String finalize(PendingActionEntity pa) {
        int amount = pa.getAmount();
        CoinTx tx;
        switch (pa.getKind()) {
            case "TASK_CHECK" -> {
                tx = ledger.appendTx(pa.getProfileId(), TxType.TASK, amount, pa.getRefId(), null, null);
                var pk = new PlanCheckEntity.Pk(pa.getProfileId(), pa.getRefId(), pa.getDayKey());
                PlanCheckEntity c = planRepo.findById(pk).orElse(null);
                if (c == null) {
                    planRepo.save(new PlanCheckEntity(pa.getProfileId(), pa.getRefId(), pa.getDayKey(),
                            "done", Instant.now(), tx.id(), null));
                } else {
                    c.setStatus("done");
                    c.setCheckedAt(Instant.now());
                    c.setTxId(tx.id());
                    c.setReverseTxId(null);
                    planRepo.save(c);
                }
            }
            case "CHECKIN" -> tx = ledger.appendTx(pa.getProfileId(), TxType.DAILY_CHECKIN, amount,
                    "checkin", null, null);
            case "TEXTBOOK_CHECK" -> {
                if (textbookCheckRepo.findById(new TextbookCheckEntity.Pk(pa.getProfileId(), pa.getRefId()))
                        .isPresent()) {
                    throw new ApiException(409, "该知识点已经打过卡啦");
                }
                tx = ledger.appendTx(pa.getProfileId(), TxType.TEXTBOOK, amount, pa.getRefId(), null, null);
                textbookCheckRepo.save(new TextbookCheckEntity(pa.getProfileId(), pa.getRefId(),
                        todayKey(), Instant.now(), tx.id()));
            }
            case "LIFE_CHECK" -> tx = ledger.appendTx(pa.getProfileId(), TxType.TASK, amount,
                    "life_" + pa.getRefId() + "_" + pa.getDayKey(), null, null);
            case "HABIT_MILESTONE" -> {
                String[] parts = pa.getRefId().split("#");
                if (parts.length != 3) {
                    throw new ApiException(400, "里程碑引用格式错误");
                }
                tx = ledger.appendTx(pa.getProfileId(), TxType.HABIT_MILESTONE, amount,
                        "habit:" + parts[0] + ":" + parts[2] + ":" + parts[1], null, null);
                // 回写链上的占位：pendingId → txId
                chainRepo.findById(new HabitChainEntity.Pk(pa.getProfileId(), parts[0], parts[1])).ifPresent(c -> {
                    try {
                        var aw = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(
                                c.getAwardedMilestones() == null ? "{}" : c.getAwardedMilestones());
                        if (aw.has(pa.getId())) {
                            aw.put(pa.getId(), tx.id());
                            c.setAwardedMilestones(aw.toString());
                            chainRepo.save(c);
                        }
                    } catch (Exception ignored) {
                    }
                });
            }
            case "MASTERY_REWARD" -> tx = ledger.appendTx(pa.getProfileId(), TxType.TEXTBOOK, amount,
                    pa.getRefId(), null, "首次精通");
            case "MANUAL_TX" -> tx = ledger.appendTx(pa.getProfileId(), TxType.MANUAL, amount,
                    null, null, pa.getReason());
            case "PENALTY" -> tx = ledger.appendTx(pa.getProfileId(), TxType.MANUAL, -amount,
                    null, null, pa.getReason() == null || pa.getReason().isBlank() ? "扣分" : pa.getReason());
            default -> throw new ApiException(400, "未知申请类型");
        }
        return tx.id();
    }

    // ── 扣分面板（M12，v2.3/FR-28） ────────────────────────────────

    public List<Map<String, Object>> listPresets() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (PenaltyPresetEntity p : presetRepo.findAllByOrderBySortAscIdAsc()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("icon", p.getIcon());
            m.put("amount", p.getAmount());
            m.put("enabled", p.isEnabled());
            m.put("sort", p.getSort());
            out.add(m);
        }
        return out;
    }

    @Transactional
    public Map<String, Object> upsertPreset(String id, String name, String icon,
                                            Integer amount, Boolean enabled, Integer sort) {
        PenaltyPresetEntity p;
        if (id != null && presetRepo.existsById(id)) {
            p = presetRepo.findById(id).orElseThrow();
        } else {
            if (name == null || name.isBlank()) {
                throw new ApiException(400, "扣分事项名称必填");
            }
            p = new PenaltyPresetEntity(id != null ? id : Ids.next("pp"), name.trim(),
                    "⚠️", 5, true, 100);
        }
        if (name != null && !name.isBlank()) {
            if (name.trim().length() > 24) {
                throw new ApiException(400, "名称至多 24 字");
            }
            p.setName(name.trim());
        }
        if (icon != null && !icon.isBlank()) {
            p.setIcon(icon);
        }
        if (amount != null) {
            int a = Math.abs(amount);
            if (a < 1 || a > 100) {
                throw new ApiException(400, "扣分值限 1–100");
            }
            p.setAmount(a);
        }
        if (enabled != null) {
            p.setEnabled(enabled);
        }
        if (sort != null) {
            p.setSort(sort);
        }
        presetRepo.save(p);
        return listPresets().stream().filter(m -> m.get("id").equals(p.getId())).findFirst().orElse(Map.of());
    }

    @Transactional
    public void deletePreset(String id) {
        presetRepo.deleteById(id);
    }

    /** 一键扣分 → PENALTY 待审批（同预设同日限一条 pending）；快照名称入 reason，预设可安全删除。 */
    @Transactional
    public Map<String, Object> penaltyApply(String profileId, String presetId, String extraReason) {
        return penaltyApply(profileId, presetId, extraReason, null, null);
    }

    /** v2.4/M13.5：扣分可附 0–3 张佐证照片与备注。 */
    @Transactional
    public Map<String, Object> penaltyApply(String profileId, String presetId, String extraReason,
                                            List<String> photoIds, String note) {
        PenaltyPresetEntity p = presetRepo.findById(presetId)
                .orElseThrow(() -> new ApiException(404, "扣分预设不存在"));
        if (!p.isEnabled()) {
            throw new ApiException(400, "该扣分项已停用");
        }
        String day = todayKey();
        if (findPending(profileId, "PENALTY", presetId, day).isPresent()) {
            throw new ApiException(409, "今天已为这件事扣过分啦");
        }
        String reason = "扣分：" + p.getName()
                + (extraReason == null || extraReason.isBlank() ? "" : "（" + extraReason.trim() + "）");
        PendingActionEntity pa = submit(profileId, "PENALTY", presetId, day, -p.getAmount(),
                reason.length() > 120 ? reason.substring(0, 120) : reason, photoIds, null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pendingId", pa.getId());
        m.put("amount", -p.getAmount());
        m.put("label", p.getName());
        m.put("balance", ledger.balance(profileId));
        return m;
    }

    // ── 视图（M11.4 列表 / M11.9 孩子端 mine） ────────────────────────────────

    public List<Map<String, Object>> listForAdmin(String status, String profileId) {
        String st = status == null || status.isBlank() ? "pending" : status;
        List<PendingActionEntity> rows = profileId == null || profileId.isBlank()
                ? pendingRepo.findByStatusOrderByCreatedAtDesc(st)
                : pendingRepo.findByProfileIdAndStatusOrderByCreatedAtDesc(profileId, st);
        return rows.stream().map(pa -> view(pa, true)).toList();
    }

    /** 孩子端：本人 pending 全量（前端按 kind/refId/dayKey 匹配今日视图与撤回）。 */
    public List<Map<String, Object>> mine(String profileId) {
        return pendingRepo.findByProfileIdAndStatusOrderByCreatedAtDesc(profileId, "pending").stream()
                .map(pa -> view(pa, false)).toList();
    }

    public long countPendingForProfiles(List<String> profileIds) {
        return pendingRepo.findByStatusOrderByCreatedAtDesc("pending").stream()
                .filter(pa -> profileIds.contains(pa.getProfileId())).count();
    }

    public long countPending(String profileId) {
        return pendingRepo.findByProfileIdAndStatusOrderByCreatedAtDesc(profileId, "pending").size();
    }

    private PendingActionEntity get(String id) {
        return pendingRepo.findById(id).orElseThrow(() -> new ApiException(404, "申请不存在"));
    }

    private Map<String, Object> view(PendingActionEntity pa, boolean withProfileName) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", pa.getId());
        m.put("profileId", pa.getProfileId());
        if (withProfileName) {
            m.put("profileName", profileRepo.findById(pa.getProfileId())
                    .map(ProfileEntity::getName).orElse("?"));
        }
        m.put("kind", pa.getKind());
        m.put("refId", pa.getRefId());
        m.put("label", label(pa));
        m.put("amount", pa.getAmount());
        m.put("reason", pa.getReason());
        m.put("dayKey", pa.getDayKey());
        m.put("status", pa.getStatus());
        m.put("createdAt", pa.getCreatedAt().toString());
        m.put("decidedAt", pa.getDecidedAt() == null ? null : pa.getDecidedAt().toString());
        m.put("txId", pa.getTxId());
        // v2.4/M11.10：审批中心缩略图与孩子端"已附佐证"展示
        if (pa.getEvidenceIds() != null && !pa.getEvidenceIds().isBlank()) {
            List<Map<String, Object>> shots = photos.viewsByIds(List.of(pa.getEvidenceIds().split(",")));
            m.put("photos", shots);
            m.put("evidenceCount", shots.size());
        } else {
            m.put("photos", List.of());
            m.put("evidenceCount", 0);
        }
        return m;
    }

    /** 审批中心展示标签（kind + 引用解析）。 */
    private String label(PendingActionEntity pa) {
        String r = pa.getRefId();
        return switch (pa.getKind()) {
            case "TASK_CHECK" -> "任务：" + taskRepo.findById(r).map(TaskItemEntity::getName).orElse(r);
            case "CHECKIN" -> "每日签到";
            case "TEXTBOOK_CHECK" -> "教材打卡：" + nodeTitle(r);
            case "LIFE_CHECK" -> ("sleep".equals(r) ? "早睡打卡" : "早起打卡");
            case "HABIT_MILESTONE" -> {
                String[] parts = r.split("#");
                String habit = parts.length > 0
                        ? habitRepo.findById(parts[0]).map(HabitEntity::getName).orElse(parts[0]) : "?";
                String ms = parts.length > 2 ? parts[2] : "?";
                yield "习惯里程碑：" + habit + " 连续 " + ms + " 天";
            }
            case "MASTERY_REWARD" -> "首次精通：" + nodeTitle(r);
            case "MANUAL_TX" -> "家长记录：" + (pa.getReason() == null ? "—" : pa.getReason());
            // reason 已由 penaltyApply 拼好"扣分：预设名（补充）"，不再二次加前缀
            case "PENALTY" -> pa.getReason() == null || pa.getReason().isBlank() ? "扣分" : pa.getReason();
            default -> pa.getKind();
        };
    }

    private String nodeTitle(String nodeId) {
        String fromDb = nodeRepo.findById(nodeId).map(TreeNodeEntity::getTitle).orElse(null);
        if (fromDb != null && !fromDb.isBlank()) {
            return fromDb;
        }
        return seedLeafTitles().getOrDefault(nodeId, nodeId);
    }

    /** 内置 tb-/kn- 叶 id → 标题（懒扫种子文件，进程内缓存）。 */
    private Map<String, String> seedLeafTitles() {
        if (seedTitlesCache != null) {
            return seedTitlesCache;
        }
        Map<String, String> out = new HashMap<>();
        for (String subj : List.of("cn", "math", "en")) {
            for (int g = 1; g <= 6; g++) {
                for (String t : List.of("上", "下")) {
                    collectTitles(seed.textbookTerm(subj, g, t).path("modules"), out);
                    for (JsonNode module : seed.knowledgeTerm(subj, g, t, null).path("modules")) {
                        for (JsonNode unit : module.path("units")) {
                            for (JsonNode leaf : unit.path("leaves")) {
                                out.putIfAbsent(leaf.path("id").asText(), leaf.path("title").asText());
                            }
                        }
                    }
                }
            }
        }
        seedTitlesCache = out;
        return out;
    }

    private void collectTitles(JsonNode grpNode, Map<String, String> out) {
        for (JsonNode grp : grpNode) {
            for (JsonNode l : grp.path("leaves")) {
                out.putIfAbsent(l.path("id").asText(), l.path("title").asText());
            }
            for (JsonNode sub : grp.path("groups")) {
                for (JsonNode l : sub.path("leaves")) {
                    out.putIfAbsent(l.path("id").asText(), l.path("title").asText());
                }
            }
        }
    }
}
