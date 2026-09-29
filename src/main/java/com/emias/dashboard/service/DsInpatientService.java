package com.emias.dashboard.service;

import com.emias.dashboard.entity.DsChuzPlan;
import com.emias.dashboard.entity.DsHospitalization;
import com.emias.dashboard.repository.DsChuzPlanRepository;
import com.emias.dashboard.repository.DsHospitalizationRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Блок «Стационарная помощь» на дашборде Гепатит С:
 * План 2026 — из «дс_с чуз.xlsx», Факт 2026 — пациенты из «Путь пациента при лечении гепатита»
 * по ЛПУ прикрепления + пролеченные в ЧУЗ.
 */
@Service
public class DsInpatientService {

    public static final String TASK_KEY     = "inpatient.task";
    public static final String DEADLINE_KEY = "inpatient.deadline";

    /**
     * ЛПУ прикрепления → город из плана. Ключ — фрагмент нормализованного названия
     * (верхний регистр, Ё→Е, без кавычек) либо аббревиатура целиком в кавычках.
     * Учитываются только ГБУЗ — ФМБА, ЧУЗ РЖД, МОКНД и т.п. в план МО не входят.
     */
    private static final Map<String, String> STEMS = new LinkedHashMap<>();
    private static final Map<String, String> ABBREVIATIONS = Map.of(
            "ПОКБ", "Подольск",
            "ХКБ",  "Химки",
            "СКБ",  "Ступино",
            "НФБ",  "Наро-Фоминск",
            "ЧБ",   "Чехов");
    static {
        String[][] pairs = {
                {"БАЛАШИХ", "Балашиха"}, {"ВИДНОВ", "Видное"}, {"ВОЛОКОЛАМ", "Волоколамск"},
                {"ВОСКРЕСЕН", "Воскресенск"}, {"ДМИТРОВ", "Дмитров"}, {"ДОЛГОПРУД", "Долгопрудный"},
                {"ДОМОДЕДОВ", "Домодедово"}, {"ДУБНЕН", "Дубна"}, {"ЕГОРЬЕВ", "Егорьевск"},
                {"ЖУКОВ", "Жуковский"}, {"ЗАРАЙ", "Зарайск"}, {"ИСТРИН", "Истра"}, {"КАШИР", "Кашира"},
                {"КЛИНСК", "Клин"}, {"КОЛОМЕН", "Коломна"}, {"КОРОЛЕВ", "Королёв"},
                {"КОТЕЛЬНИК", "Котельники"}, {"КРАСНОГОРСК", "Красногорск"},
                {"КРАСНОЗНАМЕН", "Краснознаменск"}, {"ЛОБНЕН", "Лобня"}, {"ЛОТОШИН", "Лотошино"},
                {"ЛУХОВИЦ", "Луховицы"}, {"ЛЫТКАРИН", "Лыткарино"}, {"ЛЮБЕРЕЦ", "Люберцы"},
                {"МОЖАЙ", "Можайск"}, {"МЫТИЩ", "Мытищи"}, {"НАРО-ФОМИН", "Наро-Фоминск"},
                {"НОГИН", "Ногинск"}, {"ОДИНЦОВ", "Одинцово"}, {"ОРЕХОВО-ЗУЕВ", "Орехово-Зуево"},
                {"ПАВЛОВО-ПОСАД", "Павловский Посад"}, {"ПОДОЛЬ", "Подольск"},
                {"РОЗАНОВА", "Пушкино"}, {"ПУШКИН", "Пушкино"}, {"РАМЕН", "Раменское"},
                {"РЕУТОВ", "Реутов"}, {"РУЗСК", "Руза"}, {"СЕРГИЕВО-ПОСАД", "Сергиев Посад"},
                {"СЕРЕБРЯНО-ПРУД", "Серебряные Пруды"}, {"СЕРПУХОВ", "Серпухов"},
                {"СОЛНЕЧНОГОР", "Солнечногорск"}, {"СТУПИН", "Ступино"}, {"ХИМКИН", "Химки"},
                {"ЧЕХОВ", "Чехов"}, {"ШАТУР", "Шатура"}, {"ШАХОВСК", "Шаховская"},
                {"ЩЕЛКОВ", "Щёлково"}, {"ЭЛЕКТРОСТАЛ", "Электросталь"},
        };
        for (String[] p : pairs) STEMS.put(p[0], p[1]);
    }

    private static final Pattern QUOTED = Pattern.compile("[\"«»“”]\\s*([^\"«»“”]+?)\\s*[\"«»“”]");

    private final DsHospitalizationRepository hospRepo;
    private final DsChuzPlanRepository chuzRepo;
    private final SettingsService settingsService;

    public DsInpatientService(DsHospitalizationRepository hospRepo,
                              DsChuzPlanRepository chuzRepo,
                              SettingsService settingsService) {
        this.hospRepo = hospRepo;
        this.chuzRepo = chuzRepo;
        this.settingsService = settingsService;
    }

    public Map<String, Object> getDashboard() {
        return getDashboard(null, null);
    }

