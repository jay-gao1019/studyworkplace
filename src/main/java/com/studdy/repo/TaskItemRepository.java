package com.studdy.repo;

import com.studdy.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface TaskItemRepository extends JpaRepository<TaskItemEntity, String> {
    List<TaskItemEntity> findAllByOrderBySortAscIdAsc();
}
