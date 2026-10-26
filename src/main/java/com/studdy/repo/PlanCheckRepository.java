package com.studdy.repo;

import com.studdy.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PlanCheckRepository extends JpaRepository<PlanCheckEntity, PlanCheckEntity.Pk> {
    List<PlanCheckEntity> findByProfileIdAndDayKey(String profileId, String dayKey);
}
