package com.studdy.repo;

import com.studdy.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface TextbookCheckRepository extends JpaRepository<TextbookCheckEntity, TextbookCheckEntity.Pk> {
    List<TextbookCheckEntity> findByProfileId(String profileId);
}
