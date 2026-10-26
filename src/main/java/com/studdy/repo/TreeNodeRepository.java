package com.studdy.repo;

import com.studdy.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface TreeNodeRepository extends JpaRepository<TreeNodeEntity, String> {
    List<TreeNodeEntity> findBySubjectAndGradeAndTerm(String subject, int grade, String term);
}
