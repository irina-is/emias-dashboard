package com.emias.dashboard.service;

import com.emias.dashboard.entity.FacilityPlan;
import com.emias.dashboard.model.AgeDiagram;
import com.emias.dashboard.model.AgeGroupChange;
import com.emias.dashboard.model.AgeGroupStat;
import com.emias.dashboard.model.Conclusions;
import com.emias.dashboard.model.DiagramSeries;
import com.emias.dashboard.model.FacilityRating;
import com.emias.dashboard.model.MonthlyChartData;
import com.emias.dashboard.model.PatientRecord;
import com.emias.dashboard.model.ScreeningStats;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Отвечает за подготовку данных для диаграмм.
 */
@Service
public class DiagramService {

    // Названия возрастных групп — порядок важен, он совпадает с порядком значений
    private static final List<String> AGE_LABELS = List.of(
            "18–25", "26–35", "36–45", "46–55", "56–65", "66–75", "76–85", "86+", "Нет данных"
    );

    /**
     * Строит диаграмму по возрасту для нескольких недель сразу.
     * Каждая неделя — отдельная серия на графике.
     *
     * @param recordsByWeek словарь: название недели → список записей
     * @return AgeDiagram с подписями и сериями для каждой недели
     */
    public AgeDiagram buildAgeDiagram(Map<String, List<PatientRecord>> recordsByWeek) {
        List<DiagramSeries> series = new ArrayList<>();

        // Для каждой недели считаем количество пациентов по группам
        for (Map.Entry<String, List<PatientRecord>> entry : recordsByWeek.entrySet()) {
            String weekName              = entry.getKey();
            List<PatientRecord> records  = entry.getValue();
            List<Long> values            = countByAgeGroup(records);

            series.add(new DiagramSeries(weekName, values));
        }

        return new AgeDiagram(AGE_LABELS, series);
    }

    /**
     * Сравнивает две недели и возвращает изменение по каждой возрастной группе.
     * previousRecords — более ранняя неделя, currentRecords — более поздняя.
     */
    public List<AgeGroupChange> buildComparison(List<PatientRecord> previousRecords, List<PatientRecord> currentRecords) {
        List<Long> previousValues = countByAgeGroup(previousRecords);
        List<Long> currentValues  = countByAgeGroup(currentRecords);

        List<AgeGroupChange> result = new ArrayList<>();
        for (int i = 0; i < AGE_LABELS.size(); i++) {
            result.add(new AgeGroupChange(AGE_LABELS.get(i), previousValues.get(i), currentValues.get(i)));
        }
        return result;
    }

    /**
     * Считает количество пациентов в каждой возрастной группе.
     * Порядок значений совпадает с порядком AGE_LABELS.
     */
    private List<Long> countByAgeGroup(List<PatientRecord> records) {
        // Инициализируем счётчики нулями для каждой группы
        LinkedHashMap<String, Long> counters = new LinkedHashMap<>();
        for (String label : AGE_LABELS) {
            counters.put(label, 0L);
        }

        for (PatientRecord record : records) {
            int age = calculateAgeFromBirthDate(record.getBirthDate());

            if (age < 0 || age < 18) {
                // Неверный формат даты рождения или возраст до 18 — в «Нет данных»
                counters.put("Нет данных", counters.get("Нет данных") + 1);
                continue;
            }

            String group = getAgeGroup(age);
            counters.put(group, counters.get(group) + 1);
        }

        return new ArrayList<>(counters.values());
    }

