package com.studdy.repo;

import com.studdy.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ScreenSessionRepository extends JpaRepository<ScreenSessionEntity, String> {
    List<ScreenSessionEntity> findByProfileIdAndDayKeyOrderByStartAtAsc(String profileId, String dayKey);
}
