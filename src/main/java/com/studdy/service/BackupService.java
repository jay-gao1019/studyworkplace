package com.studdy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studdy.domain.backup.BackupFormat;
import com.studdy.domain.util.DayKey;
import com.studdy.entity.*;
import com.studdy.repo.*;
import com.studdy.util.Ids;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全量导出/两步导入（docs/03 M8.3–8.7、docs/05 §8）。
 * 导入前自动快照存 pre_import_snapshots（ADR-14），任一环节失败当前数据不变。
 */
@Service
public class BackupService {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, String> staged = new ConcurrentHashMap<>(); // confirmId → rawJson

    private final SettingsRepository settingsRepo;
    private final TaskItemRepository taskRepo;
    private final ShopItemRepository shopRepo;
    private final HabitRepository habitRepo;
    private final ProfileRepository profileRepo;
    private final CoinTxRepository txRepo;
    private final RedemptionRepository redemptionRepo;
    private final PlanCheckRepository planRepo;
    private final TextbookCheckRepository textbookRepo;
    private final TreeNodeRepository nodeRepo;
    private final QuizRepository quizRepo;
    private final AttemptRoundRepository attemptRepo;
    private final SleepLogRepository sleepRepo;
    private final ScreenSessionRepository screenRepo;
    private final ScreenBankRepository bankRepo;
    private final HabitLogRepository habitLogRepo;
    private final HabitChainRepository chainRepo;
    private final PreImportSnapshotRepository snapshotRepo;
    private final SessionService session;

    public BackupService(SettingsRepository settingsRepo, TaskItemRepository taskRepo,
                         ShopItemRepository shopRepo, HabitRepository habitRepo,
                         ProfileRepository profileRepo, CoinTxRepository txRepo,
                         RedemptionRepository redemptionRepo, PlanCheckRepository planRepo,
                         TextbookCheckRepository textbookRepo, TreeNodeRepository nodeRepo,
                         QuizRepository quizRepo, AttemptRoundRepository attemptRepo,
                         SleepLogRepository sleepRepo, ScreenSessionRepository screenRepo,
                         ScreenBankRepository bankRepo, HabitLogRepository habitLogRepo,
                         HabitChainRepository chainRepo, PreImportSnapshotRepository snapshotRepo,
                         SessionService session) {
        this.settingsRepo = settingsRepo;
        this.taskRepo = taskRepo;
        this.shopRepo = shopRepo;
        this.habitRepo = habitRepo;
        this.profileRepo = profileRepo;
        this.txRepo = txRepo;
        this.redemptionRepo = redemptionRepo;
        this.planRepo = planRepo;
        this.textbookRepo = textbookRepo;
        this.nodeRepo = nodeRepo;
        this.quizRepo = quizRepo;
        this.attemptRepo = attemptRepo;
        this.sleepRepo = sleepRepo;
        this.screenRepo = screenRepo;
        this.bankRepo = bankRepo;
        this.habitLogRepo = habitLogRepo;
        this.chainRepo = chainRepo;
        this.snapshotRepo = snapshotRepo;
        this.session = session;
    }

    // ── 导出 ────────────────────────────────

