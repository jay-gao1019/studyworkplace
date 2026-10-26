package com.studdy.repo;

import com.studdy.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface CoinTxRepository extends JpaRepository<CoinTxEntity, String> {
    List<CoinTxEntity> findByProfileIdOrderByCreatedAtAsc(String profileId);
    List<CoinTxEntity> findByProfileIdAndDayKey(String profileId, String dayKey);
}
