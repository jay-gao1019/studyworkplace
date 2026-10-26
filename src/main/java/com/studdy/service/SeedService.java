package com.studdy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studdy.domain.quiz.Quiz;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 种子内容服务（docs/02 §5）：读取 classpath:/seed/*.json（打包进 jar），
 * 解析缓存供判分/哈希/树查询使用；/seed 静态伺服供前端拉取原始文件。
 */
@Service
public class SeedService {

    private static final Logger log = LoggerFactory.getLogger(SeedService.class);
    private static final List<String> FILES = List.of(
            "textbook-cn.json", "textbook-math.json", "textbook-en.json",
            "knowledge-cn.json", "knowledge-math.json", "knowledge-en.json");

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, JsonNode> cache = new ConcurrentHashMap<>();

    public JsonNode root(String fileName) {
        return cache.computeIfAbsent(fileName, f -> {
            try (InputStream in = new ClassPathResource("seed/" + f).getInputStream()) {
                return mapper.readTree(in);
            } catch (Exception e) {
                log.warn("种子文件缺失或损坏: {}", f);
                return mapper.createObjectNode();
            }
        });
    }

    /** 知识文件中定位 (科目,年级,学期[,分支]) 的 term 节点。 */
    public JsonNode knowledgeTerm(String subject, int grade, String term, String branch) {
        for (JsonNode t : root("knowledge-" + subjectKey(subject) + ".json").path("terms")) {
            if (t.path("grade").asInt() == grade && t.path("term").asText().equals(term)
                    && (branch == null || branch.equals(t.path("branch").asText("校内")))) {
                return t;
            }
        }
        return mapper.nullNode();
    }

    public JsonNode textbookTerm(String subject, int grade, String term) {
        for (JsonNode t : root("textbook-" + subjectKey(subject) + ".json").path("terms")) {
            if (t.path("grade").asInt() == grade && t.path("term").asText().equals(term)) {
                return t;
            }
        }
        return mapper.nullNode();
    }

    /** 某内置叶子的题目 → domain Quiz。 */
    public List<Quiz> builtinQuizzesOfLeaf(String leafId) {
        for (String file : FILES) {
            if (!file.startsWith("knowledge-")) {
                continue;
            }
            for (JsonNode term : root(file).path("terms")) {
                for (JsonNode module : term.path("modules")) {
                    for (JsonNode unit : module.path("units")) {
                        for (JsonNode leaf : unit.path("leaves")) {
                            if (leafId.equals(leaf.path("id").asText())) {
                                return parseQuizzes(leaf.path("quizzes"), leafId);
                            }
                        }
                    }
                }
            }
        }
        return List.of();
    }

    /** 知识树所有内置叶子 id → title（含 branch）。 */
    public List<BuiltinLeaf> allBuiltinLeaves(String subject, int grade, String term) {
        List<BuiltinLeaf> out = new ArrayList<>();
        String file = "knowledge-" + subjectKey(subject) + ".json";
        for (JsonNode t : root(file).path("terms")) {
            if (t.path("grade").asInt() != grade || !t.path("term").asText().equals(term)) {
                continue;
            }
            String branch = t.path("branch").asText("校内");
            for (JsonNode module : t.path("modules")) {
                for (JsonNode unit : module.path("units")) {
                    for (JsonNode leaf : unit.path("leaves")) {
                        out.add(new BuiltinLeaf(leaf.path("id").asText(),
                                leaf.path("title").asText(), branch,
                                parseQuizzes(leaf.path("quizzes"), leaf.path("id").asText())));
                    }
                }
            }
        }
        return out;
    }

    public record BuiltinLeaf(String id, String title, String branch, List<Quiz> quizzes) {}

    private List<Quiz> parseQuizzes(JsonNode arr, String leafId) {
        List<Quiz> list = new ArrayList<>();
        for (JsonNode q : arr) {
            List<Quiz.Option> options = new ArrayList<>();
            for (JsonNode o : q.path("options")) {
                options.add(new Quiz.Option(o.path("id").asText(), o.path("text").asText()));
            }
            List<String> accepts = new ArrayList<>();
            q.path("answerAccepts").forEach(a -> accepts.add(a.asText()));
            Quiz.Type type = "single".equals(q.path("type").asText()) ? Quiz.Type.SINGLE : Quiz.Type.BLANK;
            list.add(new Quiz(q.path("id").asText(), leafId, type, q.path("stem").asText(),
                    options, q.path("answer").asText(), accepts, q.path("explanation").asText(null)));
        }
        return list;
    }

    private static String subjectKey(String subject) {
        return switch (subject) {
            case "cn", "cny" -> "cn";
            case "math", "mat" -> "math";
            case "en", "eng" -> "en";
            default -> subject;
        };
    }
}
