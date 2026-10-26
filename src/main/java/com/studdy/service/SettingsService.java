package com.studdy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studdy.domain.coin.LevelRule;
import com.studdy.entity.SettingsEntity;
import com.studdy.repo.SettingsRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** 全局配置单例访问（settings.code="singleton"；docs/05 §3）。 */
@Service
public class SettingsService {

    /** 生活参数（M6-A/C；解析自 life_settings JSON，缺省取文档默认值）。 */
    public record LifeSettings(String sleepTarget, int sleepGraceMin, String wakeTarget, int wakeGraceMin,
                               int sleepCoin, int wakeCoin, int screenDailyMinutes,
                               int screenWeeklyBankCap, int screenOverPenalty) {}

    private final SettingsRepository repo;
    private final ObjectMapper mapper = new ObjectMapper();

    public SettingsService(SettingsRepository repo) {
        this.repo = repo;
    }

    public SettingsEntity get() {
        return repo.findById("singleton").orElseThrow(() -> new ApiException(500, "配置未初始化"));
    }

    public LevelRule levelRule() {
        SettingsEntity s = get();
        try {
            JsonNode th = mapper.readTree(s.getLevelThresholds());
            JsonNode nm = mapper.readTree(s.getLevelNames());
            int[] thresholds = new int[th.size()];
            for (int i = 0; i < th.size(); i++) {
                thresholds[i] = th.get(i).asInt();
            }
            String[] names = new String[nm.size()];
            for (int i = 0; i < nm.size(); i++) {
                names[i] = nm.get(i).asText();
            }
            return new LevelRule(thresholds, names);
        } catch (Exception e) {
            return LevelRule.DEFAULT;
        }
    }

    public LifeSettings life() {
        try {
            JsonNode n = mapper.readTree(get().getLifeSettings());
            return new LifeSettings(
                    n.path("sleepTarget").asText("21:30"), n.path("sleepGraceMin").asInt(15),
                    n.path("wakeTarget").asText("07:00"), n.path("wakeGraceMin").asInt(15),
                    n.path("sleepCoin").asInt(5), n.path("wakeCoin").asInt(5),
                    n.path("screenDailyMinutes").asInt(40), n.path("screenWeeklyBankCap").asInt(60),
                    n.path("screenOverPenalty").asInt(-10));
        } catch (Exception e) {
            return new LifeSettings("21:30", 15, "07:00", 15, 5, 5, 40, 60, -10);
        }
    }

    public int checkinAmount() {
        return get().getCheckinAmount();
    }

    public int textbookCoinValue() {
        return get().getTextbookCoinValue();
    }

    public SettingsEntity save(SettingsEntity s) {
        return repo.save(s);
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    public List<Integer> parseIntArray(String json) {
        try {
            List<Integer> out = new ArrayList<>();
            mapper.readTree(json).forEach(n -> out.add(n.asInt()));
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }
}
