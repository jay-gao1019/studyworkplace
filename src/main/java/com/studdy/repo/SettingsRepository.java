package com.studdy.repo;

import com.studdy.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingsRepository extends JpaRepository<SettingsEntity, String> {}
