package com.studdy.repo;

import com.studdy.entity.PhotoEvidenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PhotoEvidenceRepository extends JpaRepository<PhotoEvidenceEntity, String> {
    List<PhotoEvidenceEntity> findByProfileIdOrderByCreatedAtDesc(String profileId);
}
