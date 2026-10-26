package com.studdy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.studdy.domain.coin.CoinTx;
import com.studdy.domain.coin.TxType;
import com.studdy.domain.knowledge.ProgressCalc;
import com.studdy.domain.util.DayKey;
import com.studdy.entity.TextbookCheckEntity;
import com.studdy.entity.TreeNodeEntity;
import com.studdy.repo.TextbookCheckRepository;
import com.studdy.repo.TreeNodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 教材目录树（seed builtin + tree_nodes custom）与打卡（docs/03 M4）。 */
@Service
public class TextbookService {

    private final SeedService seed;
    private final TreeNodeRepository nodes;
    private final TextbookCheckRepository checks;
    private final ProfileService profiles;
    private final LedgerService ledger;
    private final SettingsService settings;

    public TextbookService(SeedService seed, TreeNodeRepository nodes, TextbookCheckRepository checks,
                           ProfileService profiles, LedgerService ledger, SettingsService settings) {
        this.seed = seed;
        this.nodes = nodes;
        this.checks = checks;
        this.profiles = profiles;
        this.ledger = ledger;
        this.settings = settings;
    }

    /** GET /api/textbook/{subject}：合并目录树 + 勾选态 + 进度（M4.3/4.4）。 */
    public Map<String, Object> tree(String profileId, String subject, Integer grade, String term) {
        var p = profiles.get(profileId);
        int g = grade != null ? grade : p.grade();
        String t = term != null ? term : p.term();
        String dbSubject = DbSubjects.db(subject);

        Set<String> checked = checks.findByProfileId(profileId).stream()
                .map(TextbookCheckEntity::getNodeId).collect(Collectors.toCollection(LinkedHashSet::new));

        List<Map<String, Object>> groups = new ArrayList<>();
        List<ProgressCalc.LeafWithBranch> leavesForProgress = new ArrayList<>();
        JsonNode termNode = seed.textbookTerm(DbSubjects.seed(subject), g, t);
        for (JsonNode grp : termNode.path("modules")) {
            String branch = grp.path("branch").asText("校内");
            List<Map<String, Object>> leafList = new ArrayList<>();
            for (JsonNode l : grp.path("leaves")) {
                leafList.add(leafNode(l.path("id").asText(), l.path("title").asText(), checked));
                leavesForProgress.add(new ProgressCalc.LeafWithBranch(l.path("id").asText(), branch));
            }
            // 该分组下自定义叶子
            for (TreeNodeEntity c : nodes.findBySubjectAndGradeAndTerm(dbSubject, g, t)) {
                if ("leaf".equals(c.getKind()) && grp.path("id").asText().equals(c.getParentRef())) {
                    leafList.add(leafNode(c.getId(), c.getTitle(), checked));
                    leavesForProgress.add(new ProgressCalc.LeafWithBranch(c.getId(),
                            c.getBranch() == null ? "校内" : c.getBranch()));
                }
            }
            // 英语模块下的可选 sub-groups
            for (JsonNode sub : grp.path("groups")) {
                List<Map<String, Object>> subLeaves = new ArrayList<>();
                for (JsonNode l : sub.path("leaves")) {
                    subLeaves.add(leafNode(l.path("id").asText(), l.path("title").asText(), checked));
                    leavesForProgress.add(new ProgressCalc.LeafWithBranch(l.path("id").asText(), branch));
                }
                if (!subLeaves.isEmpty()) {
                    leafList.addAll(subLeaves);
                }
            }
            Map<String, Object> gm = new LinkedHashMap<>();
            gm.put("id", grp.path("id").asText());
            gm.put("title", grp.path("title").asText());
            gm.put("branch", branch);
            if (grp.path("ebookPage").isNumber()) {
                gm.put("ebookPage", grp.path("ebookPage").asInt()); // M4.7 v2.2：单元→电子书起始页
            }
            gm.put("leaves", leafList);
            groups.add(gm);
        }
        // 顶层自定义分组（parentRef 为 null）
        for (TreeNodeEntity c : nodes.findBySubjectAndGradeAndTerm(dbSubject, g, t)) {
            if ("group".equals(c.getKind()) && c.getParentRef() == null) {
                List<Map<String, Object>> leafList = new ArrayList<>();
                for (TreeNodeEntity ch : nodes.findBySubjectAndGradeAndTerm(dbSubject, g, t)) {
                    if ("leaf".equals(ch.getKind()) && c.getId().equals(ch.getParentRef())) {
                        leafList.add(leafNode(ch.getId(), ch.getTitle(), checked));
                        leavesForProgress.add(new ProgressCalc.LeafWithBranch(ch.getId(),
                                ch.getBranch() == null ? "校内" : ch.getBranch()));
                    }
                }
                Map<String, Object> gm = new LinkedHashMap<>();
                gm.put("id", c.getId());
                gm.put("title", c.getTitle());
                gm.put("branch", c.getBranch() == null ? "校内" : c.getBranch());
                gm.put("leaves", leafList);
                groups.add(gm);
            }
        }

        ProgressCalc.SubjectProgress prog = ProgressCalc.progressAll(leavesForProgress, checked);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("subject", DbSubjects.seed(subject));
        out.put("grade", g);
        out.put("term", t);
        out.put("groups", groups);
        out.put("progress", Map.of("schoolPercent", prog.schoolPercent(),
                "olympiadPercent", prog.olympiadPercent(), "hasOlympiad", prog.hasOlympiad()));
        out.put("coinValue", settings.textbookCoinValue());
        return out;
    }

    private Map<String, Object> leafNode(String id, String title, Set<String> checked) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("title", title);
        m.put("checked", checked.contains(id));
        return m;
    }

    /** M4.2 叶子打卡切换；进度只统计叶子。 */
    @Transactional
    public Map<String, Object> toggle(String profileId, String nodeId) {
        String dayKey = DayKey.ofToday(LocalDateTime.now(ZoneId.systemDefault()));
        var pk = new TextbookCheckEntity.Pk(profileId, nodeId);
        var existing = checks.findById(pk);
        if (existing.isPresent()) {
            TextbookCheckEntity c = existing.get();
            if (c.getTxId() != null) {
                ledger.reverse(profileId, c.getTxId());
            }
            checks.delete(c);
            return Map.of("checked", false, "balance", ledger.balance(profileId));
        }
        // 自定义叶子需在库中；内置叶子 id 以 tb- 开头直接放行
        if (!nodeId.startsWith("tb-") && !nodes.existsById(nodeId)) {
            throw new ApiException(404, "条目不存在");
        }
        CoinTx tx = ledger.appendTx(profileId, TxType.TEXTBOOK, settings.textbookCoinValue(), nodeId, null, null);
        checks.save(new TextbookCheckEntity(profileId, nodeId, dayKey, Instant.now(), tx.id()));
        return Map.of("checked", true, "balance", ledger.balance(profileId),
                "amount", settings.textbookCoinValue());
    }

    /** 三科进度徽标（M4.6 bootstrap 用）。 */
    public Map<String, Object> progressSummary(String profileId) {
        var p = profiles.get(profileId);
        Map<String, Object> out = new LinkedHashMap<>();
        for (String subject : List.of("cn", "math", "en")) {
            Map<String, Object> t = tree(profileId, subject, p.grade(), p.term());
            out.put(subject, t.get("progress"));
        }
        return out;
    }
}