    /**
     * @param from начало периода динамики (включительно); null — за 6 дней до {@code to}
     * @param to   дата, на которую считается факт (включительно); null — конец последней полной недели
     */
    public Map<String, Object> getDashboard(LocalDate from, LocalDate to) {
        List<DsChuzPlan> plans = chuzRepo.findAllByOrderByMoAsc();
        List<DsHospitalization> patients = hospRepo.findAll();
        LocalDateTime uploadedAt = hospRepo.lastUploadedAt();

        // По умолчанию — последняя полная неделя (пн–вс) перед загрузкой выгрузки
        LocalDate base = uploadedAt != null ? uploadedAt.toLocalDate() : LocalDate.now();
        LocalDate defaultTo = base.minusDays(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        LocalDate defaultFrom = defaultTo.minusDays(6);

        LocalDate reportDate = to != null ? to : defaultTo;
        LocalDate weekStart = from != null ? from : reportDate.minusDays(6);
        if (weekStart.isAfter(reportDate)) {
            throw new IllegalArgumentException("Дата начала периода позже даты окончания");
        }

        LocalDate minDate = null, maxDate = null;
        for (DsHospitalization p : patients) {
            LocalDate d = p.getHospDate1();
            if (d == null) continue;
            if (minDate == null || d.isBefore(minDate)) minDate = d;
            if (maxDate == null || d.isAfter(maxDate)) maxDate = d;
        }

        Map<String, Integer> factByCity = new HashMap<>();
        Map<String, Integer> weekByCity = new HashMap<>();
        Map<String, Integer> unmatched = new TreeMap<>();
        for (DsHospitalization p : patients) {
            LocalDate d = p.getHospDate1();
            if (d != null && d.isAfter(reportDate)) continue;
            String city = cityOfAttachment(p.getAttachMo());
            if (city == null) {
                String name = p.getAttachMo() == null ? "(не указано)" : p.getAttachMo();
                unmatched.merge(name, 1, Integer::sum);
                continue;
            }
            String key = cityKey(city);
            factByCity.merge(key, 1, Integer::sum);
            if (d != null && !d.isBefore(weekStart)) weekByCity.merge(key, 1, Integer::sum);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        long totalPlan = 0, totalFact = 0, totalWeek = 0, totalGbuz = 0, totalChuz = 0;
        for (DsChuzPlan pl : plans) {
            String key  = cityKey(pl.getMo());
            int gbuz    = factByCity.getOrDefault(key, 0);
            int chuz    = pl.getChuzTreated() != null ? pl.getChuzTreated() : 0;
            int plan    = pl.getPlan2026() != null ? pl.getPlan2026() : 0;
            int week    = weekByCity.getOrDefault(key, 0);
            int fact    = gbuz + chuz;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("mo",    pl.getMo());
            m.put("plan",  plan);
            m.put("gbuz",  gbuz);
            m.put("chuz",  chuz);
            m.put("fact",  fact);
            m.put("pct",   plan > 0 ? (int) Math.round(fact * 100.0 / plan) : null);
            m.put("week",  week);
            rows.add(m);

            totalPlan += plan; totalFact += fact; totalWeek += week;
            totalGbuz += gbuz; totalChuz += chuz;
        }
        rows.sort(Comparator.comparingDouble(m -> {
            Integer plan = (Integer) m.get("plan");
            return plan == null || plan == 0 ? Double.MAX_VALUE : ((Integer) m.get("fact")) * 1.0 / plan;
        }));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hasData",    !plans.isEmpty());
        result.put("defaultFrom", defaultFrom);
        result.put("defaultTo",  defaultTo);
        result.put("minDate",    minDate);
        result.put("maxDate",    maxDate);
        result.put("reportDate", reportDate);
        result.put("weekStart",  weekStart);
        result.put("totalPlan",  totalPlan);
        result.put("totalFact",  totalFact);
        result.put("totalGbuz",  totalGbuz);
        result.put("totalChuz",  totalChuz);
        result.put("totalPct",   totalPlan > 0 ? Math.round(totalFact * 100.0 / totalPlan) : null);
        result.put("totalWeek",  totalWeek);
        result.put("rows",       rows);
        result.put("unmatched",  unmatched);
        result.put("task",       settingsService.get(TASK_KEY,
                "Подготовить пациентов к госпитализации, согласовать даты госпитализации в ДС, выдать направления 057у"));
        result.put("deadline",   settingsService.get(DEADLINE_KEY, ""));
        return result;
    }

    public void saveTask(String task, String deadline) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(TASK_KEY, task == null ? "" : task.trim());
        values.put(DEADLINE_KEY, deadline == null ? "" : deadline.trim());
        settingsService.save(values);
    }

    /** Город из плана по названию ЛПУ прикрепления, или null, если МО не из плана. */
    static String cityOfAttachment(String attachMo) {
        if (attachMo == null) return null;
        String n = normalize(attachMo);
        if (!n.startsWith("ГБУЗ")) return null;
        Matcher m = QUOTED.matcher(attachMo.toUpperCase().replace('Ё', 'Е'));
        while (m.find()) {
            String abbr = ABBREVIATIONS.get(m.group(1).trim());
            if (abbr != null) return abbr;
        }
        for (Map.Entry<String, String> e : STEMS.entrySet()) {
            if (n.contains(e.getKey())) return e.getValue();
        }
        return null;
    }

    /**
     * Ключ сопоставления города: первые 7 букв без пробелов/дефисов.
     * Нужен, потому что в плане встречаются «Раменск», «Жуковск», «Солнечногрск», «Руза ».
     */
    static String cityKey(String city) {
        String s = normalize(city).replaceAll("[^А-ЯA-Z]", "");
        return s.length() > 7 ? s.substring(0, 7) : s;
    }

    private static String normalize(String s) {
        return s.toUpperCase().replace('Ё', 'Е').replaceAll("[\"«»“”]", "").trim();
    }
}