    public String exportJson() {
        ObjectNode payload = mapper.createObjectNode();
        // 必须用 valueToTree 得到真实 ObjectNode：putPOJO 的 POJONode.toString() 是 "{k=v}"，
        // 会让导出侧 checksum 与落盘 JSON 永不相等（校验和口径 bug 修复）
        payload.set("settings", settingsRepo.findById("singleton")
                .map(e -> (JsonNode) mapper.valueToTree(settingsMap(e))).orElse(null));
        payload.set("taskItems", arr(taskRepo.findAll(), this::taskItemMap));
        payload.set("shopItems", arr(shopRepo.findAll(), this::shopItemMap));
        payload.set("habits", arr(habitRepo.findAll(), this::habitMap));
        payload.set("profiles", arr(profileRepo.findAll(), this::profileMap));
        payload.set("coinTxs", arr(txRepo.findAll(), this::txMap));
        payload.set("redemptions", arr(redemptionRepo.findAll(), this::redemptionMap));
        payload.set("planChecks", arr(planRepo.findAll(), this::planCheckMap));
        payload.set("textbookChecks", arr(textbookRepo.findAll(), this::textbookCheckMap));
        payload.set("customNodes", arr(nodeRepo.findAll(), this::nodeMap));
        payload.set("customQuizzes", arr(quizRepo.findAll(), this::quizMap));
        payload.set("attempts", arr(attemptRepo.findAll(), this::attemptMap));
        ObjectNode life = payload.putObject("life");
        life.set("sleepLogs", arr(sleepRepo.findAll(), this::sleepMap));
        life.set("screenSessions", arr(screenRepo.findAll(), this::screenMap));
        life.set("screenBanks", arr(bankRepo.findAll(), this::bankMap));
        life.set("habitLogs", arr(habitLogRepo.findAll(), this::habitLogMap));
        life.set("habitChains", arr(chainRepo.findAll(), this::chainMap));

        ObjectNode root = mapper.createObjectNode();
        root.put("schemaVersion", BackupFormat.CURRENT_VERSION);
        root.put("exportedAt", Instant.now().toString());
        root.put("appVersion", "1.0.0");
        root.put("checksum", BackupFormat.checksum(mapper, payload));
        root.set("payload", payload);
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            throw new ApiException(500, "导出失败");
        }
    }

    public String exportFileName() {
        return "studyworkplace-backup-" + DayKey.of(LocalDate.now(ZoneId.systemDefault())) + ".json";
    }

    // ── 两步导入（M8.5）────────────────────────────────

    /** 第一步：解析→校验→存快照→返回 confirmId 与摘要预览。 */
    @Transactional
    public Map<String, Object> stageImport(String rawJson) {
        BackupFormat.Validation v = BackupFormat.validate(mapper, rawJson);
        if (!v.ok()) {
            throw new ApiException(400, v.message());
        }
        String confirmId = Ids.next("imp");
        staged.put(confirmId, rawJson);
        // 当前库快照（M8.7）
        snapshotRepo.save(new PreImportSnapshotEntity(Ids.next("sn"), Instant.now(), exportJson()));
        // 只留最近 3 份
        var all = snapshotRepo.findAll();
        if (all.size() > 3) {
            all.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
            snapshotRepo.deleteAll(all.subList(3, all.size()));
        }
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("confirmId", confirmId);
        preview.put("profileCount", v.profileCount());
        preview.put("txCount", v.txCount());
        preview.put("warning", "确认后将【覆盖当前全部数据】，操作不可逆（快照已保存，可人工找回）");
        return preview;
    }

    /** 第二步：整库替换，单事务。 */
    @Transactional
    public Map<String, Object> confirmImport(String confirmId) {
        String raw = staged.remove(confirmId);
        if (raw == null) {
            throw new ApiException(400, "导入会话已过期，请重新上传文件");
        }
        JsonNode payload;
        try {
            JsonNode root = mapper.readTree(raw);
            payload = root.path("payload");
            if (BackupFormat.CURRENT_VERSION.equals(root.path("schemaVersion").asText())
                    || root.path("schemaVersion").asText().compareTo(BackupFormat.CURRENT_VERSION) < 0) {
                // 已在校验阶段迁移；重新走 validate 拿迁移后 payload
                BackupFormat.validate(mapper, raw);
                payload = migratePayload(root);
            }
        } catch (Exception e) {
            throw new ApiException(400, "解析失败: " + e.getMessage());
        }
        wipeAll();
        int counts = 0;
        counts += readSettings(payload.path("settings"));
        counts += readList(payload.path("taskItems"), this::writeTaskItem);
        counts += readList(payload.path("shopItems"), this::writeShopItem);
        counts += readList(payload.path("habits"), this::writeHabit);
        counts += readList(payload.path("profiles"), this::writeProfile);
        // 流水按时间序插入：revoked 行先落库（生成列即 NULL），避免"同日冲正后重勾"键位冲突
        JsonNode txs = payload.path("coinTxs");
        if (txs.isArray()) {
            List<JsonNode> sorted = new ArrayList<>();
            txs.forEach(sorted::add);
            sorted.sort(Comparator.comparing(n -> ((JsonNode) n).path("createdAt").asText("")));
            counts += readList(mapper.valueToTree(sorted), this::writeTx);
        }
        counts += readList(payload.path("redemptions"), this::writeRedemption);
        counts += readList(payload.path("planChecks"), this::writePlanCheck);
        counts += readList(payload.path("textbookChecks"), this::writeTextbookCheck);
        counts += readList(payload.path("customNodes"), this::writeNode);
        counts += readList(payload.path("customQuizzes"), this::writeQuiz);
        counts += readList(payload.path("attempts"), this::writeAttempt);
        JsonNode life = payload.path("life");
        counts += readList(life.path("sleepLogs"), this::writeSleep);
        counts += readList(life.path("screenSessions"), this::writeScreen);
        counts += readList(life.path("screenBanks"), this::writeBank);
        counts += readList(life.path("habitLogs"), this::writeHabitLog);
        counts += readList(life.path("habitChains"), this::writeChain);
        return Map.of("imported", true, "rows", counts);
    }

    private JsonNode migratePayload(JsonNode root) {
        String v = root.path("schemaVersion").asText();
        JsonNode p = root.path("payload");
        if (v.compareTo(BackupFormat.CURRENT_VERSION) < 0) {
            return BackupFormat.migrate(mapper, v, p);
        }
        return p;
    }

    private void wipeAll() {
        chainRepo.deleteAllInBatch();
        habitLogRepo.deleteAllInBatch();
        bankRepo.deleteAllInBatch();
        screenRepo.deleteAllInBatch();
        sleepRepo.deleteAllInBatch();
        attemptRepo.deleteAllInBatch();
        quizRepo.deleteAllInBatch();
        nodeRepo.deleteAllInBatch();
        textbookRepo.deleteAllInBatch();
        planRepo.deleteAllInBatch();
        redemptionRepo.deleteAllInBatch();
        txRepo.deleteAllInBatch();
        profileRepo.deleteAllInBatch();
        habitRepo.deleteAllInBatch();
        shopRepo.deleteAllInBatch();
        taskRepo.deleteAllInBatch();
        settingsRepo.deleteAllInBatch();
    }

    // ── 序列化辅助 ────────────────────────────────

    @FunctionalInterface
    interface RowWriter {
        void write(JsonNode n);
    }

    private int readList(JsonNode arr, RowWriter w) {
        int c = 0;
        if (arr.isArray()) {
            for (JsonNode n : arr) {
                w.write(n);
                c++;
            }
        }
        return c;
    }

    private <T> ArrayNode arr(java.util.Collection<T> list, java.util.function.Function<T, Map<String, Object>> f) {
        ArrayNode a = mapper.createArrayNode();
        list.forEach(t -> a.add(mapper.valueToTree(f.apply(t))));
        return a;
    }

    private static String s(JsonNode n, String key) {
        JsonNode v = n.get(key);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String sDefault(JsonNode n, String key, String def) {
        String v = s(n, key);
        return v == null ? def : v;
    }

    private static int i(JsonNode n, String key) {
        return n.path(key).asInt();
    }

    private static int iDefault(JsonNode n, String key, int def) {
        return n.hasNonNull(key) ? n.get(key).asInt() : def;
    }

    private static boolean b(JsonNode n, String key, boolean def) {
        return n.hasNonNull(key) ? n.get(key).asBoolean() : def;
    }

    private static Instant ts(JsonNode n, String key) {
        String v = s(n, key);
        return v == null ? null : Instant.parse(v);
    }

    private static Instant tsReq(JsonNode n, String key) {
        Instant v = ts(n, key);
        return v == null ? Instant.now() : v;
    }

    private Map<String, Object> settingsMap(SettingsEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", e.getCode());
        m.put("pinHash", e.getPinHash());
        m.put("levelThresholds", e.getLevelThresholds());
        m.put("levelNames", e.getLevelNames());
        m.put("checkinAmount", e.getCheckinAmount());
        m.put("textbookCoinValue", e.getTextbookCoinValue());
        m.put("lifeSettings", e.getLifeSettings());
        return m;
    }

    private int readSettings(JsonNode n) {
        if (n == null || n.isNull() || !n.isObject()) {
            return 0;
        }
        SettingsEntity e = new SettingsEntity(sDefault(n, "code", "singleton"));
        e.setPinHash(s(n, "pinHash"));
        e.setLevelThresholds(sDefault(n, "levelThresholds", "[0,200,600,1500,3000]"));
        e.setLevelNames(sDefault(n, "levelNames", "[\"小嫩芽\",\"绿幼苗\",\"向阳花\",\"知识树\",\"小学霸\"]"));
        e.setCheckinAmount(iDefault(n, "checkinAmount", 20));
        e.setTextbookCoinValue(iDefault(n, "textbookCoinValue", 5));
        e.setLifeSettings(s(n, "lifeSettings"));
        settingsRepo.save(e);
        return 1;
    }

    private Map<String, Object> taskItemMap(TaskItemEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId()); m.put("name", e.getName()); m.put("icon", e.getIcon());
        m.put("amount", e.getAmount()); m.put("category", e.getCategory());
        m.put("gradeScope", e.getGradeScope()); m.put("term", e.getTerm());
        m.put("enabled", e.isEnabled()); m.put("sort", e.getSort());
        return m;
    }

    private void writeTaskItem(JsonNode n) {
        taskRepo.save(new TaskItemEntity(s(n, "id"), s(n, "name"), s(n, "icon"), i(n, "amount"),
                s(n, "category"), sDefault(n, "gradeScope", "all"), sDefault(n, "term", "all"),
                b(n, "enabled", true), iDefault(n, "sort", 0)));
    }

    private Map<String, Object> shopItemMap(ShopItemEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId()); m.put("name", e.getName()); m.put("icon", e.getIcon());
        m.put("price", e.getPrice()); m.put("enabled", e.isEnabled()); m.put("stock", e.getStock());
        return m;
    }

    private void writeShopItem(JsonNode n) {
        shopRepo.save(new ShopItemEntity(s(n, "id"), s(n, "name"), s(n, "icon"), i(n, "price"),
                b(n, "enabled", true), sDefault(n, "stock", "unlimited")));
    }

    private Map<String, Object> habitMap(HabitEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId()); m.put("name", e.getName()); m.put("icon", e.getIcon());
        m.put("cycleDays", e.getCycleDays()); m.put("milestoneRewards", e.getMilestoneRewards());
        m.put("enabled", e.isEnabled());
        return m;
    }

    private void writeHabit(JsonNode n) {
        habitRepo.save(new HabitEntity(s(n, "id"), s(n, "name"), s(n, "icon"),
                iDefault(n, "cycleDays", 21), s(n, "milestoneRewards"), b(n, "enabled", true)));
    }

    private Map<String, Object> profileMap(ProfileEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId()); m.put("name", e.getName()); m.put("avatar", e.getAvatar());
        m.put("grade", e.getGrade()); m.put("term", e.getTerm());
        m.put("createdAt", e.getCreatedAt().toString());
        return m;
    }

    private void writeProfile(JsonNode n) {
        profileRepo.save(new ProfileEntity(s(n, "id"), s(n, "name"), sDefault(n, "avatar", "🐼"),
                i(n, "grade"), s(n, "term"), tsReq(n, "createdAt")));
    }

    private Map<String, Object> txMap(CoinTxEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId()); m.put("profileId", e.getProfileId()); m.put("type", e.getType());
        m.put("amount", e.getAmount()); m.put("dayKey", e.getDayKey());
        m.put("createdAt", e.getCreatedAt().toString()); m.put("ref", e.getRef()); m.put("revoked", Boolean.TRUE.equals(e.getRevoked()));
        return m;
    }

    private void writeTx(JsonNode n) {
        CoinTxEntity e = new CoinTxEntity(s(n, "id"), s(n, "profileId"), s(n, "type"), i(n, "amount"),
                s(n, "dayKey"), tsReq(n, "createdAt"), s(n, "ref"));
        e.setRevoked(b(n, "revoked", false));
        txRepo.save(e);
    }

    private Map<String, Object> redemptionMap(RedemptionEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId()); m.put("profileId", e.getProfileId()); m.put("itemName", e.getItemName());
        m.put("price", e.getPrice()); m.put("icon", e.getIcon()); m.put("status", e.getStatus());
        m.put("createdAt", e.getCreatedAt().toString());
        m.put("redeemedAt", e.getRedeemedAt() == null ? null : e.getRedeemedAt().toString());
        m.put("txId", e.getTxId());
        return m;
    }

    private void writeRedemption(JsonNode n) {
        RedemptionEntity e = new RedemptionEntity(s(n, "id"), s(n, "profileId"), s(n, "itemName"),
                i(n, "price"), s(n, "icon"), s(n, "status"), tsReq(n, "createdAt"), s(n, "txId"));
        e.setRedeemedAt(ts(n, "redeemedAt"));
        redemptionRepo.save(e);
    }

    private Map<String, Object> planCheckMap(PlanCheckEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("profileId", e.getProfileId()); m.put("taskItemId", e.getTaskItemId());
        m.put("dayKey", e.getDayKey()); m.put("status", e.getStatus());
        m.put("checkedAt", e.getCheckedAt().toString());
        m.put("txId", e.getTxId()); m.put("reverseTxId", e.getReverseTxId());
        return m;
    }

    private void writePlanCheck(JsonNode n) {
        planRepo.save(new PlanCheckEntity(s(n, "profileId"), s(n, "taskItemId"), s(n, "dayKey"),
                s(n, "status"), tsReq(n, "checkedAt"), s(n, "txId"), s(n, "reverseTxId")));
    }

    private Map<String, Object> textbookCheckMap(TextbookCheckEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("profileId", e.getProfileId()); m.put("nodeId", e.getNodeId());
        m.put("dayKey", e.getDayKey()); m.put("checkedAt", e.getCheckedAt().toString());
        m.put("txId", e.getTxId());
        return m;
    }

    private void writeTextbookCheck(JsonNode n) {
        textbookRepo.save(new TextbookCheckEntity(s(n, "profileId"), s(n, "nodeId"), s(n, "dayKey"),
                tsReq(n, "checkedAt"), s(n, "txId")));
    }

    private Map<String, Object> nodeMap(TreeNodeEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId()); m.put("subject", e.getSubject()); m.put("grade", e.getGrade());
        m.put("term", e.getTerm()); m.put("parentRef", e.getParentRef()); m.put("kind", e.getKind());
        m.put("title", e.getTitle()); m.put("sort", e.getSort()); m.put("branch", e.getBranch());
        m.put("origin", e.getOrigin());
        return m;
    }

    private void writeNode(JsonNode n) {
        nodeRepo.save(new TreeNodeEntity(s(n, "id"), s(n, "subject"), i(n, "grade"), s(n, "term"),
                s(n, "parentRef"), s(n, "kind"), s(n, "title"), iDefault(n, "sort", 0),
                s(n, "branch"), sDefault(n, "origin", "custom")));
    }

    private Map<String, Object> quizMap(QuizEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId()); m.put("leafId", e.getLeafId()); m.put("subject", e.getSubject());
        m.put("type", e.getType()); m.put("stem", e.getStem()); m.put("options", e.getOptions());
        m.put("answer", e.getAnswer()); m.put("answerAccepts", e.getAnswerAccepts());
        m.put("explanation", e.getExplanation()); m.put("origin", e.getOrigin());
        return m;
    }

    private void writeQuiz(JsonNode n) {
        quizRepo.save(new QuizEntity(s(n, "id"), s(n, "leafId"), s(n, "subject"), s(n, "type"),
                s(n, "stem"), s(n, "options"), s(n, "answer"), s(n, "answerAccepts"),
                s(n, "explanation"), sDefault(n, "origin", "custom")));
    }

    private Map<String, Object> attemptMap(AttemptRoundEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId()); m.put("profileId", e.getProfileId()); m.put("leafId", e.getLeafId());
        m.put("quizSetHash", e.getQuizSetHash()); m.put("answers", e.getAnswers());
        m.put("rate", e.getRate()); m.put("mastery", e.getMastery());
        m.put("finishedAt", e.getFinishedAt().toString());
        return m;
    }

    private void writeAttempt(JsonNode n) {
        attemptRepo.save(new AttemptRoundEntity(s(n, "id"), s(n, "profileId"), s(n, "leafId"),
                s(n, "quizSetHash"), s(n, "answers"),
                n.hasNonNull("rate") ? new java.math.BigDecimal(n.get("rate").asText()) : java.math.BigDecimal.ZERO,
                s(n, "mastery"), tsReq(n, "finishedAt")));
    }

    private Map<String, Object> sleepMap(SleepLogEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("profileId", e.getProfileId()); m.put("dayKey", e.getDayKey()); m.put("kind", e.getKind());
        m.put("actualAt", e.getActualAt().toString()); m.put("onTime", e.isOnTime()); m.put("txId", e.getTxId());
        return m;
    }

    private void writeSleep(JsonNode n) {
        sleepRepo.save(new SleepLogEntity(s(n, "profileId"), s(n, "dayKey"), s(n, "kind"),
                tsReq(n, "actualAt"), b(n, "onTime", false), s(n, "txId")));
    }

    private Map<String, Object> screenMap(ScreenSessionEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId()); m.put("profileId", e.getProfileId()); m.put("dayKey", e.getDayKey());
        m.put("startAt", e.getStartAt().toString());
        m.put("endAt", e.getEndAt() == null ? null : e.getEndAt().toString());
        m.put("minutesUsed", e.getMinutesUsed());
        return m;
    }

    private void writeScreen(JsonNode n) {
        ScreenSessionEntity e = new ScreenSessionEntity(s(n, "id"), s(n, "profileId"), s(n, "dayKey"),
                tsReq(n, "startAt"));
        e.setEndAt(ts(n, "endAt"));
        if (n.hasNonNull("minutesUsed")) {
            e.setMinutesUsed(new java.math.BigDecimal(n.get("minutesUsed").asText()));
        }
        screenRepo.save(e);
    }

    private Map<String, Object> bankMap(ScreenBankEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("profileId", e.getProfileId()); m.put("grantedMinutes", e.getGrantedMinutes());
        m.put("consumedMinutes", e.getConsumedMinutes());
        return m;
    }

    private void writeBank(JsonNode n) {
        bankRepo.save(new ScreenBankEntity(s(n, "profileId"), iDefault(n, "grantedMinutes", 0),
                iDefault(n, "consumedMinutes", 0)));
    }

    private Map<String, Object> habitLogMap(HabitLogEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("profileId", e.getProfileId()); m.put("habitId", e.getHabitId());
        m.put("dayKey", e.getDayKey()); m.put("chainId", e.getChainId()); m.put("done", e.isDone());
        return m;
    }

    private void writeHabitLog(JsonNode n) {
        habitLogRepo.save(new HabitLogEntity(s(n, "profileId"), s(n, "habitId"), s(n, "dayKey"),
                s(n, "chainId"), b(n, "done", true)));
    }

    private Map<String, Object> chainMap(HabitChainEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("profileId", e.getProfileId()); m.put("habitId", e.getHabitId());
        m.put("chainId", e.getChainId()); m.put("consecutive", e.getConsecutive());
        m.put("startedAt", e.getStartedAt()); m.put("awardedMilestones", e.getAwardedMilestones());
        return m;
    }

    private void writeChain(JsonNode n) {
        chainRepo.save(new HabitChainEntity(s(n, "profileId"), s(n, "habitId"), s(n, "chainId"),
                i(n, "consecutive"), s(n, "startedAt"), sDefault(n, "awardedMilestones", "{}")));
    }
}
