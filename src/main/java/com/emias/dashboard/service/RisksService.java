package com.emias.dashboard.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class RisksService {

    private static final String KEY = "hcv.risks";

    private static final List<Map<String, String>> DEFAULTS = List.of(
        Map.of("level", "Критичный", "text", "Недоступность ТМК в ГБУЗ МО МОНИКИ к врачу-гепатологу"),
        Map.of("level", "Критичный", "text", "Нет технической возможности направить в Центр СПИД на ТМК и на очный приём. В соответствии с проектом приказа об организации оказания медицинской помощи взрослому населению с хроническими вирусными гепатитами в Московской области (согл-314738753-19)"),
        Map.of("level", "Критичный", "text", "Дубна — контракт в стадии заключения, самые ранние возможные поставки препаратов ожидаются в конце августа 2026 (около 465 пациентов)")
    );

    private final SettingsService settings;
    private final ObjectMapper    mapper;

    public RisksService(SettingsService settings, ObjectMapper mapper) {
        this.settings = settings;
        this.mapper   = mapper;
    }

    public List<Map<String, String>> get() {
        String json = settings.get(KEY, null);
        if (json == null || json.isBlank()) return DEFAULTS;
        try {
            return mapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return DEFAULTS;
        }
    }

    public void save(List<Map<String, String>> risks) throws Exception {
        settings.save(Map.of(KEY, mapper.writeValueAsString(risks)));
    }
}
