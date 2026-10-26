package com.studdy.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.studdy.service.*;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 生活管理 + 商店 API（docs/02 §4）。 */
@RestController
@RequestMapping("/api")
public class LifeShopController {

    private final SessionService session;
    private final LifeService life;
    private final SettingsService settings;
    private final ShopService shop;

    public LifeShopController(SessionService session, LifeService life,
                              SettingsService settings, ShopService shop) {
        this.session = session;
        this.life = life;
        this.settings = settings;
        this.shop = shop;
    }

    private String pid() {
        String id = session.currentProfileId();
        if (id == null) {
            throw new ApiException(400, "请先创建孩子档案");
        }
        return id;
    }

    @GetMapping("/life")
    public Map<String, Object> lifeView() {
        var ls = settings.life();
        Map<String, Object> windows = new LinkedHashMap<>();
        windows.put("sleepTarget", ls.sleepTarget());
        windows.put("sleepGraceMin", ls.sleepGraceMin());
        windows.put("wakeTarget", ls.wakeTarget());
        windows.put("wakeGraceMin", ls.wakeGraceMin());
        return Map.of("windows", windows, "screen", life.screenView(pid()),
                "habits", life.habitViews(pid()), "sleep", life.sleepToday(pid()));
    }

    @PostMapping("/life/sleep")
    public Map<String, Object> sleep(@RequestBody JsonNode body) {
        return life.sleepWake(pid(), body.path("kind").asText());
    }

    @PostMapping("/life/habit/toggle")
    public Map<String, Object> habit(@RequestBody JsonNode body) {
        return life.habitToggle(pid(), body.path("habitId").asText());
    }

    @PostMapping("/life/screen/start")
    public Map<String, Object> screenStart() {
        return life.screenStart(pid());
    }

    @PostMapping("/life/screen/stop")
    public Map<String, Object> screenStop() {
        return life.screenStop(pid());
    }

    // ── 商店 ──

    @GetMapping("/shop")
    public List<Map<String, Object>> shopCatalog() {
        return shop.catalog(pid());
    }

    @PostMapping("/shop/redeem")
    public Map<String, Object> redeem(@RequestBody JsonNode body) {
        return shop.redeem(pid(), body.path("itemId").asText());
    }

    @GetMapping("/shop/redemptions")
    public List<Map<String, Object>> redemptions() {
        return shop.myRedemptions(pid());
    }
}