    // Вычисляет возраст из строки "dd.MM.yyyy". Возвращает -1 если не удалось
    private int calculateAgeFromBirthDate(String birthDate) {
        if (birthDate == null || birthDate.isEmpty()) {
            return -1;
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
            LocalDate birth = LocalDate.parse(birthDate, formatter);
            return Period.between(birth, LocalDate.now()).getYears();
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Строит данные для графика "план / факт по месяцам".
     * Файл нарастающим итогом — берём только последний.
     * Дедуплицируем по МКАБ внутри файла: один пациент = один скрининг.
     * Считаем только записи со статусом "Выполнено".
     */
    public MonthlyChartData buildMonthlyChartData(List<PatientRecord> records, long annualPlan) {
        List<String> monthNames = List.of(
                "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
                "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
        );

        int currentYear = LocalDate.now().getYear();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        // Дедупликация внутри файла: один (МКАБ + Фамилия) со статусом "Завершено" — одна запись
        Map<String, PatientRecord> uniquePatients = new LinkedHashMap<>();
        for (PatientRecord record : records) {
            String key = uniqueKey(record);
            if (key != null && !uniquePatients.containsKey(key) && isCompleted(record)) {
                uniquePatients.put(key, record);
            }
        }

        // Раскладываем по месяцу даты исследования
        long[] factByMonth = new long[12]; // индекс 0 = Январь
        long noDateCount = 0;
        for (PatientRecord record : uniquePatients.values()) {
            String dateStr = record.getResearchDate();
            if (dateStr == null || dateStr.isEmpty()) {
                noDateCount++;
                continue;
            }
            try {
                LocalDate date = LocalDate.parse(dateStr, fmt);
                if (date.getYear() == currentYear) {
                    factByMonth[date.getMonthValue() - 1]++;
                } else {
                    noDateCount++; // дата из другого года — тоже "Без даты" для текущего графика
                }
            } catch (Exception e) {
                noDateCount++; // некорректный формат даты
            }
        }

        List<String> labels = new ArrayList<>(monthNames);

        List<Long> factList = new ArrayList<>();
        for (long v : factByMonth) {
            factList.add(v);
        }

        long monthlyPlan = Math.round((double) annualPlan / 12);
        return new MonthlyChartData(labels, factList, monthlyPlan, noDateCount);
    }

    /**
     * Считает сводную статистику плана по последнему загруженному файлу.
     * Файл нарастающим итогом — в нём уже все данные с начала года.
     */
    public ScreeningStats buildStats(List<PatientRecord> records, long annualPlan) {
        // Уникальные (МКАБ + Фамилия) со статусом "Завершено"
        Set<String> unique = new HashSet<>();
        for (PatientRecord record : records) {
            String key = uniqueKey(record);
            if (key != null && isCompleted(record)) {
                unique.add(key);
            }
        }

        long completed = unique.size();
        long remaining = Math.max(0, annualPlan - completed);

        // Дней осталось до конца текущего года (включая сегодня)
        LocalDate today   = LocalDate.now();
        LocalDate yearEnd = LocalDate.of(today.getYear(), 12, 31);
        long daysLeft     = ChronoUnit.DAYS.between(today, yearEnd) + 1;

        long dailyRate = daysLeft > 0 ? (long) Math.ceil((double) remaining / daysLeft) : 0;

        return new ScreeningStats(completed, dailyRate, annualPlan);
    }

    /**
     * Считает уникальных завершённых пациентов по целевым возрастам скрининга (25, 35, 45, 65, 75 лет).
     * Возраст берётся из поля ageAtResearch.
     */
    public List<AgeGroupStat> buildScreeningAgeCounts(List<PatientRecord> records) {
        int[] targetAges = {25, 35, 45, 55, 65, 75};
        long[] counts    = new long[targetAges.length];

        Set<String> seen = new HashSet<>();
        long total = 0;

        for (PatientRecord record : records) {
            if (!isCompleted(record)) continue;
            String key = uniqueKey(record);
            if (key == null || !seen.add(key)) continue;
            total++;

            int age = calculateAgeFromBirthDate(record.getBirthDate());
            if (age < 0) continue;
            for (int i = 0; i < targetAges.length; i++) {
                if (targetAges[i] == age) { counts[i]++; break; }
            }
        }

        long ageTotal = 0;
        for (long c : counts) ageTotal += c;

        List<AgeGroupStat> result = new ArrayList<>();
        result.add(new AgeGroupStat("Всего", ageTotal, null));
        for (int i = 0; i < targetAges.length; i++) {
            long c = counts[i];
            String pct = ageTotal > 0
                ? String.format("%.1f", c * 100.0 / ageTotal).replace('.', ',') + "% от всех"
                : "0% от всех";
            result.add(new AgeGroupStat(targetAges[i] + " лет", c, pct));
        }
        return result;
    }

    /**
     * Строит рейтинг медицинских организаций без данных о планах.
     */
    public List<FacilityRating> buildFacilityRating(List<PatientRecord> records) {
        return buildFacilityRating(records, Collections.emptyMap(), Collections.emptyMap());
    }

    /**
     * Строит рейтинг медицинских организаций с данными о планах.
     *
     * @param records     записи скрининга
     * @param mappingMap  словарь: screeningName → planName (из таблицы соответствий)
     * @param plansByName словарь: planName → FacilityPlan (из файла планов)
     */
    public List<FacilityRating> buildFacilityRating(
            List<PatientRecord> records,
            Map<String, String> mappingMap,
            Map<String, FacilityPlan> plansByName) {

        // Дедупликация: один (МКАБ + Фамилия) среди завершённых — первое завершённое вхождение.
        // Это обеспечивает совпадение итога с buildStats.
        Map<String, PatientRecord> uniquePatients = new LinkedHashMap<>();
        for (PatientRecord record : records) {
            String key = uniqueKey(record);
            if (key != null && isCompleted(record) && !uniquePatients.containsKey(key)) {
                uniquePatients.put(key, record);
            }
        }

        // counts[0]=Завершено, [1]=Без отклонений, [2]=С отклонениями, [3]=Отказ, [4]=Нет данных
        Map<String, long[]> counts = new LinkedHashMap<>();

        for (PatientRecord record : uniquePatients.values()) {
            String facility = record.getFacilityFrom();
            if (facility == null || facility.isEmpty()) {
                facility = "Не указано";
            }

            if (!counts.containsKey(facility)) {
                counts.put(facility, new long[5]);
            }
            long[] c = counts.get(facility);

            c[0]++;

            String result = record.getResearchResult();
            if (result == null) result = "";
            result = result.trim();

            if (result.equalsIgnoreCase("Без отклонений")) {
                c[1]++;
            } else if (result.equalsIgnoreCase("Выявлено отклонение")) {
                c[2]++;
            } else if (result.equalsIgnoreCase("Отказ")) {
                c[3]++;
            } else {
                c[4]++;
            }
        }

        List<FacilityRating> rating = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : counts.entrySet()) {
            long[] c = entry.getValue();
            FacilityRating row = new FacilityRating(entry.getKey(), c[0], c[1], c[2], c[3], c[4]);

            // Сопоставляем план через таблицу соответствий
            if (!mappingMap.isEmpty()) {
                String screeningName = entry.getKey().trim();
                String planName = mappingMap.get(screeningName);
                if (planName != null) {
                    FacilityPlan plan = plansByName.get(planName);
                    if (plan != null) {
                        row.setAnnualPlanTotal(plan.getAnnualPlanAllAges());
                        row.setMonthlyPlanTotal(plan.getMonthlyPlanAllAges());
                        row.setWeeklyPlanTotal(plan.getWeeklyPlanAllAges());
                    }
                }
            }

            rating.add(row);
        }

        rating.sort((a, b) -> {
            Integer pa = a.getCompletionPercent();
            Integer pb = b.getCompletionPercent();
            if (pa == null && pb == null) return 0;
            if (pa == null) return 1;   // без плана — в конец
            if (pb == null) return -1;
            return Integer.compare(pb, pa); // по убыванию %
        });

        return rating;
    }

    /**
     * Проставляет monthlyCompleted — количество уникальных завершённых скринингов
     * за весь текущий календарный месяц (по всем датам загрузки в месяце).
     */
    public void enrichWithMonthlyFact(List<FacilityRating> rating, List<PatientRecord> monthlyRecords) {
        Map<String, Long> monthlyCounts = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();

        for (PatientRecord record : monthlyRecords) {
            String key = uniqueKey(record);
            if (key == null || !seen.add(key)) continue;
            if (!isCompleted(record)) continue;

            String facility = record.getFacilityFrom();
            if (facility == null || facility.isEmpty()) facility = "Не указано";
            monthlyCounts.merge(facility, 1L, Long::sum);
        }

        for (FacilityRating row : rating) {
            row.setMonthlyCompleted(monthlyCounts.getOrDefault(row.getName(), 0L));
        }
    }

    /**
     * Формирует автоматические выводы по дашборду.
     *
     * 1. Риск невыполнения плана: факт < прогноз на сегодня (равномерное распределение плана по дням года).
     * 2. Много отказов: отказы > 10% от всех уникальных МКАБ — указываем ЛПУ с максимальным числом отказов.
     */
    public Conclusions buildConclusions(List<PatientRecord> records,
                                        ScreeningStats stats,
                                        List<FacilityRating> rating,
                                        long annualPlan,
                                        String lastUploadDate) {
        // ── Риск невыполнения плана ───────────────────────────────────────────────
        LocalDate today      = LocalDate.now();
        int dayOfYear        = today.getDayOfYear();
        int daysInYear       = today.isLeapYear() ? 366 : 365;
        long projected       = Math.round((double) annualPlan * dayOfYear / daysInYear);

        boolean planAtRisk   = stats.getCompletedCount() < projected;
        long lagPercent      = 0;
        if (planAtRisk && projected > 0) {
            lagPercent = Math.round((double)(projected - stats.getCompletedCount()) / projected * 100);
        }

        // ── Много отказов ─────────────────────────────────────────────────────────
        Set<String> uniqueKeys   = new HashSet<>();
        Set<String> refusalKeys  = new HashSet<>();

        for (PatientRecord record : records) {
            String key = uniqueKey(record);
            if (key == null) continue;
            uniqueKeys.add(key);
            String result = record.getResearchResult();
            if (result != null && result.trim().equalsIgnoreCase("Отказ")) {
                refusalKeys.add(key);
            }
        }

        long total          = uniqueKeys.size();
        long refusals       = refusalKeys.size();
        long refusalPercent = total > 0 ? refusals * 100 / total : 0;
        boolean highRefusals = refusalPercent > 10;

        // Находим ЛПУ с наибольшим числом отказов
        String topRefusalFacility = "";
        if (highRefusals) {
            FacilityRating topFacility = null;
            for (FacilityRating f : rating) {
                if (topFacility == null || f.getRefusal() > topFacility.getRefusal()) {
                    topFacility = f;
                }
            }
            if (topFacility != null && topFacility.getRefusal() > 0) {
                topRefusalFacility = topFacility.getName();
            }
        }

        // ── Много записей без результата (только среди "Завершено") ─────────────
        Set<String> completedKeys = new HashSet<>();
        for (PatientRecord record : records) {
            String key = uniqueKey(record);
            if (key != null && isCompleted(record)) {
                completedKeys.add(key);
            }
        }
        long completedTotal = completedKeys.size();
        long noDataCount    = completedTotal
                - countByResultAndStatus(records, "Без отклонений")
                - countByResultAndStatus(records, "Выявлено отклонение")
                - countByResultAndStatus(records, "Отказ");
        if (noDataCount < 0) noDataCount = 0;
        long noDataPercent  = completedTotal > 0 ? noDataCount * 100 / completedTotal : 0;
        boolean highNoData  = noDataPercent >= 10;

        // ── Сравнение месяца последней загрузки с предыдущим месяцем ────────────
        // Используем дату последнего загруженного файла, а не сегодняшний день,
        // чтобы сравнивались реальные отчётные месяцы (например, март vs февраль),
        // а не текущий календарный месяц, в котором данных ещё нет.
        LocalDate lastUpload    = LocalDate.parse(lastUploadDate);
        int currentMonthIdx     = lastUpload.getMonthValue() - 1; // 0-based
        int previousMonthIdx    = currentMonthIdx - 1;
        int uploadYear          = lastUpload.getYear();

        long currentMonthCount  = 0;
        long previousMonthCount = 0;
        boolean slowingDown     = false;

        if (previousMonthIdx >= 0) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy");

            // Дедупликация по (МКАБ + Фамилия) для скринингов со статусом "Завершено"
            Set<String> seenKeys = new HashSet<>();
            for (PatientRecord record : records) {
                String key = uniqueKey(record);
                if (key == null || !isCompleted(record)) continue;
                if (!seenKeys.add(key)) continue;

                String dateStr = record.getResearchDate();
                if (dateStr == null || dateStr.isEmpty()) continue;
                try {
                    LocalDate date = LocalDate.parse(dateStr, fmt);
                    if (date.getYear() == uploadYear) {
                        if (date.getMonthValue() - 1 == currentMonthIdx)  currentMonthCount++;
                        if (date.getMonthValue() - 1 == previousMonthIdx) previousMonthCount++;
                    }
                } catch (Exception e) {
                    // пропускаем записи с неверной датой
                }
            }

            slowingDown = previousMonthCount > 0 && currentMonthCount < previousMonthCount;
        }

        return new Conclusions(planAtRisk, lagPercent, projected,
                highRefusals, refusalPercent, topRefusalFacility,
                highNoData, noDataPercent,
                slowingDown, currentMonthCount, previousMonthCount);
    }

