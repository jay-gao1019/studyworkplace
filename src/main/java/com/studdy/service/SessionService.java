package com.studdy.service;

import com.studdy.entity.ProfileEntity;
import com.studdy.repo.ProfileRepository;
import org.springframework.stereotype.Service;

/** 当前档案会话指针（家庭单机；docs/03 M8.2；进程内存即可，重启回落首个/默认档案）。 */
@Service
public class SessionService {

    private final ProfileRepository profiles;
    private volatile String currentProfileId;

    public SessionService(ProfileRepository profiles) {
        this.profiles = profiles;
    }

    public synchronized String currentProfileId() {
        if (currentProfileId != null && profiles.existsById(currentProfileId)) {
            return currentProfileId;
        }
        String fallback = profiles.findAll().stream()
                .min(java.util.Comparator.comparingLong(p -> p.getCreatedAt().getEpochSecond()))
                .map(ProfileEntity::getId).orElse(null);
        currentProfileId = fallback;
        return fallback;
    }

    public synchronized void switchTo(String profileId) {
        if (!profiles.existsById(profileId)) {
            throw new ApiException(404, "档案不存在");
        }
        currentProfileId = profileId;
    }
}
