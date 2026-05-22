package com.emias.dashboard.service;

import com.emias.dashboard.entity.FacilityPlan;
import com.emias.dashboard.model.AgeDiagram;
import com.emias.dashboard.model.AgeGroupChange;
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
            "до 18", "18–25", "26–35", "36–45", "46–55", "56–65", "66–75", "76–85", "86+"
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

            if (age < 0) {
                continue; // дата рождения не заполнена или неверный формат
            }

            String group = getAgeGroup(age);
            counters.put(group, counters.get(group) + 1);
        }

        return new ArrayList<>(counters.values());
    }

    // Вычисляет возраст из строки "dd.MM.yyyy". Возвращает -1 если не удалось
    private int calculateAgeFromBirthDate(String birthDate) {
        if (birthDate.isEmpty()) {
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

        // Дедупликация внутри файла: один МКАБ со статусом "Выполнено" — одна запись
        Map<String, PatientRecord> uniquePatients = new LinkedHashMap<>();
        for (PatientRecord record : records) {
            String mkab = record.getMkabNumber();
            if (mkab != null && !mkab.isEmpty()
                    && !uniquePatients.containsKey(mkab)
                    && isCompleted(record)) {
                uniquePatients.put(mkab, record);
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
        // Уникальные МКАБ со статусом "Выполнено"
        Set<String> uniqueMkab = new HashSet<>();
        for (PatientRecord record : records) {
            if (record.getMkabNumber() != null && !record.getMkabNumber().isEmpty()
                    && isCompleted(record)) {
                uniqueMkab.add(record.getMkabNumber());
            }
        }

        long completed = uniqueMkab.size();
        long remaining = Math.max(0, annualPlan - completed);

        // Дней осталось до конца текущего года (включая сегодня)
        LocalDate today   = LocalDate.now();
        LocalDate yearEnd = LocalDate.of(today.getYear(), 12, 31);
        long daysLeft     = ChronoUnit.DAYS.between(today, yearEnd) + 1;

        long dailyRate = daysLeft > 0 ? (long) Math.ceil((double) remaining / daysLeft) : 0;

        return new ScreeningStats(completed, dailyRate, annualPlan);
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

        // Дедупликация: один МКАБ — одна запись (первое вхождение)
        Map<String, PatientRecord> uniquePatients = new LinkedHashMap<>();
        for (PatientRecord record : records) {
            String mkab = record.getMkabNumber();
            if (mkab != null && !mkab.isEmpty() && !uniquePatients.containsKey(mkab)) {
                uniquePatients.put(mkab, record);
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

            if (!isCompleted(record)) {
                continue;
            }
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
                        row.setAnnualPlanTotal(plan.getAnnualPlanTotal());
                        row.setMonthlyPlanTotal(plan.getMonthlyPlanTotal());
                        row.setWeeklyPlanTotal(plan.getWeeklyPlanTotal());
                    }
                }
            }

            rating.add(row);
        }

        rating.sort((a, b) -> Long.compare(b.getCompleted(), a.getCompleted()));

        return rating;
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
                                        long annualPlan) {
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
        Set<String> uniqueMkab   = new HashSet<>();
        Set<String> refusalMkab  = new HashSet<>();

        for (PatientRecord record : records) {
            String mkab = record.getMkabNumber();
            if (mkab == null || mkab.isEmpty()) continue;
            uniqueMkab.add(mkab);
            String result = record.getResearchResult();
            if (result != null && result.trim().equalsIgnoreCase("Отказ")) {
                refusalMkab.add(mkab);
            }
        }

        long total          = uniqueMkab.size();
        long refusals       = refusalMkab.size();
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
        Set<String> completedMkab = new HashSet<>();
        for (PatientRecord record : records) {
            String mkab = record.getMkabNumber();
            if (mkab != null && !mkab.isEmpty() && isCompleted(record)) {
                completedMkab.add(mkab);
            }
        }
        long completedTotal = completedMkab.size();
        long noDataCount    = completedTotal
                - countByResultAndStatus(records, "Без отклонений")
                - countByResultAndStatus(records, "Выявлено отклонение")
                - countByResultAndStatus(records, "Отказ");
        if (noDataCount < 0) noDataCount = 0;
        long noDataPercent  = completedTotal > 0 ? noDataCount * 100 / completedTotal : 0;
        boolean highNoData  = noDataPercent >= 10;

        // ── Сравнение текущего и прошлого месяца ─────────────────────────────────
        int currentMonthIdx  = today.getMonthValue() - 1; // 0-based
        int previousMonthIdx = currentMonthIdx - 1;

        long currentMonthCount  = 0;
        long previousMonthCount = 0;
        boolean slowingDown     = false;

        if (previousMonthIdx >= 0) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy");

            // Дедупликация по МКАБ для скринингов со статусом "Завершено"
            Set<String> seenMkab = new HashSet<>();
            for (PatientRecord record : records) {
                String mkab = record.getMkabNumber();
                if (mkab == null || mkab.isEmpty() || !isCompleted(record)) continue;
                if (!seenMkab.add(mkab)) continue;

                String dateStr = record.getResearchDate();
                if (dateStr == null || dateStr.isEmpty()) continue;
                try {
                    LocalDate date = LocalDate.parse(dateStr, fmt);
                    if (date.getYear() == today.getYear()) {
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

    // Считает уникальные МКАБ с конкретным researchResult только среди "Завершено"
    private long countByResultAndStatus(List<PatientRecord> records, String targetResult) {
        Set<String> mkabs = new HashSet<>();
        for (PatientRecord record : records) {
            String mkab = record.getMkabNumber();
            if (mkab == null || mkab.isEmpty() || !isCompleted(record)) continue;
            String result = record.getResearchResult();
            if (result != null && result.trim().equalsIgnoreCase(targetResult)) {
                mkabs.add(mkab);
            }
        }
        return mkabs.size();
    }

    // Считает уникальные МКАБ с конкретным значением researchResult
    private long countByResult(List<PatientRecord> records, String targetResult) {
        Set<String> mkabs = new HashSet<>();
        for (PatientRecord record : records) {
            String mkab = record.getMkabNumber();
            if (mkab == null || mkab.isEmpty()) continue;
            String result = record.getResearchResult();
            if (result != null && result.trim().equalsIgnoreCase(targetResult)) {
                mkabs.add(mkab);
            }
        }
        return mkabs.size();
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
        if (age < 18) return "до 18";
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
