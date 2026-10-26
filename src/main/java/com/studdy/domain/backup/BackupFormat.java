package com.studdy.domain.backup;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studdy.domain.quiz.GraderEngine;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 备份文件格式核心规则（docs/05 §8、docs/03 M8.3–8.6）：
 * 规范化序列化与校验和、结构校验、旧版本迁移（1.0 本地导出 → 2.0）。
 * 纯 Jackson，无 Spring/JPA 依赖。
 */
public final class BackupFormat {

    public static final String CURRENT_VERSION = "2.0";
    private static final List<String> REQUIRED_SECTIONS = List.of(
            "settings", "taskItems", "shopItems", "habits", "profiles",
            "coinTxs", "redemptions", "planChecks", "textbookChecks",
            "customNodes", "customQuizzes", "attempts", "life");

    private BackupFormat() {}

    /** 校验口径解析器：浮点按 BigDecimal 读入，保留小数位（0.000 回读不得缩成 0.0），保证与导出侧 checksum 对称。 */
    private static final ObjectMapper CANON_READER =
            new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    public record Validation(boolean ok, String message, int profileCount, int txCount) {}

    /** 键深排序 + 紧凑序列化（checksum 的规范化口径）。 */
    public static String canonicalJson(ObjectMapper mapper, JsonNode node) {
        return sortDeep(node).toString();
    }

    public static String checksum(ObjectMapper mapper, JsonNode payload) {
        return "sha256:" + GraderEngine.sha256Hex(canonicalJson(mapper, payload));
    }

    /** 解析 + 版本迁移 + 结构校验 + 校验和复算。 */
    public static Validation validate(ObjectMapper mapper, String rawJson) {
        JsonNode root;
        try {
            root = CANON_READER.readTree(rawJson);
        } catch (Exception e) {
            return new Validation(false, "文件不是有效的 JSON，请确认选择的是导出的备份文件", 0, 0);
        }
        if (!root.isObject() || !root.hasNonNull("schemaVersion") || !root.hasNonNull("payload")) {
            return new Validation(false, "缺少 schemaVersion 或 payload，可能不是本工具的备份文件", 0, 0);
        }
        String version = root.get("schemaVersion").asText();
        JsonNode payload = root.get("payload");
        if (isOlderVersion(version)) {
            payload = migrate(mapper, version, payload);
        } else if (isNewerVersion(version)) {
            return new Validation(false, "该备份来自更新版本（" + version + "），请先升级应用后再导入", 0, 0);
        }
        List<String> missing = new ArrayList<>();
        for (String s : REQUIRED_SECTIONS) {
            if (!payload.has(s)) {
                missing.add(s);
            }
        }
        if (!missing.isEmpty()) {
            return new Validation(false, "备份缺少数据段: " + String.join(", ", missing), 0, 0);
        }
        // checksum 一律按文件内容复算比对（防篡改/损坏）
        if (root.hasNonNull("checksum")
                && !checksum(mapper, payload).equals(root.get("checksum").asText())) {
            return new Validation(false, "校验和不一致，文件可能被修改或损坏，已中止导入", 0, 0);
        }
        int profiles = payload.path("profiles").isArray() ? payload.get("profiles").size() : 0;
        int txs = payload.path("coinTxs").isArray() ? payload.get("coinTxs").size() : 0;
        return new Validation(true, "OK", profiles, txs);
    }

    private static boolean isOlderVersion(String v) {
        return v.compareTo(CURRENT_VERSION) < 0;
    }

    private static boolean isNewerVersion(String v) {
        return v.compareTo(CURRENT_VERSION) > 0;
    }

    /** 迁移链：1.0（docs/05 v1.1 本地导出）→ 2.0。 */
    public static JsonNode migrate(ObjectMapper mapper, String fromVersion, JsonNode payload) {
        if (!"1.0".equals(fromVersion)) {
            return payload; // 未知旧版本尽力而为；结构校验兜底
        }
        ObjectNode p = payload.deepCopy();
        renameIfPresent(p, "config", "settings");
        renameIfPresent(p, "txs", "coinTxs");
        renameIfPresent(p, "redemptions", "redemptions");
        renameIfPresent(p, "customNodes", "customNodes");
        renameIfPresent(p, "customQuizzes", "customQuizzes");
        renameIfPresent(p, "attempts", "attempts");
        // 1.0 settings 为对象 {pin, levelThresholds,...}：pin → pinHash 置空（导入后强制重设）
        JsonNode settings = p.get("settings");
        if (settings != null && settings.isObject() && settings.has("pin") && !settings.has("pinHash")) {
            ((ObjectNode) settings).remove("pin");
            ((ObjectNode) settings).putNull("pinHash");
        }
        return p;
    }

    private static void renameIfPresent(ObjectNode node, String from, String to) {
        if (node.has(from) && !node.has(to)) {
            node.set(to, node.remove(from));
        }
    }

    private static JsonNode sortDeep(JsonNode node) {
        if (node.isObject()) {
            TreeMap<String, JsonNode> sorted = new TreeMap<>();
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                sorted.put(e.getKey(), sortDeep(e.getValue()));
            }
            ObjectNode out = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            sorted.forEach(out::set);
            return out;
        }
        if (node.isArray()) {
            var arr = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
            node.forEach(el -> arr.add(sortDeep(el)));
            return arr;
        }
        return node;
    }
}
