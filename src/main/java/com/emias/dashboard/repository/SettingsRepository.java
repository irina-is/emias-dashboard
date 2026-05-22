package com.emias.dashboard.repository;

import com.emias.dashboard.entity.Settings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingsRepository extends JpaRepository<Settings, String> {}
