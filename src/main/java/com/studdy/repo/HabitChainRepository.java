package com.studdy.repo;

import com.studdy.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface HabitChainRepository extends JpaRepository<HabitChainEntity, HabitChainEntity.Pk> {
    List<HabitChainEntity> findByProfileIdAndHabitId(String profileId, String habitId);
}
