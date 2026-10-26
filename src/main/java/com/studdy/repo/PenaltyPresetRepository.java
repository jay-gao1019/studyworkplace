package com.studdy.repo;

import com.studdy.entity.PenaltyPresetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PenaltyPresetRepository extends JpaRepository<PenaltyPresetEntity, String> {
    List<PenaltyPresetEntity> findAllByOrderBySortAscIdAsc();
}
