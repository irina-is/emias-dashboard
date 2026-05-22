package com.emias.dashboard.service;

import com.emias.dashboard.entity.Settings;
import com.emias.dashboard.model.DashboardConfig;
import com.emias.dashboard.repository.SettingsRepository;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class SettingsService {

    public static final String DASHBOARD_TITLE          = "dashboard.title";
    public static final String ANNUAL_PLAN              = "screening.annual-plan";
    public static final String CHART_MONTHLY_TITLE      = "chart.monthly.title";
    public static final String CHART_MONTHLY_ENABLED    = "chart.monthly.enabled";
    public static final String CHART_AGES_TITLE         = "chart.ages.title";
    public static final String CHART_AGES_ENABLED       = "chart.ages.enabled";

    public static final String TILE_1_NAME = "tile.1.name";
    public static final String TILE_2_NAME = "tile.2.name";
    public static final String TILE_3_NAME = "tile.3.name";

    private final SettingsRepository repo;

    public SettingsService(SettingsRepository repo) {
        this.repo = repo;
    }

    public String get(String key, String defaultValue) {
        return repo.findById(key).map(Settings::getValue).orElse(defaultValue);
    }

    public DashboardConfig getConfig() {
        String  title          = get(DASHBOARD_TITLE,       "Контроль выполнения плана скрининга ХВГС");
        long    plan           = Long.parseLong(get(ANNUAL_PLAN, "430270"));
        String  monthlyTitle   = get(CHART_MONTHLY_TITLE,   "Динамика выполнения плана скрининга по месяцам");
        boolean monthlyEnabled = Boolean.parseBoolean(get(CHART_MONTHLY_ENABLED, "true"));
        String  agesTitle      = get(CHART_AGES_TITLE,      "Распределение по возрасту");
        boolean agesEnabled    = Boolean.parseBoolean(get(CHART_AGES_ENABLED,    "true"));
        return new DashboardConfig(title, plan, monthlyTitle, monthlyEnabled, agesTitle, agesEnabled);
    }

    public void save(Map<String, String> values) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            Settings s = repo.findById(entry.getKey())
                             .orElse(new Settings(entry.getKey(), null));
            s.setValue(entry.getValue());
            repo.save(s);
        }
    }
}
