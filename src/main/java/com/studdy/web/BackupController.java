package com.studdy.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.studdy.service.AdminService;
import com.studdy.service.ApiException;
import com.studdy.service.BackupService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/** 备份 API（docs/02 §4 备份段；docs/03 M8.3–8.7）。 */
@RestController
@RequestMapping("/api/backup")
public class BackupController {

    private final BackupService backup;
    private final AdminService admin;

    public BackupController(BackupService backup, AdminService admin) {
        this.backup = backup;
        this.admin = admin;
    }

    private void gate(HttpServletRequest req) {
        if (!admin.validToken(req.getHeader("X-Admin-Token"))) {
            throw new ApiException(401, "需要家长身份验证");
        }
    }

    @GetMapping("/export")
    public ResponseEntity<Resource> export(HttpServletRequest req) {
        gate(req);
        byte[] bytes = backup.exportJson().getBytes(StandardCharsets.UTF_8);
        ContentDisposition cd = ContentDisposition.attachment()
                .filename(backup.exportFileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ByteArrayResource(bytes));
    }

    /** 第一步：上传→校验→预览（confirmId）。 */
    @PostMapping(value = "/import", consumes = MediaType.ALL_VALUE)
    public Map<String, Object> stage(@RequestBody String raw, HttpServletRequest req) {
        gate(req);
        return backup.stageImport(raw);
    }

    /** 第二步：确认覆盖。 */
    @PostMapping("/import/confirm")
    public Map<String, Object> confirm(@RequestBody JsonNode body, HttpServletRequest req) {
        gate(req);
        return backup.confirmImport(body.path("confirmId").asText());
    }
}
