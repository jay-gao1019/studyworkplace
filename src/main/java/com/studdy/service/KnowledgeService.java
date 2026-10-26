package com.studdy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studdy.domain.coin.TxType;
import com.studdy.domain.mastery.Mastery;
import com.studdy.domain.mastery.MasteryEngine;
import com.studdy.domain.quiz.GraderEngine;
import com.studdy.domain.quiz.Quiz;
import com.studdy.entity.AttemptRoundEntity;
import com.studdy.entity.QuizEntity;
import com.studdy.entity.TreeNodeEntity;
import com.studdy.repo.AttemptRoundRepository;
import com.studdy.repo.QuizRepository;
import com.studdy.repo.TreeNodeRepository;
import com.studdy.util.Ids;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 知识体系树 + 自测判分 + 掌握度（docs/03 M5；docs/02 §5 答案永不下发）。 */
@Service
public class KnowledgeService {

    private final SeedService seed;
    private final TreeNodeRepository nodes;
    private final QuizRepository quizzes;
    private final AttemptRoundRepository attempts;
    private final LedgerService ledger;
    private final SettingsService settings;
    private final ObjectMapper mapper = new ObjectMapper();

    public KnowledgeService(SeedService seed, TreeNodeRepository nodes, QuizRepository quizzes,
                            AttemptRoundRepository attempts, LedgerService ledger, SettingsService settings) {
        this.seed = seed;
        this.nodes = nodes;
        this.quizzes = quizzes;
        this.attempts = attempts;
        this.ledger = ledger;
        this.settings = settings;
    }

    /** 叶子当前题目集 = builtin（seed JSON）+ custom（quizzes 表 origin=custom）。 */
    public List<Quiz> quizzesOfLeaf(String leafId) {
        List<Quiz> out = new ArrayList<>(seed.builtinQuizzesOfLeaf(leafId));
        for (QuizEntity e : quizzes.findByLeafId(leafId)) {
            if ("custom".equals(e.getOrigin())) {
                out.add(toDomain(e));
            }
        }
        return out;
    }

    /** GET 答题接口视图：剥掉 answer/answerAccepts/explanation（判分在服务端）。 */
    public List<Map<String, Object>> quizView(String leafId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Quiz q : quizzesOfLeaf(leafId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", q.id());
            m.put("type", q.type() == Quiz.Type.SINGLE ? "single" : "blank");
            m.put("stem", q.stem());
            if (q.type() == Quiz.Type.SINGLE) {
                m.put("options", q.options().stream()
                        .map(o -> Map.of("id", o.id(), "text", o.text())).toList());
            }
            out.add(m);
        }
        return out;
    }

