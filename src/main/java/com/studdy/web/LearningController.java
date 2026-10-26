package com.studdy.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.studdy.service.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 学习台 API：计划/签到/教材/知识树/自测（docs/02 §4 学习+知识段）。 */
@RestController
@RequestMapping("/api")
public class LearningController {

    private final SessionService session;
    private final ProfileService profiles;
    private final PlanService plan;
    private final LedgerService ledger;
    private final TextbookService textbook;
    private final KnowledgeService knowledge;
    private final LifeService life;
    private final StatsService statsService;

    public LearningController(SessionService session, ProfileService profiles, PlanService plan,
                              LedgerService ledger, TextbookService textbook, KnowledgeService knowledge,
                              LifeService life, StatsService statsService) {
        this.session = session;
        this.profiles = profiles;
        this.plan = plan;
        this.ledger = ledger;
        this.textbook = textbook;
        this.knowledge = knowledge;
        this.life = life;
        this.statsService = statsService;
    }

    private String pid() {
        String id = session.currentProfileId();
        if (id == null) {
            throw new ApiException(400, "请先创建孩子档案");
        }
        return id;
    }

    // ── 计划与签到 ──

    @GetMapping("/plans/today")
    public Map<String, Object> today() {
        return Map.of("items", plan.todayView(pid()), "deductedToday", plan.todayDeducted(pid()),
                "coins", ledger.summary(pid()));
    }

    @PostMapping("/plans/check")
    public Map<String, Object> check(@RequestBody JsonNode body) {
        return plan.toggleChild(pid(), body.path("taskItemId").asText());
    }

    @PostMapping("/checkin")
    public Map<String, Object> checkin() {
        return plan.checkin(pid());
    }

    /** P1 悬浮卡双线完成率（docs/02 v2.1）。 */
    @GetMapping("/plans/weekly")
    public Map<String, Object> weekly() {
        String p = pid();
        var prof = profiles.get(p);
        return Map.of("study", plan.weeklyStudy(p, prof.grade(), prof.term()),
                "life", life.weeklyLife(p));
    }

    // ── 教材 ──

    @GetMapping("/textbook/{subject}")
    public Map<String, Object> textbookTree(@PathVariable String subject,
                                            @RequestParam(required = false) Integer grade,
                                            @RequestParam(required = false) String term) {
        return textbook.tree(pid(), subject, grade, term);
    }

    @PostMapping("/textbook/check")
    public Map<String, Object> textbookCheck(@RequestBody JsonNode body) {
        return textbook.toggle(pid(), body.path("nodeId").asText());
    }

    // ── 知识树与自测 ──

    @GetMapping("/knowledge/{subject}/{grade}/{term}")
    public Map<String, Object> knowledgeTree(@PathVariable String subject, @PathVariable int grade,
                                             @PathVariable String term,
                                             @RequestParam(required = false) String filter) {
        return knowledge.tree(pid(), subject, grade, term, filter);
    }

    @GetMapping("/knowledge/leaf/{leafId}/quiz")
    public List<Map<String, Object>> quiz(@PathVariable String leafId) {
        return knowledge.quizView(leafId);
    }

    @PostMapping("/knowledge/leaf/{leafId}/attempt")
    public Map<String, Object> attempt(@PathVariable String leafId, @RequestBody JsonNode body) {
        return knowledge.submitRound(pid(), leafId, body.path("answers"));
    }

    @GetMapping("/knowledge/attack")
    public List<Map<String, Object>> attack(@RequestParam(required = false) String subject,
                                            @RequestParam(required = false) Integer grade,
                                            @RequestParam(required = false) String term) {
        String p = pid();
        var prof = profiles.get(p);
        int g = grade != null ? grade : prof.grade();
        String t = term != null ? term : prof.term();
        if (subject == null) {
            java.util.List<Map<String, Object>> all = new java.util.ArrayList<>();
            for (String s : List.of("cn", "math", "en")) {
                all.addAll(knowledge.attackList(p, s, g, t));
            }
            return all;
        }
        return knowledge.attackList(p, subject, g, t);
    }

    @GetMapping("/coins/stats")
    public List<com.studdy.domain.coin.LedgerRule.Bucket> stats(@RequestParam(defaultValue = "day") String period) {
        return ledger.stats(pid(), period);
    }

    /** 学习台统计报表（M10/FR-25，v2.2）。 */
    @GetMapping("/stats/report")
    public Map<String, Object> statsReport(@RequestParam(defaultValue = "week") String period) {
        return statsService.report(pid(), period);
    }

    @GetMapping("/ledger")
    public List<com.studdy.domain.coin.CoinTx> ledgerOf() {
        return ledger.txsOf(pid());
    }
}
