package com.studdy.repo;

import com.studdy.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface HabitLogRepository extends JpaRepository<HabitLogEntity, HabitLogEntity.Pk> {
    List<HabitLogEntity> findByProfileIdAndHabitId(String profileId, String habitId);

    List<HabitLogEntity> findByProfileIdAndDayKey(String profileId, String dayKey);
}