    /** 树 + 掌握度着色（M5.5/5.6）；支持六档筛选（M5.8）。 */
    public Map<String, Object> tree(String profileId, String subject, int grade, String term, String filter) {
        List<SeedService.BuiltinLeaf> builtinLeaves = seed.allBuiltinLeaves(subject, grade, term);
        String dbSubject = DbSubjects.db(DbSubjects.seed(subject));

        // 最近一轮（按叶子）
        Map<String, AttemptRoundEntity> latest = new LinkedHashMap<>();
        for (AttemptRoundEntity a : attempts.findByProfileIdOrderByFinishedAtDesc(profileId)) {
            latest.putIfAbsent(a.getLeafId(), a);
        }

        List<Map<String, Object>> modules = new ArrayList<>();
        List<Mastery> allMastery = new ArrayList<>();
        int nodeCount = 0;
        Map<String, List<SeedService.BuiltinLeaf>> byModuleUnit = new LinkedHashMap<>();
        JsonNode termNode = seed.root("knowledge-" + DbSubjects.seed(subject) + ".json");

        // 遍历 seed 结构组树（modules→units→leaves）
        for (JsonNode t : termNode.path("terms")) {
            if (t.path("grade").asInt() != grade || !t.path("term").asText().equals(
                    term)) {
                continue;
            }
            String branch = t.path("branch").asText("校内");
            for (JsonNode mod : t.path("modules")) {
                Map<String, Object> mm = new LinkedHashMap<>();
                mm.put("id", mod.path("id").asText());
                mm.put("title", mod.path("title").asText());
                mm.put("branch", branch);
                List<Map<String, Object>> unitList = new ArrayList<>();
                nodeCount++;
                for (JsonNode unit : mod.path("units")) {
                    Map<String, Object> um = new LinkedHashMap<>();
                    um.put("id", unit.path("id").asText());
                    um.put("title", unit.path("title").asText());
                    List<Map<String, Object>> leafList = new ArrayList<>();
                    nodeCount++;
                    for (JsonNode leaf : unit.path("leaves")) {
                        String leafId = leaf.path("id").asText();
                        Mastery ms = masteryOf(latest.get(leafId), quizzesOfLeaf(leafId));
                        allMastery.add(ms);
                        if (!passFilter(ms, filter)) {
                            continue;
                        }
                        Map<String, Object> lm = new LinkedHashMap<>();
                        lm.put("id", leafId);
                        lm.put("title", leaf.path("title").asText());
                        lm.put("mastery", ms.wire());
                        lm.put("masteryLabel", ms.label());
                        lm.put("quizCount", quizzesOfLeaf(leafId).size());
                        leafList.add(lm);
                    }
                    if (!leafList.isEmpty() || filter == null || "all".equals(filter)) {
                        um.put("leaves", leafList);
                        unitList.add(um);
                        if (!leafList.isEmpty()) {
                            nodeCount++;
                        }
                    }
                }
                mm.put("units", unitList);
                if (!unitList.isEmpty()) {
                    modules.add(mm);
                }
            }
        }
        // 自定义叶子（挂在 custom group 节点或首个模块下）
        for (TreeNodeEntity cn : nodes.findBySubjectAndGradeAndTerm(dbSubject, grade, term)) {
            if ("leaf".equals(cn.getKind())) {
                Mastery ms = masteryOf(latest.get(cn.getId()), quizzesOfLeaf(cn.getId()));
                allMastery.add(ms);
                if (passFilter(ms, filter)) {
                    Map<String, Object> lm = new LinkedHashMap<>();
                    lm.put("id", cn.getId());
                    lm.put("title", cn.getTitle());
                    lm.put("mastery", ms.wire());
                    lm.put("masteryLabel", ms.label());
                    lm.put("quizCount", quizzesOfLeaf(cn.getId()).size());
                    lm.put("custom", true);
                    modules.add(Map.of("id", cn.getId(), "title", cn.getTitle(),
                            "branch", "校内", "units", List.of(Map.of("id", cn.getId(),
                                    "title", "", "leaves", List.of(lm)))));
                }
            }
        }

        MasteryEngine.Stats st = MasteryEngine.stats(allMastery.size(), nodeCount, allMastery);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("modules", modules);
        out.put("stats", Map.of("leafCount", st.leafCount(), "nodeCount", st.nodeCount(),
                "answeredLeaves", st.answeredLeaves(),
                "goodRatio", Math.round(st.proficientPlusMasteredRatio() * 1000) / 10.0));
        return out;
    }

