package com.emias.dashboard.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class KpiService {

    // Плановые цифры (большие числа на карточках)
    public static final String TARGET_STAT = "kpi.target.stat"; // Дневной стационар
    public static final String TARGET_AMB  = "kpi.target.amb";  // Амбулаторное лечение
    public static final String TARGET_UVO  = "kpi.target.uvo";  // УВО / Излечены

    // Амбулаторное лечение
    private static final String AMB_LEAD_VAL  = "kpi.amb.lead.val";
    private static final String AMB_LEAD_DESC = "kpi.amb.lead.desc";
    private static final String AMB_LAG_VAL   = "kpi.amb.lag.val";
    private static final String AMB_LAG_DESC  = "kpi.amb.lag.desc";
    private static final String AMB_PROGRESS  = "kpi.amb.progress";

    // Дневной стационар
    private static final String STAT_LEAD_VAL  = "kpi.stat.lead.val";
    private static final String STAT_LEAD_DESC = "kpi.stat.lead.desc";
    private static final String STAT_LAG_VAL   = "kpi.stat.lag.val";
    private static final String STAT_LAG_DESC  = "kpi.stat.lag.desc";
    private static final String STAT_PROGRESS  = "kpi.stat.progress";

    private final SettingsService settings;

    public KpiService(SettingsService settings) {
        this.settings = settings;
    }

    public int getTarget(String key, int defaultVal) {
        try { return Integer.parseInt(settings.get(key, String.valueOf(defaultVal))); }
        catch (NumberFormatException e) { return defaultVal; }
    }

    public Map<String, String> get() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("targetStat",   settings.get(TARGET_STAT,  "4105"));
        m.put("targetAmb",    settings.get(TARGET_AMB,   "1416"));
        m.put("targetUvo",    settings.get(TARGET_UVO,   "5163"));
        m.put("ambLeadVal",   settings.get(AMB_LEAD_VAL,   "392"));
        m.put("ambLeadDesc",  settings.get(AMB_LEAD_DESC,  "выдано рецептов"));
        m.put("ambLagVal",    settings.get(AMB_LAG_VAL,    "0"));
        m.put("ambLagDesc",   settings.get(AMB_LAG_DESC,   "завершили лечение"));
        m.put("ambProgress",  settings.get(AMB_PROGRESS,   "28"));
        m.put("statLeadVal",  settings.get(STAT_LEAD_VAL,  "1 741"));
        m.put("statLeadDesc", settings.get(STAT_LEAD_DESC, "направлены на лечение"));
        m.put("statLagVal",   settings.get(STAT_LAG_VAL,   "977"));
        m.put("statLagDesc",  settings.get(STAT_LAG_DESC,  "закончили лечение"));
        m.put("statProgress", settings.get(STAT_PROGRESS,  "23"));
        return m;
    }

    public void save(Map<String, String> data) {
        Map<String, String> toSave = new LinkedHashMap<>();
        toSave.put(TARGET_STAT,   data.getOrDefault("targetStat",   "4105"));
        toSave.put(TARGET_AMB,    data.getOrDefault("targetAmb",    "1416"));
        toSave.put(TARGET_UVO,    data.getOrDefault("targetUvo",    "5163"));
        toSave.put(AMB_LEAD_VAL,  data.getOrDefault("ambLeadVal",   ""));
        toSave.put(AMB_LEAD_DESC, data.getOrDefault("ambLeadDesc",  ""));
        toSave.put(AMB_LAG_VAL,   data.getOrDefault("ambLagVal",    ""));
        toSave.put(AMB_LAG_DESC,  data.getOrDefault("ambLagDesc",   ""));
        toSave.put(AMB_PROGRESS,  data.getOrDefault("ambProgress",  ""));
        toSave.put(STAT_LEAD_VAL,  data.getOrDefault("statLeadVal",  ""));
        toSave.put(STAT_LEAD_DESC, data.getOrDefault("statLeadDesc", ""));
        toSave.put(STAT_LAG_VAL,   data.getOrDefault("statLagVal",   ""));
        toSave.put(STAT_LAG_DESC,  data.getOrDefault("statLagDesc",  ""));
        toSave.put(STAT_PROGRESS,  data.getOrDefault("statProgress", ""));
        settings.save(toSave);
    }
}
