package com.studdy.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.studdy.domain.coin.TxType;
import com.studdy.entity.*;
import com.studdy.repo.*;
import com.studdy.service.*;
import com.studdy.util.Ids;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 后台（家长门）API：X-Admin-Token 校验（docs/03 M7）。 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService admin;
    private final SessionService session;
    private final ProfileService profiles;
    private final PlanService plan;
    private final LedgerService ledger;
    private final ShopService shop;
    private final LifeService life;
    private final SettingsService settings;
    private final TaskItemRepository taskRepo;
    private final ShopItemRepository shopRepo;
    private final HabitRepository habitRepo;
    private final TreeNodeRepository nodeRepo;
    private final QuizRepository quizRepo;
    private final SeedService seed;

    public AdminController(AdminService admin, SessionService session, ProfileService profiles,
                           PlanService plan, LedgerService ledger, ShopService shop, LifeService life,
                           SettingsService settings, TaskItemRepository taskRepo, ShopItemRepository shopRepo,
                           HabitRepository habitRepo, TreeNodeRepository nodeRepo, QuizRepository quizRepo,
                           SeedService seed) {
        this.admin = admin;
        this.session = session;
        this.profiles = profiles;
        this.plan = plan;
        this.ledger = ledger;
        this.shop = shop;
        this.life = life;
        this.settings = settings;
        this.taskRepo = taskRepo;
        this.shopRepo = shopRepo;
        this.habitRepo = habitRepo;
        this.nodeRepo = nodeRepo;
        this.quizRepo = quizRepo;
        this.seed = seed;
    }

    private void gate(HttpServletRequest req) {
        if (!admin.validToken(req.getHeader("X-Admin-Token"))) {
            throw new ApiException(401, "需要家长身份验证");
        }
    }

    // ── 门 ──

    @PostMapping("/unlock")
    public Map<String, Object> unlock(@RequestBody JsonNode body) {
        return admin.unlock(body.path("pin").asText());
    }

    @PostMapping("/pin")
    public Map<String, Object> setPin(@RequestBody JsonNode body, HttpServletRequest req) {
        gate(req);
        admin.setPin(body.path("pin").asText());
        return Map.of("ok", true);
    }

    @PostMapping("/lock")
    public Map<String, Object> lock(HttpServletRequest req) {
        admin.revoke(req.getHeader("X-Admin-Token"));
        return Map.of("ok", true);
    }

    /** 配置快照（后台页水合）。 */
    @GetMapping("/config")
    public Map<String, Object> config(HttpServletRequest req) {
        gate(req);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("taskItems", taskRepo.findAllByOrderBySortAscIdAsc().stream().map(t -> row(
                t.getId(), t.getName(), t.getIcon(), t.getAmount(), t.getCategory(),
                t.getGradeScope(), t.getTerm(), t.isEnabled(), t.getSort())).toList());
        out.put("shopItems", shopRepo.findAll().stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId()); m.put("name", s.getName()); m.put("icon", s.getIcon());
            m.put("price", s.getPrice()); m.put("enabled", s.isEnabled()); m.put("stock", s.getStock());
            return m;
        }).toList());
        out.put("habits", habitRepo.findAll().stream().map(hb -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", hb.getId()); m.put("name", hb.getName()); m.put("icon", hb.getIcon());
            m.put("cycleDays", hb.getCycleDays()); m.put("milestoneRewards", hb.getMilestoneRewards());
            m.put("enabled", hb.isEnabled());
            return m;
        }).toList());
        out.put("profiles", profiles.list());
        var st = settings.get();
        Map<String, Object> sett = new LinkedHashMap<>();
        sett.put("checkinAmount", st.getCheckinAmount());
        sett.put("textbookCoinValue", st.getTextbookCoinValue());
        sett.put("levelThresholds", st.getLevelThresholds());
        sett.put("levelNames", st.getLevelNames());
        sett.put("lifeSettings", st.getLifeSettings());
        sett.put("pinSet", st.getPinHash() != null);
        out.put("settings", sett);
        out.put("redemptionsPending", pendingRedemptions());
        return out;
    }

    private List<Map<String, Object>> pendingRedemptions() {
        // 家长端：全部档案的 pending/redeemed/cancelled
        List<Map<String, Object>> out = new ArrayList<>();
        for (var p : profiles.list()) {
            for (Map<String, Object> r : shop.myRedemptions(p.id())) {
                Map<String, Object> m = new LinkedHashMap<>(r);
                m.put("profileName", p.name());
                m.put("profileId", p.id());
                out.add(m);
            }
        }
        return out;
    }

    // ── 任务项 CRUD ──

    @PostMapping("/task-items")
    public Map<String, Object> upsertTask(@RequestBody JsonNode b, HttpServletRequest req) {
        gate(req);
        String id = b.path("id").asText(null);
        TaskItemEntity t;
        if (id != null && taskRepo.existsById(id)) {
            t = taskRepo.findById(id).orElseThrow();
            t.setName(b.path("name").asText(t.getName()));
            t.setIcon(b.path("icon").asText(t.getIcon()));
            t.setAmount(b.path("amount").asInt(t.getAmount()));
            t.setCategory(b.path("category").asText(t.getCategory()));
            t.setGradeScope(b.path("gradeScope").asText(t.getGradeScope()));
            t.setTerm(b.path("term").asText(t.getTerm()));
            t.setEnabled(b.path("enabled").asBoolean(t.isEnabled()));
            t.setSort(b.path("sort").asInt(t.getSort()));
        } else {
            t = new TaskItemEntity(id != null ? id : Ids.next("task"), b.path("name").asText(),
                    b.path("icon").asText("✅"), b.path("amount").asInt(5),
                    b.path("category").asText("study"), b.path("gradeScope").asText("all"),
                    b.path("term").asText("all"), b.path("enabled").asBoolean(true),
                    b.path("sort").asInt(50));
        }
        taskRepo.save(t);
        return row(t.getId());
    }

    @DeleteMapping("/task-items/{id}")
    public Map<String, Object> deleteTask(@PathVariable String id, HttpServletRequest req) {
        gate(req);
        taskRepo.deleteById(id); // enabled=false 语义已由 PUT 支持；硬删仅影响未来勾选
        return row(id);
    }

    // ── 商店 CRUD ──

    @PostMapping("/shop-items")
    public Map<String, Object> upsertShop(@RequestBody JsonNode b, HttpServletRequest req) {
        gate(req);
        String id = b.path("id").asText(null);
        ShopItemEntity s;
        if (id != null && shopRepo.existsById(id)) {
            s = shopRepo.findById(id).orElseThrow();
            s.setName(b.path("name").asText(s.getName()));
            s.setIcon(b.path("icon").asText(s.getIcon()));
            s.setPrice(b.path("price").asInt(s.getPrice()));
            s.setEnabled(b.path("enabled").asBoolean(s.isEnabled()));
            s.setStock(b.path("stock").asText(s.getStock()));
        } else {
            s = new ShopItemEntity(id != null ? id : Ids.next("shop"), b.path("name").asText(),
                    b.path("icon").asText("🎁"), b.path("price").asInt(50),
                    b.path("enabled").asBoolean(true), b.path("stock").asText("unlimited"));
        }
        shopRepo.save(s);
        return row(s.getId());
    }

    @DeleteMapping("/shop-items/{id}")
    public Map<String, Object> deleteShop(@PathVariable String id, HttpServletRequest req) {
        gate(req);
        shopRepo.deleteById(id);
        return row(id);
    }

    // ── 习惯 CRUD ──

    @PostMapping("/habits")
    public Map<String, Object> upsertHabit(@RequestBody JsonNode b, HttpServletRequest req) {
        gate(req);
        String id = b.path("id").asText(null);
        HabitEntity h;
        if (id != null && habitRepo.existsById(id)) {
            h = habitRepo.findById(id).orElseThrow();
            h.setName(b.path("name").asText(h.getName()));
            h.setIcon(b.path("icon").asText(h.getIcon()));
            h.setCycleDays(b.path("cycleDays").asInt(h.getCycleDays()));
            if (b.hasNonNull("milestoneRewards")) {
                h.setMilestoneRewards(b.get("milestoneRewards").toString());
            }
            h.setEnabled(b.path("enabled").asBoolean(h.isEnabled()));
        } else {
            h = new HabitEntity(id != null ? id : Ids.next("hb"), b.path("name").asText(),
                    b.path("icon").asText("⭐"), b.path("cycleDays").asInt(21),
                    b.hasNonNull("milestoneRewards") ? b.get("milestoneRewards").toString() : "{\"7\":10,\"14\":20,\"21\":50}",
                    b.path("enabled").asBoolean(true));
        }
        habitRepo.save(h);
        return row(h.getId());
    }

    @DeleteMapping("/habits/{id}")
    public Map<String, Object> deleteHabit(@PathVariable String id, HttpServletRequest req) {
        gate(req);
        habitRepo.deleteById(id);
        return row(id);
    }

    // ── 档案 ──

    @PostMapping("/profiles")
    public ProfileService.ProfileView createProfile(@RequestBody JsonNode b, HttpServletRequest req) {
        gate(req);
        return profiles.create(b.path("name").asText(), b.path("avatar").asText("🐼"),
                b.path("grade").asInt(1), b.path("term").asText("上"));
    }

    @PatchMapping("/profiles/{id}")
    public ProfileService.ProfileView updateProfile(@PathVariable String id, @RequestBody JsonNode b,
                                                    HttpServletRequest req) {
        gate(req);
        return profiles.update(id, b.path("name").asText(null), b.path("avatar").asText(null),
                b.hasNonNull("grade") ? b.get("grade").asInt() : null, b.path("term").asText(null));
    }

    // ── 设置 ──

    @PostMapping("/settings")
    public Map<String, Object> updateSettings(@RequestBody JsonNode b, HttpServletRequest req) {
        gate(req);
        var s = settings.get();
        if (b.hasNonNull("checkinAmount")) s.setCheckinAmount(b.get("checkinAmount").asInt());
        if (b.hasNonNull("textbookCoinValue")) s.setTextbookCoinValue(b.get("textbookCoinValue").asInt());
        if (b.hasNonNull("levelThresholds")) s.setLevelThresholds(b.get("levelThresholds").toString());
        if (b.hasNonNull("levelNames")) s.setLevelNames(b.get("levelNames").toString());
        if (b.hasNonNull("lifeSettings")) s.setLifeSettings(b.get("lifeSettings").toString());
        settings.save(s);
        return Map.of("ok", true);
    }

    // ── 家长侧动作（M1.7/M2.4/M6.9/M3.4）──

    @PostMapping("/manual-tx")
    public Map<String, Object> manualTx(@RequestBody JsonNode b, HttpServletRequest req) {
        gate(req);
        String reason = b.path("reason").asText("");
        if (reason.trim().length() < 2) {
            throw new ApiException(400, "原因至少 2 个字");
        }
        String pid = b.path("profileId").asText(session.currentProfileId());
        var tx = ledger.appendTx(pid, TxType.MANUAL, b.path("amount").asInt(), null, null, reason);
        return Map.of("txId", tx.id(), "balance", ledger.balance(pid));
    }

    // ── 数据修正（M7.6/FR-26，v2.2）──

    @GetMapping("/profiles/{id}/txs")
    public List<Map<String, Object>> profileTxs(@PathVariable String id, HttpServletRequest req) {
        gate(req);
        return ledger.recentForAdmin(id, 50);
    }

    @PostMapping("/txs/adjust")
    public Map<String, Object> adjustTx(@RequestBody JsonNode b, HttpServletRequest req) {
        gate(req);
        return ledger.adjustTx(b.path("txId").asText(), b.path("newAmount").asInt(),
                b.path("reason").asText(""));
    }

    @PostMapping("/plans/check")
    public Map<String, Object> adminCheck(@RequestBody JsonNode b, HttpServletRequest req) {
        gate(req);
        String pid = b.path("profileId").asText(session.currentProfileId());
        return plan.toggleAdmin(pid, b.path("taskItemId").asText());
    }

    @PostMapping("/redemptions/{id}/confirm")
    public Map<String, Object> confirmRedeem(@PathVariable String id, HttpServletRequest req) {
        gate(req);
        return shop.confirm(id);
    }

    @PostMapping("/redemptions/{id}/cancel")
    public Map<String, Object> cancelRedeem(@PathVariable String id, HttpServletRequest req) {
        gate(req);
        return shop.cancel(id);
    }

    @PostMapping("/screen/bank-grant")
    public Map<String, Object> bankGrant(@RequestBody JsonNode b, HttpServletRequest req) {
        gate(req);
        String pid = b.path("profileId").asText(session.currentProfileId());
        return life.bankGrant(pid, b.path("minutes").asInt());
    }

    // ── 自定义内容（docs/06 T3.5）──

    @PostMapping("/nodes")
    public Map<String, Object> addNode(@RequestBody JsonNode b, HttpServletRequest req) {
        gate(req);
        String subject = DbSubjects.db(b.path("subject").asText());
        TreeNodeEntity n = new TreeNodeEntity(Ids.next("cust"), subject,
                b.path("grade").asInt(), b.path("term").asText("上"),
                b.path("parentRef").asText(null), b.path("kind").asText("leaf"),
                b.path("title").asText(), b.path("sort").asInt(900),
                b.path("branch").asText(null), "custom");
        nodeRepo.save(n);
        return row(n.getId());
    }

    @DeleteMapping("/nodes/{id}")
    public Map<String, Object> deleteNode(@PathVariable String id, HttpServletRequest req) {
        gate(req);
        TreeNodeEntity n = nodeRepo.findById(id).orElseThrow(() -> new ApiException(404, "节点不存在"));
        if (!"custom".equals(n.getOrigin())) {
            throw new ApiException(403, "内置内容不可删除");
        }
        quizRepo.findAll().stream().filter(q -> "custom".equals(q.getOrigin()) && id.equals(q.getLeafId()))
                .forEach(quizRepo::delete);
        nodeRepo.delete(n);
        return row(id);
    }

    /** 题库导入（FR-15）：JSON 体 = {leafId?, quizzes:[{type,stem,options,answer,answerAccepts,explanation}]}；leafId 缺省时自动建自定义叶。 */
    @PostMapping(value = "/quiz/import", consumes = MediaType.ALL_VALUE)
    public Map<String, Object> importQuizzes(@RequestBody String raw, HttpServletRequest req) {
        gate(req);
        JsonNode b;
        try {
            b = settings.mapper().readTree(raw);
        } catch (Exception e) {
            throw new ApiException(400, "JSON 解析失败");
        }
        String leafId = b.path("leafId").asText(null);
        if (leafId == null || leafId.isBlank()) {
            String subject = DbSubjects.db(b.path("subject").asText("cn"));
            TreeNodeEntity n = new TreeNodeEntity(Ids.next("cust"), subject,
                    b.path("grade").asInt(1), b.path("term").asText("上"), null,
                    "leaf", b.path("title").asText("自定义知识点"), 900, null, "custom");
            nodeRepo.save(n);
            leafId = n.getId();
        } else if (leafId.startsWith("cust_") && !nodeRepo.existsById(leafId)) {
            throw new ApiException(404, "自定义知识点叶不存在，请先创建");
        }
        int count = 0;
        String subject = b.path("subject").asText("cn");
        for (JsonNode q : b.path("quizzes")) {
            String type = q.path("type").asText("single");
            if ("fill".equals(type)) {
                type = "blank"; // v2.1：别名归一
            }
            com.fasterxml.jackson.databind.node.ArrayNode opts = null;
            if (q.has("options") && q.get("options").isArray()) {
                opts = normalizeOptions((com.fasterxml.jackson.databind.node.ArrayNode) q.get("options"));
            }
            String answer = q.path("answer").asText();
            if (opts != null) {
                for (JsonNode o : opts) { // 答案写成选项文本 → 映射到字母 id（M5.1 精确匹配字母）
                    if (o.path("text").asText().equals(answer)) {
                        answer = o.path("id").asText();
                        break;
                    }
                }
            }
            quizRepo.save(new QuizEntity(Ids.next("qz"), leafId, DbSubjects.db(subject), type,
                    q.path("stem").asText(), opts != null ? opts.toString() : null, answer,
                    q.has("answerAccepts") ? q.get("answerAccepts").toString() : null,
                    q.path("explanation").asText(null), "custom"));
            count++;
        }
        return Map.of("leafId", leafId, "imported", count);
    }

    /** v2.1（M7.3）：字符串选项自动编 A–D 字母；已是 {id,text} 对象则原样返回。 */
    private static com.fasterxml.jackson.databind.node.ArrayNode normalizeOptions(
            com.fasterxml.jackson.databind.node.ArrayNode in) {
        boolean allText = true;
        for (JsonNode o : in) {
            if (!o.isTextual()) {
                allText = false;
            }
        }
        if (!allText) {
            return in;
        }
        var out = in.arrayNode();
        for (int i = 0; i < in.size(); i++) {
            out.addObject().put("id", String.valueOf((char) ('A' + i))).put("text", in.get(i).asText());
        }
        return out;
    }

    @DeleteMapping("/quizzes/{id}")
    public Map<String, Object> deleteQuiz(@PathVariable String id, HttpServletRequest req) {
        gate(req);
        QuizEntity q = quizRepo.findById(id).orElseThrow(() -> new ApiException(404, "题目不存在"));
        if (!"custom".equals(q.getOrigin())) {
            throw new ApiException(403, "内置题目不可删除（可新增同叶自定义题）");
        }
        quizRepo.delete(q);
        return row(id);
    }

    // ── 危险区（M7.4：确认词）──

    @PostMapping("/danger/reset-profile")
    public Map<String, Object> resetProfile(@RequestBody JsonNode b, HttpServletRequest req) {
        gate(req);
        return profiles.resetProfileData(b.path("profileId").asText(), b.path("confirmWord").asText());
    }

    @PostMapping("/danger/delete-profile")
    public Map<String, Object> deleteProfile(@RequestBody JsonNode b, HttpServletRequest req) {
        gate(req);
        profiles.deleteProfile(b.path("profileId").asText(), b.path("confirmWord").asText());
        return Map.of("deleted", true);
    }

    // ── 工具 ──

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (kv.length == 1) {
            m.put("id", kv[0]);
            return m;
        }
        String[] keys = {"id", "name", "icon", "amount", "category", "gradeScope", "term", "enabled", "sort"};
        for (int i = 0; i < kv.length && i < keys.length; i++) {
            m.put(keys[i], kv[i]);
        }
        return m;
    }
}
