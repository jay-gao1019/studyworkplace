package com.studdy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 种子内容契约校验（docs/10 §6）。
 * 文件存在性 → JSON 可解析 → 结构约束（ID 正则与全局唯一、数量下限、题目 options=4、
 * answer 属于 options 或 answerAccepts）→ 打印统计报表。
 */
class SeedContractTest {

    static final ObjectMapper M = new ObjectMapper();
    static final String[] TEXTBOOKS = {"seed/textbook-cn.json", "seed/textbook-math.json", "seed/textbook-en.json"};
    static final String[] KNOWLEDGE = {"seed/knowledge-cn.json", "seed/knowledge-math.json", "seed/knowledge-en.json"};

    static final Pattern TB_GROUP = Pattern.compile("^tb-(cny|mat|eng)[1-6][ab]-g\\d{2}$");
    static final Pattern TB_LEAF = Pattern.compile("^tb-(cny|mat|eng)[1-6][ab]-l\\d{2}$");
    static final Pattern KN_MODULE = Pattern.compile("^kn-(cny|mat|eng)[1-6][ab]-m\\d{2}$");
    static final Pattern KN_UNIT = Pattern.compile("^kn-(cny|mat|eng)[1-6][ab]-u\\d{2}$");
    static final Pattern KN_LEAF = Pattern.compile("^kn-(cny|mat|eng)[1-6][ab]-f\\d{2}$");
    static final Pattern QUIZ_ID = Pattern.compile("^qz-kn-(cny|mat|eng)[1-6][ab]-f\\d{2}-\\d+$");

    static final List<String> errors = new ArrayList<>();
    static final Map<String, String> globalIds = new HashMap<>();
    static final StringBuilder report = new StringBuilder();

    static JsonNode load(String cp) throws Exception {
        try (InputStream in = SeedContractTest.class.getClassLoader().getResourceAsStream(cp)) {
            assertNotNull(in, "缺少文件: " + cp);
            return M.readTree(in);
        }
    }

    /** ID 注册并检查全局唯一。 */
    static void id(String id, Pattern p, String what, String file) {
        if (id == null || !p.matcher(id).matches()) {
            errors.add(file + ": " + what + " ID 不合规则: " + id);
            return;
        }
        String prev = globalIds.put(id, file);
        if (prev != null) errors.add("ID 重复: " + id + " (" + prev + " & " + file + ")");
    }

    static String termKey(int grade, String term) {
        return "grade" + grade + ("上".equals(term) ? "a" : "b");
    }

    // ---------- 教材 ----------
    static void checkTextbook(String file) throws Exception {
        JsonNode root = load(file);
        String subject = root.path("subject").asText();
        assertTrue(List.of("cn", "math", "en").contains(subject), file + " subject");
        JsonNode terms = root.path("terms");
        assertEquals(12, terms.size(), file + " 应含 12 个年级×学期 term");
        int totalLeaves = 0, totalGroups = 0;
        Map<String, Integer> mathOlympic = new HashMap<>();
        for (JsonNode t : terms) {
            int grade = t.path("grade").asInt();
            String term = t.path("term").asText();
            String tk = file + " " + termKey(grade, term);
            List<String> pinyin = List.of("上", "下");
            assertTrue(pinyin.contains(term), tk + " term 应为 上/下");
            JsonNode modules = t.path("modules");
            assertTrue(modules.size() >= 4, tk + " 分组数 <4: " + modules.size());
            int leaves = 0;
            for (JsonNode g : modules) {
                id(g.path("id").asText(), TB_GROUP, "分组", tk);
                assertFalse(g.path("title").asText().isBlank(), tk + " 分组缺标题");
                String branch = g.path("branch").asText("");
                JsonNode lvs = g.path("leaves");
                for (JsonNode l : lvs) {
                    id(l.path("id").asText(), TB_LEAF, "叶子", tk);
                    String title = l.path("title").asText();
                    assertFalse(title.isBlank(), tk + " 叶子缺标题: " + l.path("id"));
                    leaves++;
                    if ("奥数".equals(branch)) mathOlympic.merge(termKey(grade, term), 1, Integer::sum);
                }
                // 英语模块下可选 groups 再挂叶子
                for (JsonNode sub : g.path("groups")) {
                    for (JsonNode l : sub.path("leaves")) {
                        id(l.path("id").asText(), TB_LEAF, "叶子", tk);
                        assertFalse(l.path("title").asText().isBlank(), tk + " 叶子缺标题");
                        leaves++;
                    }
                }
            }
            assertTrue(leaves >= 10, tk + " 叶子数 <10: " + leaves);
            totalLeaves += leaves;
            totalGroups += modules.size();
        }
        if ("math".equals(subject)) {
            for (JsonNode t : terms) {
                String tk2 = termKey(t.path("grade").asInt(), t.path("term").asText());
                assertTrue(mathOlympic.getOrDefault(tk2, 0) >= 3, "数学奥数 " + tk2 + " 专题叶子 <3");
            }
        }
        report.append(String.format("教材 %-28s terms=12 groups=%d leaves=%d%n", file, totalGroups, totalLeaves));
    }

