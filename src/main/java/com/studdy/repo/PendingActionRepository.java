package com.studdy.repo;

import com.studdy.entity.PendingActionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PendingActionRepository extends JpaRepository<PendingActionEntity, String> {

    List<PendingActionEntity> findByProfileIdAndStatusOrderByCreatedAtDesc(String profileId, String status);

    List<PendingActionEntity> findByStatusOrderByCreatedAtDesc(String status);

    Optional<PendingActionEntity> findByProfileIdAndKindAndRefIdAndDayKeyAndStatus(
            String profileId, String kind, String refId, String dayKey, String status);

    List<PendingActionEntity> findByProfileIdOrderByCreatedAtDesc(String profileId);
}
