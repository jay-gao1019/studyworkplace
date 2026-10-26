package com.studdy.repo;

import com.studdy.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface QuizRepository extends JpaRepository<QuizEntity, String> {
    List<QuizEntity> findByLeafId(String leafId);
}
