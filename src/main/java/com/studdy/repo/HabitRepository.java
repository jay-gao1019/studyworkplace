package com.studdy.repo;

import com.studdy.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HabitRepository extends JpaRepository<HabitEntity, String> {}
