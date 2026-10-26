package com.studdy.repo;

import com.studdy.entity.ApprovalConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalConfigRepository extends JpaRepository<ApprovalConfigEntity, String> {
}
