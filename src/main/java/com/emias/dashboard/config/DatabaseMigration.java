package com.emias.dashboard.config;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseMigration {

    private final JdbcTemplate jdbc;

    public DatabaseMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void migrate() {
        try {
            jdbc.execute("ALTER TABLE settings ALTER COLUMN setting_value VARCHAR(10000)");
        } catch (Exception ignored) {
        }
    }
}
