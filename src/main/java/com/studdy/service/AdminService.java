package com.studdy.service;

import com.studdy.domain.security.PinHash;
import com.studdy.entity.SettingsEntity;
import com.studdy.repo.SettingsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 家长门（docs/03 M7）：PBKDF2 PIN、服务端 token（30min TTL）、错 5 次锁 60s。 */
@Service
public class AdminService {

    private final SettingsRepository settingsRepo;
    private final String defaultPin;
    private final long tokenTtlMinutes;

    private final Map<String, Instant> tokens = new ConcurrentHashMap<>();
    private int failCount = 0;
    private Instant lockUntil = null;

    public AdminService(SettingsRepository settingsRepo,
                        @Value("${studdy.default-pin:8888}") String defaultPin,
                        @Value("${studdy.admin-token-ttl-minutes:30}") long tokenTtlMinutes) {
        this.settingsRepo = settingsRepo;
        this.defaultPin = defaultPin;
        this.tokenTtlMinutes = tokenTtlMinutes;
    }

    /** 是否尚未设置 PIN（M7.1：首次进后台提示设置，可跳过用默认 8888）。 */
    public boolean pinUnset() {
        return settingsRepo.findById("singleton").map(s -> s.getPinHash() == null).orElse(true);
    }

    public void setPin(String newPin) {
        if (newPin == null || !newPin.matches("\\d{4}")) {
            throw new ApiException(400, "PIN 需为 4 位数字");
        }
        SettingsEntity s = settingsRepo.findById("singleton").orElseThrow();
        s.setPinHash(PinHash.hash(newPin));
        settingsRepo.save(s);
        tokens.clear();
    }

    /** M7.2 解锁：错 5 次锁定 60 秒；成功返回 token。 */
    public synchronized Map<String, Object> unlock(String pin) {
        if (lockUntil != null && Instant.now().isBefore(lockUntil)) {
            long wait = Duration.between(Instant.now(), lockUntil).toSeconds() + 1;
            throw new ApiException(429, "尝试次数过多，请 " + wait + " 秒后再试");
        }
        SettingsEntity s = settingsRepo.findById("singleton").orElseThrow();
        String stored = s.getPinHash() != null ? s.getPinHash() : PinHash.hash(defaultPin);
        if (s.getPinHash() == null) {
            // 使用默认 PIN 首次解锁即持久化散列（不落明文）
            s.setPinHash(stored);
            settingsRepo.save(s);
        }
        if (!PinHash.verify(pin, stored)) {
            failCount++;
            if (failCount >= 5) {
                lockUntil = Instant.now().plusSeconds(60);
                failCount = 0;
                throw new ApiException(429, "已锁定 60 秒");
            }
            throw new ApiException(401, "PIN 不正确（剩 " + (5 - failCount) + " 次）");
        }
        failCount = 0;
        String token = UUID.randomUUID().toString().replace("-", "");
        tokens.put(token, Instant.now().plusSeconds(tokenTtlMinutes * 60));
        return Map.of("token", token, "ttlMinutes", tokenTtlMinutes);
    }

    public boolean validToken(String token) {
        if (token == null) {
            return false;
        }
        Instant exp = tokens.get(token);
        if (exp == null) {
            return false;
        }
        if (Instant.now().isAfter(exp)) {
            tokens.remove(token);
            return false;
        }
        return true;
    }

    public void revoke(String token) {
        if (token != null) {
            tokens.remove(token);
        }
    }
}