    // Считает уникальные (МКАБ + Фамилия) с конкретным researchResult только среди "Завершено"
    private long countByResultAndStatus(List<PatientRecord> records, String targetResult) {
        Set<String> keys = new HashSet<>();
        for (PatientRecord record : records) {
            String key = uniqueKey(record);
            if (key == null || !isCompleted(record)) continue;
            String result = record.getResearchResult();
            if (result != null && result.trim().equalsIgnoreCase(targetResult)) {
                keys.add(key);
            }
        }
        return keys.size();
    }

    // Считает уникальные (МКАБ + Фамилия) с конкретным значением researchResult
    private long countByResult(List<PatientRecord> records, String targetResult) {
        Set<String> keys = new HashSet<>();
        for (PatientRecord record : records) {
            String key = uniqueKey(record);
            if (key == null) continue;
            String result = record.getResearchResult();
            if (result != null && result.trim().equalsIgnoreCase(targetResult)) {
                keys.add(key);
            }
        }
        return keys.size();
    }

    // Составной ключ уникальности: МКАБ + Фамилия. Null если МКАБ отсутствует.
    private String uniqueKey(PatientRecord record) {
        String mkab = record.getMkabNumber();
        if (mkab == null || mkab.isEmpty()) return null;
        String lastName = record.getLastName();
        return mkab + "|" + (lastName != null ? lastName.trim().toLowerCase() : "");
    }

    // Возвращает true если исследование считается выполненным скринингом
    private boolean isCompleted(PatientRecord record) {
        String status = record.getResearchStatus();
        if (status == null) return false;
        String s = status.trim();
        return s.equalsIgnoreCase("Завершено");
    }

    // Определяет возрастную группу. Например: 45 → "36–45"
    private String getAgeGroup(int age) {
        if (age < 26) return "18–25";
        if (age < 36) return "26–35";
        if (age < 46) return "36–45";
        if (age < 56) return "46–55";
        if (age < 66) return "56–65";
        if (age < 76) return "66–75";
        if (age < 86) return "76–85";
        return "86+";
    }
}
