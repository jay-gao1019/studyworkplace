package com.studdy.repo;

import com.studdy.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface RedemptionRepository extends JpaRepository<RedemptionEntity, String> {
    List<RedemptionEntity> findByProfileIdOrderByIdDesc(String profileId);
}
