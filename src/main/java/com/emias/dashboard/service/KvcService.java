package com.emias.dashboard.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class KvcService {

    private static final String KEY_TITLE    = "kvc.hcv.title";
    private static final String KEY_GOAL     = "kvc.hcv.goal";
    private static final String KEY_LEADING  = "kvc.hcv.leading";
    private static final String KEY_LAGGING  = "kvc.hcv.lagging";

    private final SettingsService settings;
    private final ObjectMapper    mapper;

    public KvcService(SettingsService settings, ObjectMapper mapper) {
        this.settings = settings;
        this.mapper   = mapper;
    }

    public Map<String, Object> get() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title",    settings.get(KEY_TITLE,   "Лечение гепатита С и достижение УВО"));
        result.put("goal",     settings.get(KEY_GOAL,    "Вылечено: 5 163 пациента / год (план 2026)"));
        result.put("leading",  parseIndicators(settings.get(KEY_LEADING,  null)));
        result.put("lagging",  parseIndicators(settings.get(KEY_LAGGING,  null)));
        return result;
    }

    public void save(String title, String goal,
                     List<Map<String, String>> leading,
                     List<Map<String, String>> lagging) throws Exception {
        settings.save(Map.of(
            KEY_TITLE,   title,
            KEY_GOAL,    goal,
            KEY_LEADING, mapper.writeValueAsString(leading),
            KEY_LAGGING, mapper.writeValueAsString(lagging)
        ));
    }

    private List<Map<String, String>> parseIndicators(String json) {
        if (json == null || json.isBlank()) return defaultIndicators();
        try {
            return mapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return defaultIndicators();
        }
    }

    private List<Map<String, String>> defaultIndicators() {
        return new ArrayList<>();
    }
}
