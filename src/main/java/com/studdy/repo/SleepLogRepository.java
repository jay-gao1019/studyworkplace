package com.studdy.repo;

import com.studdy.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface SleepLogRepository extends JpaRepository<SleepLogEntity, SleepLogEntity.Pk> {
    List<SleepLogEntity> findByProfileIdAndDayKey(String profileId, String dayKey);
}
