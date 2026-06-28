package com.emias.dashboard.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ActionPlanService {

    private static final String KEY = "hcv.action-plan";

    private final SettingsService settings;
    private final ObjectMapper    mapper;

    public ActionPlanService(SettingsService settings, ObjectMapper mapper) {
        this.settings = settings;
        this.mapper   = mapper;
    }

    public Map<String, Object> get() {
        String json = settings.get(KEY, null);
        if (json != null && !json.isBlank()) {
            try { return mapper.readValue(json, new TypeReference<>() {}); }
            catch (Exception ignored) {}
        }
        return defaults();
    }

    public void save(Map<String, Object> data) throws Exception {
        settings.save(Map.of(KEY, mapper.writeValueAsString(data)));
    }

    private Map<String, Object> defaults() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("deadline", "10.07.2026");
        m.put("kpi", List.of(
            Map.of("value", "2 944", "color", "#02C39A", "desc", "пациентов не отработаны МО из еженедельного отчета"),
            Map.of("value", "915",   "color", "#f59e0b", "desc", "уже есть заключение МОНИКИ"),
            Map.of("value", "2 029", "color", "#ef4444", "desc", "нужно направить на консультацию")
        ));
        m.put("groups", List.of(
            Map.of("color", "green", "icon", "✅", "title", "Уже готовы к лечению — 1 166 чел.",
                "items", List.of("ПЦР-положительный результат подтверждён", "Заключение МОНИКИ / Центра СПИД получено")),
            Map.of("color", "amber", "icon", "⏳", "title", "Нужна консультация МОНИКИ — 3 096 чел.",
                "items", List.of("ПЦР-положительный результат есть", "Заключение МОНИКИ / Центра СПИД отсутствует",
                    "Требуется срочное направление на консультацию", "Открыть дополнительные ячейки для записи"))
        ));
        return m;
    }
}
