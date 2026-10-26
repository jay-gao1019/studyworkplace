package com.studdy.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.studdy.service.*;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** 壳层 API：bootstrap / 会话切换（docs/02 §4）。 */
@RestController
@RequestMapping("/api")
public class BootstrapController {

    private final SessionService session;
    private final ProfileService profiles;
    private final LedgerService ledger;
    private final PlanService plan;
    private final TextbookService textbook;
    private final AdminService admin;

    public BootstrapController(SessionService session, ProfileService profiles, LedgerService ledger,
                               PlanService plan, TextbookService textbook, AdminService admin) {
        this.session = session;
        this.profiles = profiles;
        this.ledger = ledger;
        this.plan = plan;
        this.textbook = textbook;
        this.admin = admin;
    }

    @GetMapping("/bootstrap")
    public Map<String, Object> bootstrap() {
        Map<String, Object> out = new LinkedHashMap<>();
        String pid = session.currentProfileId();
        out.put("profiles", profiles.list());
        out.put("currentProfileId", pid);
        if (pid != null) {
            out.put("profile", profiles.get(pid));
            out.put("coins", ledger.summary(pid));
            out.put("todayPlan", plan.todayView(pid));
            out.put("progress", textbook.progressSummary(pid));
        }
        out.put("pinUnset", admin.pinUnset());
        return out;
    }

    @PostMapping("/session/switch")
    public Map<String, Object> switchProfile(@RequestBody JsonNode body) {
        String pid = body.path("profileId").asText();
        session.switchTo(pid);
        return Map.of("currentProfileId", pid);
    }

    /** 档案快速创建也在壳层（P1 首屏引导；正式管理在后台）。 */
    @PostMapping("/profiles")
    public ProfileService.ProfileView create(@RequestBody JsonNode body) {
        return profiles.create(body.path("name").asText(), body.path("avatar").asText("🐼"),
                body.path("grade").asInt(1), body.path("term").asText("上"));
    }
}
