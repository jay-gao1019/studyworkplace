package com.studdy.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.studdy.service.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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
    private final ApprovalService approval;
    private final int port;

    public BootstrapController(SessionService session, ProfileService profiles, LedgerService ledger,
                               PlanService plan, TextbookService textbook, AdminService admin,
                               ApprovalService approval, @Value("${server.port:8080}") int port) {
        this.session = session;
        this.profiles = profiles;
        this.ledger = ledger;
        this.plan = plan;
        this.textbook = textbook;
        this.admin = admin;
        this.approval = approval;
        this.port = port;
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
            out.put("pendingCount", approval.countPending(pid));
        }
        out.put("approvalEnabled", approval.configEntity().isEnabled());
        out.put("pinUnset", admin.pinUnset());
        // v2.4/FR-31：P7 底部"手机访问"入口——本机内网 IPv4 × 服务端口
        out.put("lanHints", lanHints());
        return out;
    }

    /** 非回环站点本地 IPv4 → http://ip:port 列表（NFR-01：仅家内网，不出公网）。 */
    private List<String> lanHints() {
        List<String> out = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) {
                    continue;
                }
                for (java.net.InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (addr instanceof Inet4Address && addr.isSiteLocalAddress()) {
                        out.add("http://" + addr.getHostAddress() + ":" + port);
                    }
                }
            }
        } catch (Exception ignored) {
        }
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
