package com.studdy.repo;

import com.studdy.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
public interface AttemptRoundRepository extends JpaRepository<AttemptRoundEntity, String> {
    List<AttemptRoundEntity> findByProfileIdAndLeafIdOrderByFinishedAtDesc(String profileId, String leafId);
    List<AttemptRoundEntity> findByProfileIdOrderByFinishedAtDesc(String profileId);
    @Query("select a from AttemptRoundEntity a where a.profileId = :pid and a.finishedAt >= :from order by a.finishedAt")
    List<AttemptRoundEntity> findRecent(@Param("pid") String pid, @Param("from") java.time.Instant from);
}