    /** 攻关清单（M5.8："未掌握"红点列表）。 */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> attackList(String profileId, String subject, int grade, String term) {
        Map<String, Object> t = tree(profileId, subject, grade, term, "weak");
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> mod : (List<Map<String, Object>>) t.get("modules")) {
            Object units = mod.get("units");
            if (!(units instanceof List)) {
                continue;
            }
            for (Map<String, Object> unit : (List<Map<String, Object>>) units) {
                Object leaves = unit.get("leaves");
                if (!(leaves instanceof List)) {
                    continue;
                }
                for (Map<String, Object> leaf : (List<Map<String, Object>>) leaves) {
                    out.add(leaf);
                }
            }
        }
        return out;
    }

    Mastery masteryOf(AttemptRoundEntity latest, List<Quiz> currentQuizzes) {
        String currentHash = GraderEngine.quizSetHash(currentQuizzes);
        if (latest == null) {
            return currentQuizzes.isEmpty() ? Mastery.UNANSWERED : Mastery.UNANSWERED;
        }
        double rate = latest.getRate() == null ? 0 : latest.getRate().doubleValue();
        return MasteryEngine.displayState(latest.getQuizSetHash(), currentHash, rate);
    }

    private boolean passFilter(Mastery m, String filter) {
        if (filter == null || "all".equals(filter)) {
            return true;
        }
        return switch (filter) {
            case "proficient", "mastered", "partial", "weak", "unanswered" -> m.wire().equals(filter);
            case "needs_retest" -> m == Mastery.NEEDS_RETEST;
            default -> true;
        };
    }

    /**
     * 整轮提交判分（M5.4）：answers = [{quizId, given}]，必须覆盖当前题目集全部题目。
     * 落 AttemptRound；首轮达"精通"奖励 +textbookCoinValue（M5.10，每叶至多一次）。
     */
    @Transactional
    public Map<String, Object> submitRound(String profileId, String leafId, JsonNode answersArr) {
        List<Quiz> qs = quizzesOfLeaf(leafId);
        if (qs.isEmpty()) {
            throw new ApiException(400, "该知识点暂无题目");
        }
        Map<String, String> given = new LinkedHashMap<>();
        for (JsonNode a : answersArr) {
            given.put(a.path("quizId").asText(), a.path("given").asText(""));
        }
        if (given.size() != qs.size() || !qs.stream().allMatch(q -> given.containsKey(q.id()))) {
            throw new ApiException(400, "一轮需答完当前全部题目（" + qs.size() + " 题）");
        }
        int correct = 0;
        ArrayNode detail = mapper.createArrayNode();
        for (Quiz q : qs) {
            boolean ok = GraderEngine.grade(q, given.get(q.id()));
            if (ok) {
                correct++;
            }
            ObjectNode d = detail.addObject();
            d.put("quizId", q.id());
            d.put("given", given.get(q.id()));
            d.put("correct", ok);
            d.put("answer", q.answer());
            if (q.explanation() != null) {
                d.put("explanation", q.explanation());
            }
        }
        double rate = (double) correct / qs.size();
        Mastery m = MasteryEngine.fromRate(rate);
        String hash = GraderEngine.quizSetHash(qs);

        // 是否"首次达精通"（M5.10）：本叶历史有效轮次从未达到过精通
        boolean everProficient = attempts.findByProfileIdAndLeafIdOrderByFinishedAtDesc(profileId, leafId)
                .stream().anyMatch(a -> hash.equals(a.getQuizSetHash())
                        && a.getRate().doubleValue() >= 0.9);
        boolean alreadyRewarded = ledger.txsOf(profileId).stream().anyMatch(t ->
                t.type() == TxType.TEXTBOOK && leafId.equals(t.srcId()));

        attempts.save(new AttemptRoundEntity(Ids.next("at"), profileId, leafId, hash,
                detail.toString(), BigDecimal.valueOf(rate).setScale(3, RoundingMode.HALF_UP),
                m.wire(), Instant.now()));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("correct", correct);
        out.put("total", qs.size());
        out.put("rate", Math.round(rate * 100));
        out.put("mastery", m.wire());
        out.put("masteryLabel", m.label());
        out.put("results", detail);
        if (m == Mastery.PROFICIENT && !everProficient && !alreadyRewarded) {
            var tx = ledger.appendTx(profileId, TxType.TEXTBOOK, settings.textbookCoinValue(),
                    leafId, null, "首次精通");
            out.put("rewarded", settings.textbookCoinValue());
            out.put("balance", ledger.balance(profileId));
            out.put("rewardTxId", tx.id());
        }
        return out;
    }

    Quiz toDomain(QuizEntity e) {
        List<Quiz.Option> options = new ArrayList<>();
        try {
            if (e.getOptions() != null) {
                for (JsonNode o : mapper.readTree(e.getOptions())) {
                    options.add(new Quiz.Option(o.path("id").asText(), o.path("text").asText()));
                }
            }
        } catch (Exception ignored) {
        }
        List<String> accepts = new ArrayList<>();
        try {
            if (e.getAnswerAccepts() != null) {
                mapper.readTree(e.getAnswerAccepts()).forEach(n -> accepts.add(n.asText()));
            }
        } catch (Exception ignored) {
        }
        Quiz.Type type = "single".equals(e.getType()) ? Quiz.Type.SINGLE : Quiz.Type.BLANK;
        return new Quiz(e.getId(), e.getLeafId(), type, e.getStem(), options,
                e.getAnswer(), accepts, e.getExplanation());
    }
}