    // ---------- 知识树 ----------
    static void checkQuiz(JsonNode q, String leafId, String tk, int[] cnt) {
        String qid = q.path("id").asText();
        id(qid, QUIZ_ID, "题目", tk);
        assertTrue(qid.startsWith("qz-" + leafId + "-"), tk + " 题目 ID 前缀应含叶 ID: " + qid);
        String type = q.path("type").asText();
        assertTrue(List.of("single", "blank").contains(type), tk + " 非法题型: " + type);
        assertFalse(q.path("stem").asText().isBlank(), tk + " 题干为空: " + qid);
        assertFalse(q.path("explanation").asText().isBlank(), qid + " explanation 为空");
        String answer = q.path("answer").asText();
        if ("single".equals(type)) {
            JsonNode opts = q.path("options");
            assertEquals(4, opts.size(), qid + " options 应为 4");
            List<String> ids = new ArrayList<>();
            for (JsonNode o : opts) {
                assertFalse(o.path("text").asText().isBlank(), qid + " 选项 text 为空");
                ids.add(o.path("id").asText());
            }
            assertTrue(ids.contains(answer), qid + " answer 不在 options: " + answer);
            cnt[0]++;
        } else {
            assertFalse(answer.isBlank(), qid + " 填空 answer 为空");
            JsonNode accepts = q.path("answerAccepts");
            assertTrue(accepts.isArray() && accepts.size() >= 1, qid + " answerAccepts 缺失");
            boolean contains = false;
            for (JsonNode a : accepts) if (a.asText().equals(answer)) contains = true;
            assertTrue(contains, qid + " answer 应包含于 answerAccepts");
            cnt[1]++;
        }
    }

    static void checkKnowledge(String file) throws Exception {
        JsonNode root = load(file);
        JsonNode terms = root.path("terms");
        String subject = root.path("subject").asText();
        int expectTerms = "math".equals(subject) ? 24 : 12; // math 为校内+奥数双分支，各 12 项
        assertEquals(expectTerms, terms.size(), file + " 应含 " + expectTerms + " 项 terms（1–6 年级 × 上下 × 分支）");
        int totalLeaves = 0, totalQuizzes = 0;
        List<String> thinLeaves = new ArrayList<>();
        for (JsonNode t : terms) {
            int grade = t.path("grade").asInt();
            String branch = t.path("branch").asText("校内");
            String tk = file + " " + termKey(grade, t.path("term").asText()) + "/" + branch;
            for (JsonNode m : t.path("modules")) {
                id(m.path("id").asText(), KN_MODULE, "模块", tk);
                for (JsonNode u : m.path("units")) {
                    id(u.path("id").asText(), KN_UNIT, "单元", tk);
                    for (JsonNode leaf : u.path("leaves")) {
                        String leafId = leaf.path("id").asText();
                        id(leafId, KN_LEAF, "知识点叶", tk);
                        JsonNode quizzes = leaf.path("quizzes");
                        int min = (grade >= 4 && "校内".equals(branch)) ? 3 : 2;
                        assertTrue(quizzes.size() >= 2, tk + " 叶题目 <2: " + leafId);
                        int[] cnt = {0, 0};
                        for (JsonNode q : quizzes) checkQuiz(q, leafId, tk, cnt);
                        assertTrue(cnt[0] >= 1 && cnt[1] >= 1, leafId + " 应单选与填空各至少 1");
                        if (quizzes.size() < min) thinLeaves.add(leafId + "(" + quizzes.size() + "/" + min + ")");
                        totalLeaves++;
                        totalQuizzes += quizzes.size();
                    }
                }
            }
        }
        report.append(String.format("知识 %-28s leaves=%d quizzes=%d 4-6年级欠题叶=%d%n", file, totalLeaves, totalQuizzes, thinLeaves.size()));
        if (!thinLeaves.isEmpty()) {
            errors.add(file + " 4–6 年级每叶≥3 题未达标（v1.2 加严项）: " + String.join(", ", thinLeaves));
        }
    }

    @BeforeAll
    static void runAll() throws Exception {
        for (String f : TEXTBOOKS) checkTextbook(f);
        for (String f : KNOWLEDGE) checkKnowledge(f);
    }

    @Test
    void structureAndContent() {
        System.out.println("==== 种子内容统计报表 ====");
        System.out.println(report);
        System.out.println("全局唯一 ID 数: " + globalIds.size());
        if (!errors.isEmpty()) {
            System.out.println("==== 契约违规 (" + errors.size() + ") ====");
            errors.stream().limit(60).forEach(System.out::println);
        }
        assertTrue(errors.isEmpty(), "种子契约违规 " + errors.size() + " 项，首条: " + (errors.isEmpty() ? "" : errors.get(0)));
    }
}
