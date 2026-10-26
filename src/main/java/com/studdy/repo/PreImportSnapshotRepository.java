package com.studdy.repo;

import com.studdy.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PreImportSnapshotRepository extends JpaRepository<PreImportSnapshotEntity, String> {}
