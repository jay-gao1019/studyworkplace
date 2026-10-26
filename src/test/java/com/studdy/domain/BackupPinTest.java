package com.studdy.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studdy.domain.backup.BackupFormat;
import com.studdy.domain.security.PinHash;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BackupPinTest {

    static final ObjectMapper M = new ObjectMapper();

    private static String v2Payload(String extraTx) {
        return """
        {"schemaVersion":"2.0","exportedAt":"2026-10-25T12:00:00+08:00",
         "payload":{"settings":{"pinHash":null,"checkinAmount":20},
           "taskItems":[],"shopItems":[],"habits":[],
           "profiles":[{"id":"p1","name":"小明","grade":4,"term":"上"}],
           "coinTxs":[%s],"redemptions":[],"planChecks":[],"textbookChecks":[],
           "customNodes":[],"customQuizzes":[],"attempts":[],
           "life":{"sleepLogs":[],"screenSessions":[],"screenBanks":[],"habitLogs":[],"habitChains":[]}}}
        """.formatted(extraTx);
    }

    @Test
    void tamperedChecksumReject() throws Exception {
        String payload = v2Payload("{\"id\":\"t1\",\"amount\":5}");
        var root = M.readTree(payload);
        String good = BackupFormat.checksum(M, root.get("payload"));
        // 正确 checksum 通过
        var ok = BackupFormat.validate(M, withChecksum(payload, good));
        assertTrue(ok.ok(), ok.message());
        // 篡改流水金额后，文件内 checksum 与内容不符
        String tampered = payload.replace("\"amount\":5", "\"amount\":999");
        var bad = BackupFormat.validate(M, withChecksum(tampered, good));
        assertFalse(bad.ok());
        assertTrue(bad.message().contains("校验和"));
    }

    private static String withChecksum(String payloadJson, String checksum) throws Exception {
        var node = (com.fasterxml.jackson.databind.node.ObjectNode) M.readTree(payloadJson);
        node.put("checksum", checksum);
        return node.toString();
    }

    @Test
    void badJsonReadable() {
        var v = BackupFormat.validate(M, "{ not json");
        assertFalse(v.ok());
        assertTrue(v.message().contains("JSON"));
        var v2 = BackupFormat.validate(M, "{\"hello\":1}");
        assertFalse(v2.ok());
        assertTrue(v2.message().contains("schemaVersion"));
        var future = BackupFormat.validate(M, "{\"schemaVersion\":\"9.9\",\"payload\":{}}");
        assertTrue(future.message().contains("更新版本"));
    }

    @Test
    void migrate10to20() throws Exception {
        String v1 = """
        {"schemaVersion":"1.0","checksum":"sha256:x",
         "payload":{"config":{"pin":"1234"},
           "txs":[],"profiles":[{"id":"p1","name":"小明","grade":4,"term":"上"}],
           "redemptions":[],"planChecks":[],"textbookChecks":[],
           "customNodes":[],"customQuizzes":[],"attempts":[],"treeNodes":[],
           "life":{"sleepLogs":[]}}}
        """.replace("\"treeNodes\":[],", "");
        var migrated = BackupFormat.migrate(M, "1.0", M.readTree(v1).get("payload"));
        assertTrue(migrated.has("settings"));
        assertTrue(migrated.has("coinTxs"));
        assertTrue(migrated.get("settings").has("pinHash"));
        assertFalse(migrated.get("settings").has("pin")); // 旧明文 PIN 丢弃，导入后强制重设
    }

    @Test
    void decimalScaleRoundTrip() throws Exception {
        // 回归：DECIMAL 列（rate 0.000 / minutesUsed 1.50）导出后回读，校验和必须仍一致
        String payload = v2Payload("{\"id\":\"t1\",\"amount\":5}");
        var root = M.readTree(payload);
        // 真实导出用 valueToTree(BigDecimal) → DecimalNode 保留标度（0.000 / 1.50）
        var attempt = M.createObjectNode();
        attempt.set("rate", M.valueToTree(new java.math.BigDecimal("0.000")));
        ((com.fasterxml.jackson.databind.node.ArrayNode) root.get("payload").get("attempts")).add(attempt);
        var session = M.createObjectNode();
        session.set("minutesUsed", M.valueToTree(new java.math.BigDecimal("1.50")));
        ((com.fasterxml.jackson.databind.node.ArrayNode) root.get("payload").get("life").get("screenSessions")).add(session);
        // 模拟导出：BigDecimal 精度经序列化落盘
        String file = M.writeValueAsString(root);
        String good = BackupFormat.checksum(M, root.get("payload"));
        var node = (com.fasterxml.jackson.databind.node.ObjectNode) M.readTree(file);
        node.put("checksum", good);
        var v = BackupFormat.validate(M, node.toString());
        assertTrue(v.ok(), v.message());
    }

    @Test
    void pinRoundTrip() {
        String h = PinHash.hash("8888");
        assertTrue(h.startsWith("pbkdf2:"));
        assertTrue(PinHash.verify("8888", h));
        assertFalse(PinHash.verify("8889", h));
        assertFalse(PinHash.verify("8888", "garbage"));
    }
}
