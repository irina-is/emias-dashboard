package com.emias.dashboard.service;

import com.emias.dashboard.entity.HcvRegistry;
import com.emias.dashboard.repository.HcvRegistryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import com.emias.dashboard.model.PathwayStats;
import com.emias.dashboard.model.PcrDashboardStats;
import com.emias.dashboard.model.PcrTerritoryRow;
import com.emias.dashboard.model.SchemeDashboardStats;
import com.emias.dashboard.model.SchemeGroupRow;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Загружает свод ВГС из Excel в таблицу hcv_registry.
 * При каждой загрузке все старые записи заменяются новыми.
 */
@Service
public class HcvRegistryService {

    private static final Logger log = LoggerFactory.getLogger(HcvRegistryService.class);
    private static final int BATCH_SIZE = 500;

    private final HcvRegistryRepository repo;

    @PersistenceContext
    private EntityManager em;

    public HcvRegistryService(HcvRegistryRepository repo) {
        this.repo = repo;
    }

    /**
     * Парсит Excel-свод и сохраняет в БД.
     * Лист «СВОД», строка 1 — заголовок, со строки 2 — данные.
     * Колонки A–U (0–20).
     *
     * @return количество сохранённых строк
     */
    @Transactional
    public int upload(MultipartFile file) throws Exception {
        log.info("Загрузка свода ВГС: {}, {} байт", file.getOriginalFilename(), file.getSize());

        // Удаляем все старые записи
        repo.deleteAll();
        em.flush();
        em.clear();

        DataFormatter fmt = new DataFormatter();
        List<HcvRegistry> batch = new ArrayList<>(BATCH_SIZE);
        int total = 0;

        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            // Ищем лист "СВОД", если не найден — берём первый
            Sheet sheet = wb.getSheet("СВОД");
            if (sheet == null) sheet = wb.getSheetAt(0);

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                // Пропускаем пустые строки (нет территории и ФИО)
                String territory = cell(fmt, row, 0);
                String fullName  = cell(fmt, row, 2);
                if (territory.isBlank() && fullName.isBlank()) continue;

                // Структура файла (0-based, буква столбца Excel):
                // A=0  Территория
                // B=1  Статус ВИМИС
                // C=2  ФИО
                // D=3  ПЦР (ОТРИЦАТЕЛЬНЫЙ / ПОЛОЖИТЕЛЬНЫЙ / пусто)
                // E=4  ВГС / Генотип
                // F=5  Стадия фиброза
                // G=6  (не используется)
                // H=7  Заключение МОНИКИ/Центр СПИД (да/нет)
                // I=8  Заключение МОНИКИ, схема рекомендованной терапии (да/нет)
                // J=9  Дата выданного заключения МОНИКИ/Центр СПИД
                // K=10 Условия МП (амб./ДС)
                // L=11 Дата выдачи 057у / рецепта
                // M=12 Схема лечения (код)
                // N=13 Продолжительность терапии
                // O=14 Дата начала курса
                // P=15 Куда маршрутизирован (МО)
                // Q=16 Статус лечения
                // R=17 (резерв)
                // S=18 Дата завершения курса лечения
                // T=19 Дата ПЦР УВО12
                // U=20 Результат УВО12
                // U=20 Примечания
                HcvRegistry rec = new HcvRegistry();
                rec.setTerritory(territory);
                rec.setVimisStatus(cell(fmt, row, 1));
                rec.setFullName(fullName);
                rec.setPcrStatus(cell(fmt, row, 3));
                rec.setHcvGenotype(cell(fmt, row, 4));
                rec.setFibrosisStage(cell(fmt, row, 5));
                rec.setMonikConclusion(cell(fmt, row, 7));
                rec.setMonikConclusionDate(cellAsDate(fmt, row, 9));
                rec.setCareConditions(cell(fmt, row, 10)); // K: амбулаторно / дневной стационар
                rec.setReferralDate(parseDate(cell(fmt, row, 11)));
                rec.setTreatmentScheme(cell(fmt, row, 12));
                rec.setTreatmentDurationWeeks(cell(fmt, row, 13));
                rec.setTreatmentStartDate(parseDate(cell(fmt, row, 14)));
                rec.setRoutedTo(cell(fmt, row, 15));
                rec.setTreatmentStatus(cell(fmt, row, 16));
                rec.setTreatmentEndDate(cellAsDate(fmt, row, 18)); // дата завершения курса (S)
                rec.setUvo12PcrDate(parseDate(cellAsDate(fmt, row, 19))); // T: дата ПЦР УВО12 (только валидные даты)
                rec.setUvo12Result(cell(fmt, row, 20));
                rec.setNotes(cell(fmt, row, 20));

                batch.add(rec);

                if (batch.size() == BATCH_SIZE) {
                    repo.saveAll(batch);
                    em.flush();
                    em.clear();
                    total += batch.size();
                    log.info("Сохранено {} строк свода ВГС...", total);
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                repo.saveAll(batch);
                em.flush();
                em.clear();
                total += batch.size();
            }
        }

        log.info("Свод ВГС загружен: {} строк", total);
        return total;
    }

    /** Дашборд «Назначение»: заключения МОНИКИ / Центр СПИД + начавшие лечение в 2026. */
    public java.util.Map<String, Object> buildNaznachenieDashboard() {
        List<HcvRegistry> all = repo.findAll();

        long totalDa = 0, totalNet = 0, ambCount = 0, dsCount = 0;
        java.util.Map<String, Long> byTerritoryDa  = new TreeMap<>();
        java.util.Map<String, Long> byTerritoryAmb = new TreeMap<>();
        java.util.Map<String, Long> byTerritoryDs  = new TreeMap<>();

        for (HcvRegistry r : all) {
            // Фильтр: дата заключения МОНИКИ в 2026 году
            String d = r.getMonikConclusionDate();
            if (d == null || !d.contains("2026")) continue;

            String conc = r.getMonikConclusion();
            boolean isDa = conc != null && conc.trim().equalsIgnoreCase("да");
            boolean isNet = conc != null && conc.trim().equalsIgnoreCase("нет");

            if (isNet) totalNet++;
            if (!isDa) continue; // все таблицы и счётчики — только «да»

            totalDa++;
            String t = r.getTerritory() != null ? r.getTerritory().trim() : "Не указана";
            byTerritoryDa.merge(t, 1L, Long::sum);

            // Разбивка по условиям МП
            String cond = r.getCareConditions();
            if (cond != null) {
                String cl = cond.toLowerCase();
                if (cl.contains("амбулатор")) {
                    ambCount++;
                    byTerritoryAmb.merge(t, 1L, Long::sum);
                } else if (cl.contains("дневной")) {
                    dsCount++;
                    byTerritoryDs.merge(t, 1L, Long::sum);
                }
            }
        }

        java.util.Map<String, Long> sortedDa = sortDesc(byTerritoryDa);
        java.util.Map<String, Long> sortedAmb = sortDesc(byTerritoryAmb);
        java.util.Map<String, Long> sortedDs  = sortDesc(byTerritoryDs);

        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("totalDa",   totalDa);
        result.put("totalNet",  totalNet);
        result.put("totalAll",  totalDa + totalNet);
        result.put("ambCount",  ambCount);
        result.put("dsCount",   dsCount);
        result.put("byTerritory",    sortedDa);
        result.put("byTerritoryAmb", sortedAmb);
        result.put("byTerritoryDs",  sortedDs);
        return result;
    }

    private java.util.Map<String, Long> sortDesc(java.util.Map<String, Long> map) {
        return map.entrySet().stream()
                .sorted(java.util.Map.Entry.<String,Long>comparingByValue().reversed())
                .collect(java.util.stream.Collectors.toMap(
                        java.util.Map.Entry::getKey, java.util.Map.Entry::getValue,
                        (a, b) -> a, java.util.LinkedHashMap::new));
    }

    /** Возвращает количество записей в реестре. */
    public long count() {
        return repo.count();
    }

    /**
     * Контроль ПЦР: пациенты, окончившие лечение в 2026 году (столбец S),
     * у которых заполнена дата ПЦР УВО12 (столбец T).
     */
    public long countUvo12PcrDateFilled() {
        return repo.findAll().stream()
                .filter(r -> r.getTreatmentEndDate() != null && r.getTreatmentEndDate().contains("закончили в 2026 году"))
                .filter(r -> r.getUvo12PcrDate() != null && !r.getUvo12PcrDate().isBlank())
                .count();
    }

    /** Пролечено амбулаторно в 2026 году: пометка в S + "амбулаторно" в K. */
    public long countAmbulatory2026() {
        return repo.findAll().stream()
                .filter(r -> r.getTreatmentEndDate() != null && r.getTreatmentEndDate().contains("закончили в 2026 году"))
                .filter(r -> r.getCareConditions() != null && r.getCareConditions().toLowerCase().contains("амбулатор"))
                .count();
    }

    /** Пролечено в дневном стационаре в 2026 году: пометка в S + "дневной стационар" или "ДС" в K. */
    public long countDayHospital2026() {
        return repo.findAll().stream()
                .filter(r -> r.getTreatmentEndDate() != null && r.getTreatmentEndDate().contains("закончили в 2026 году"))
                .filter(r -> {
                    String c = r.getCareConditions();
                    if (c == null) return false;
                    String lower = c.toLowerCase();
                    return lower.contains("дневной стационар") || lower.contains("дс");
                })
                .count();
    }

    /** Количество записей с датой завершения курса (столбец S) за 2026 год. */
    public long countTreatmentEndDateFilled() {
        return repo.findAll().stream()
                .filter(r -> r.getTreatmentEndDate() != null && r.getTreatmentEndDate().contains("закончили в 2026 году"))
                .count();
    }

    /** Количество непустых ячеек столбца B (статус ВИМИС) по всему своду. */
    public long countVimisNonEmpty() {
        return repo.findAll().stream()
                .filter(r -> r.getVimisStatus() != null && !r.getVimisStatus().isBlank())
                .count();
    }

    /**
     * Считает статистику пути пациента.
     * Фильтр: только пациенты с датой начала лечения в 2026 году.
     */
    public PathwayStats buildPathwayStats() {
        List<HcvRegistry> all = repo.findAll();

        long total = 0, pcrTotal = 0, pcrPositive = 0, pcrNegative = 0, vimisYes = 0, monikYes = 0, schemeAssigned = 0;
        long statusQueue = 0, statusHospitalized = 0, statusCompleted = 0, statusInterrupted = 0;
        long uvo12Negative = 0, uvo12Done = 0;

        for (HcvRegistry r : all) {
            // Фильтр: дата начала лечения в 2026
            if (!isYear2026(r.getTreatmentStartDate())) continue;
            total++;

            // ПЦР по столбцу D: положительный / отрицательный
            String pcr = r.getPcrStatus();
            boolean isPos = containsIgnoreCase(pcr, "положительный");
            boolean isNeg = containsIgnoreCase(pcr, "отрицательный");
            if (isPos || isNeg) pcrTotal++;
            if (isPos) pcrPositive++;
            if (isNeg) pcrNegative++;
            if (isVimisYes(r.getVimisStatus()))                          vimisYes++;
            if (containsIgnoreCase(r.getMonikConclusion(), "да"))        monikYes++;
            if (notBlank(r.getTreatmentScheme()))                        schemeAssigned++;

            String status = lower(r.getTreatmentStatus());
            if (status.contains("очередь") || status.contains("очереди") || status.contains("ожидает")) statusQueue++;
            else if (status.contains("госпитализирован") || status.contains("выдано направление"))      statusHospitalized++;
            else if (status.contains("завершен") || status.contains("завершён"))                        statusCompleted++;
            else if (status.contains("прервал") || status.contains("прерван"))                          statusInterrupted++;

        }

        // УВО: пациенты, завершившие лечение в 2026 (столбец S)
        //      + сдавшие ПЦР УВО12 (столбец T заполнен)
        //      + результат столбца U = "отрицательно" / "отрицательный"
        for (HcvRegistry r : all) {
            if (r.getTreatmentEndDate() == null || !r.getTreatmentEndDate().contains("закончили в 2026 году")) continue;
            if (r.getUvo12PcrDate() == null || r.getUvo12PcrDate().isBlank()) continue;
            uvo12Done++;
            String uvo = lower(r.getUvo12Result());
            if (uvo.contains("отрицатель")) uvo12Negative++;
        }

        return new PathwayStats(total, pcrTotal, pcrPositive, pcrNegative, vimisYes, monikYes, schemeAssigned,
                statusQueue, statusHospitalized, statusCompleted, statusInterrupted,
                uvo12Negative, uvo12Done);
    }

    /**
     * Строит статистику дашборда ПЦР.
     * Считает ВСЕ записи без фильтра по году.
     * Разбивка: по территории и по году (из даты начала лечения).
     */
    public PcrDashboardStats buildPcrDashboard() {
        List<HcvRegistry> all = repo.findAll();

        long totalAll = 0, totalPositive = 0, totalNegative = 0, totalNoInfo = 0;

        // territory → { year → count }
        // Используем TreeMap чтобы территории шли по алфавиту
        Map<String, Map<String, Long>> territoryYearMap = new TreeMap<>();
        // territory → [total, positive, negative, noInfo]
        Map<String, long[]> territorySummary = new TreeMap<>();
        // Все встреченные годы
        TreeSet<String> yearsSet = new TreeSet<>();

        for (HcvRegistry r : all) {
            totalAll++;

            // Статус ПЦР
            String pcr = lower(r.getPcrStatus());
            boolean isPositive = pcr.contains("положительный");
            boolean isNegative = pcr.contains("отрицательный");
            if (isPositive)       totalPositive++;
            else if (isNegative)  totalNegative++;
            else                  totalNoInfo++;

            // Год из даты начала лечения
            String year = extractYear(r.getTreatmentStartDate());
            yearsSet.add(year);

            // Территория
            String territory = (r.getTerritory() != null && !r.getTerritory().isBlank())
                    ? r.getTerritory().trim() : "Не указано";

            // Накапливаем по territory+year
            territoryYearMap
                .computeIfAbsent(territory, k -> new TreeMap<>())
                .merge(year, 1L, Long::sum);

            // Сводка по территории
            long[] s = territorySummary.computeIfAbsent(territory, k -> new long[4]);
            s[0]++;
            if (isPositive)      s[1]++;
            else if (isNegative) s[2]++;
            else                 s[3]++;
        }

        // Порядок лет: числовые сначала (по возрастанию), "Не указан" в конце
        List<String> years = new ArrayList<>();
        for (String y : yearsSet) {
            if (!y.equals("Не указан")) years.add(y);
        }
        years.sort(null);

        // Итог по годам (по всем территориям)
        Map<String, Long> totalByYear = new LinkedHashMap<>();
        for (String y : years) {
            long sum = 0;
            for (Map<String, Long> yearMap : territoryYearMap.values()) {
                sum += yearMap.getOrDefault(y, 0L);
            }
            totalByYear.put(y, sum);
        }

        // Строки таблицы, сортируем по убыванию total
        List<PcrTerritoryRow> rows = new ArrayList<>();
        for (Map.Entry<String, long[]> e : territorySummary.entrySet()) {
            long[] s = e.getValue();
            Map<String, Long> byYear = new LinkedHashMap<>();
            Map<String, Long> ym = territoryYearMap.getOrDefault(e.getKey(), Map.of());
            for (String y : years) byYear.put(y, ym.getOrDefault(y, 0L));
            rows.add(new PcrTerritoryRow(e.getKey(), s[0], s[1], s[2], s[3], byYear));
        }
        rows.sort((a, b) -> Long.compare(b.total, a.total));

        return new PcrDashboardStats(totalAll, totalPositive, totalNegative, totalNoInfo,
                years, totalByYear, rows);
    }

    /**
     * Считает записи только за указанный год (по дате начала лечения).
     * Используется для плитки «ПЦР + генотип» в пути пациента на странице Гепатит С.
     */
    public PcrDashboardStats buildPcrDashboardForYear(int filterYear) {
        List<HcvRegistry> all = repo.findAll();
        String yearStr = String.valueOf(filterYear);

        long totalAll = 0, totalPositive = 0, totalNegative = 0, totalNoInfo = 0;
        Map<String, long[]> territorySummary = new TreeMap<>();

        for (HcvRegistry r : all) {
            if (!yearStr.equals(extractYear(r.getTreatmentStartDate()))) continue;
            totalAll++;
            String pcr = lower(r.getPcrStatus());
            boolean isPositive = pcr.contains("положительный");
            boolean isNegative = pcr.contains("отрицательный");
            if (isPositive)       totalPositive++;
            else if (isNegative)  totalNegative++;
            else                  totalNoInfo++;

            String territory = (r.getTerritory() != null && !r.getTerritory().isBlank())
                    ? r.getTerritory().trim() : "Не указано";
            long[] s = territorySummary.computeIfAbsent(territory, k -> new long[4]);
            s[0]++;
            if (isPositive)      s[1]++;
            else if (isNegative) s[2]++;
            else                 s[3]++;
        }

        List<PcrTerritoryRow> rows = new ArrayList<>();
        Map<String, Long> byYear = Map.of(yearStr, totalAll);
        for (Map.Entry<String, long[]> e : territorySummary.entrySet()) {
            long[] s = e.getValue();
            rows.add(new PcrTerritoryRow(e.getKey(), s[0], s[1], s[2], s[3],
                    new java.util.LinkedHashMap<>(Map.of(yearStr, s[0]))));
        }
        rows.sort((a, b) -> Long.compare(b.total, a.total));

        return new PcrDashboardStats(totalAll, totalPositive, totalNegative, totalNoInfo,
                List.of(yearStr), byYear, rows);
    }

    /** Считает заключения МОНИКИ/Центр СПИД за 2026 год (по дате заключения). */
    public long countMonikConclusion2026() {
        return repo.findAll().stream()
                .filter(r -> {
                    String d = r.getMonikConclusionDate();
                    return d != null && d.contains("2026");
                })
                .count();
    }

    /** Считает пациентов с заключением МОНИКИ за 2026, направленных на амбулаторное лечение. */
    public long countMonikAmbulatory2026() {
        return repo.findAll().stream()
                .filter(r -> {
                    String d = r.getMonikConclusionDate();
                    if (d == null || !d.contains("2026")) return false;
                    String c = r.getCareConditions();
                    return c != null && c.toLowerCase().contains("амбулатор");
                })
                .count();
    }

    /** Считает пациентов с заключением МОНИКИ за 2026, направленных в дневной стационар. */
    public long countMonikDayHospital2026() {
        return repo.findAll().stream()
                .filter(r -> {
                    String d = r.getMonikConclusionDate();
                    if (d == null || !d.contains("2026")) return false;
                    String c = r.getCareConditions();
                    return c != null && c.toLowerCase().contains("дневной");
                })
                .count();
    }

    /**
     * Рейтинг МО по количеству пролеченных в дневном стационаре (за 2026 год).
     * Возвращает список пар [orgName, count], отсортированный по убыванию.
     */
    /**
     * Рейтинг МО по дневному стационару за 2026.
     */
    public List<Object[]> buildDayHospitalRatingRows() {
        return buildMoRating(r -> {
            String c = r.getCareConditions();
            return c != null && c.toLowerCase().contains("дневной")
                    && "2026".equals(extractYear(r.getTreatmentStartDate()));
        });
    }

    /**
     * Рейтинг МО по амбулаторному лечению за 2026.
     */
    public List<Object[]> buildAmbulatoryRatingRows() {
        return buildMoRating(r -> {
            String c = r.getCareConditions();
            return c != null && c.toLowerCase().contains("амбулатор")
                    && "2026".equals(extractYear(r.getTreatmentStartDate()));
        });
    }

    /**
     * Рейтинг МО по УВО12 — тот же фильтр, что в buildPathwayStats (29 итого).
     */
    public List<Object[]> buildUvoRatingRows() {
        return buildMoRating(r -> {
            if (r.getTreatmentEndDate() == null || !r.getTreatmentEndDate().contains("закончили в 2026 году")) return false;
            if (r.getUvo12PcrDate() == null || r.getUvo12PcrDate().isBlank()) return false;
            return lower(r.getUvo12Result()).contains("отрицатель");
        });
    }

    /**
     * Аналитика: топ-5 МО по среднему ожиданию (МОНИКИ → лечение, 2026)
     * и топ-5 МО по риску УВО12 (завершили, но УВО12 не проведён).
     * Возвращает Map с ключами "waitingTop5" и "uvoRiskTop5".
     * Каждый элемент waitingTop5: Object[]{moName, patientCount, avgDays}.
     * Каждый элемент uvoRiskTop5: Object[]{moName, completedCount, uvo12Count, withoutUvo12Count}.
     */
    public Map<String, Object> buildAnalyticsStats() {
        List<HcvRegistry> all = repo.findAll();

        // Топ-5 МО по среднему ожиданию МОНИКИ → начало лечения (2026)
        Map<String, long[]> waitMap = new java.util.LinkedHashMap<>(); // mo -> [sumDays, count]
        for (HcvRegistry r : all) {
            if (!isYear2026(r.getTreatmentStartDate())) continue;
            String monik = r.getMonikConclusionDate();
            String start = r.getTreatmentStartDate();
            if (monik == null || monik.isBlank() || start == null || start.isBlank()) continue;
            LocalDate d1 = parseLocalDate(monik);
            LocalDate d2 = parseLocalDate(start);
            if (d1 == null || d2 == null || !d2.isAfter(d1)) continue;
            long days = java.time.temporal.ChronoUnit.DAYS.between(d1, d2);
            String mo = r.getTerritory() != null && !r.getTerritory().isBlank() ? r.getTerritory().trim() : "Не указано";
            waitMap.computeIfAbsent(mo, k -> new long[]{0, 0});
            waitMap.get(mo)[0] += days;
            waitMap.get(mo)[1]++;
        }
        List<Object[]> waitingTop5 = waitMap.entrySet().stream()
                .filter(e -> e.getValue()[1] > 0)
                .sorted((a, b) -> Long.compare(b.getValue()[0] / b.getValue()[1], a.getValue()[0] / a.getValue()[1]))
                .limit(5)
                .map(e -> new Object[]{e.getKey(), e.getValue()[1], e.getValue()[0] / e.getValue()[1]})
                .collect(java.util.stream.Collectors.toList());

        // Топ-5 МО по риску УВО12: завершили курс, но нет даты ПЦР УВО12
        Map<String, long[]> uvoMap = new java.util.LinkedHashMap<>(); // mo -> [completed, uvo12Done]
        for (HcvRegistry r : all) {
            String status = lower(r.getTreatmentStatus());
            boolean completed = status.contains("завершен") || status.contains("завершён");
            if (!completed) continue;
            String mo = r.getTerritory() != null && !r.getTerritory().isBlank() ? r.getTerritory().trim() : "Не указано";
            uvoMap.computeIfAbsent(mo, k -> new long[]{0, 0});
            uvoMap.get(mo)[0]++;
            if (r.getUvo12PcrDate() != null && !r.getUvo12PcrDate().isBlank()) uvoMap.get(mo)[1]++;
        }
        List<Object[]> uvoRiskTop5 = uvoMap.entrySet().stream()
                .filter(e -> e.getValue()[0] > 0)
                .sorted((a, b) -> {
                    long withoutA = a.getValue()[0] - a.getValue()[1];
                    long withoutB = b.getValue()[0] - b.getValue()[1];
                    return Long.compare(withoutB, withoutA);
                })
                .limit(5)
                .map(e -> new Object[]{e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[0] - e.getValue()[1]})
                .collect(java.util.stream.Collectors.toList());

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("waitingTop5", waitingTop5);
        result.put("uvoRiskTop5", uvoRiskTop5);
        return result;
    }

    private LocalDate parseLocalDate(String s) {
        if (s == null || s.isBlank()) return null;
        for (DateTimeFormatter f : DATE_FORMATS) {
            try { return LocalDate.parse(s.trim(), f); } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private List<Object[]> buildMoRating(java.util.function.Predicate<HcvRegistry> filter) {
        Map<String, Long> counts = new TreeMap<>();
        for (HcvRegistry r : repo.findAll()) {
            if (!filter.test(r)) continue;
            String mo = (r.getTerritory() != null && !r.getTerritory().isBlank())
                    ? r.getTerritory().trim() : "Не указано";
            counts.merge(mo, 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(e -> new Object[]{e.getKey(), e.getValue()})
                .collect(java.util.stream.Collectors.toList());
    }

    /** Извлекает год из строки даты вида dd.MM.yyyy. Если не дата — "Не указан". */
    private String extractYear(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return "Не указан";
        // Формат dd.MM.yyyy — год это последние 4 символа
        String trimmed = dateStr.trim();
        if (trimmed.length() >= 4) {
            String possibleYear = trimmed.substring(trimmed.length() - 4);
            if (possibleYear.matches("\\d{4}")) return possibleYear;
        }
        return "Не указан";
    }

    private boolean isYear2026(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return false;
        return dateStr.contains("2026");
    }

    /**
     * Проверяет 2026 год в любом формате:
     *  - строковая дата: "dd.MM.2026", "мар.26", "мар-26"
     *  - числовой серийный номер Excel: 46023–46387 (01.01.2026–31.12.2026)
     */
    private static final int EXCEL_2026_MIN = 46023; // 01.01.2026
    private static final int EXCEL_2026_MAX = 46387; // 31.12.2026

    private boolean isYear2026orShort(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return false;
        String s = dateStr.trim();
        // Строковая дата с полным или коротким годом (любое положение "2026" в строке)
        if (s.contains("2026") || s.endsWith(".26") || s.endsWith("-26")) return true;
        // Числовой серийный номер Excel (ячейка без формата даты)
        try {
            int serial = Integer.parseInt(s);
            return serial >= EXCEL_2026_MIN && serial <= EXCEL_2026_MAX;
        } catch (NumberFormatException ignored) {}
        return false;
    }

    private boolean isVimisYes(String vimis) {
        if (vimis == null || vimis.isBlank()) return false;
        // Считаем только числовой ID (целый или дробный)
        return vimis.trim().matches("\\d+(\\.\\d+)?");
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private boolean containsIgnoreCase(String s, String keyword) {
        return s != null && s.toLowerCase().contains(keyword.toLowerCase());
    }

    private String lower(String s) {
        return s != null ? s.toLowerCase() : "";
    }

    private String cell(DataFormatter fmt, Row row, int col) {
        if (col >= row.getLastCellNum()) return "";
        String val = fmt.formatCellValue(row.getCell(col));
        return val != null ? val.trim() : "";
    }

    /**
     * Читает ячейку как дату. Если ячейка хранит числовой серийный номер Excel
     * (без формата даты), принудительно конвертирует через DateUtil.
     * Возвращает строку в формате "dd.MM.yyyy" или "" если ячейка пуста/не дата.
     */
    private static final DateTimeFormatter OUT_DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private String cellAsDate(DataFormatter fmt, Row row, int col) {
        if (col >= row.getLastCellNum()) return "";
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        // Числовая ячейка — конвертируем только если отформатирована как дата
        if ((cell.getCellType() == CellType.NUMERIC || cell.getCellType() == CellType.FORMULA)
                && DateUtil.isCellDateFormatted(cell)) {
            try {
                LocalDate d = cell.getLocalDateTimeCellValue().toLocalDate();
                if (d.getYear() >= 2000 && d.getYear() <= 2100) {
                    return d.format(OUT_DATE_FMT);
                }
            } catch (Exception ignored) {}
        }
        // Строковая или уже отформатированная ячейка
        String val = fmt.formatCellValue(cell);
        return val != null ? val.trim() : "";
    }

    // Форматтер для двузначного года: 2000–2099
    private static final DateTimeFormatter FMT_SHORT_YEAR =
        new DateTimeFormatterBuilder()
            .appendPattern("d.M.")
            .appendValueReduced(ChronoField.YEAR, 2, 2, 2000)
            .toFormatter();

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
        DateTimeFormatter.ofPattern("dd.MM.yyyy"),
        DateTimeFormatter.ofPattern("d.M.yyyy"),
        DateTimeFormatter.ofPattern("d.MM.yyyy"),
        DateTimeFormatter.ofPattern("dd.M.yyyy"),
        FMT_SHORT_YEAR   // покрывает dd.MM.yy, d.M.yy и их комбинации
    );

    /**
     * Пробует распарсить строку как дату.
     * Если строка — не дата (произвольный комментарий), возвращает "".
     */
    private String parseDate(String value) {
        if (value == null || value.isBlank()) return "";
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                LocalDate.parse(value.trim(), f);
                return value.trim();
            } catch (DateTimeParseException ignored) {}
        }
        return ""; // не дата — считаем как нет информации
    }

    /**
     * Строит статистику дашборда «Схемы назначения».
     * Группировка: (схема лечения, продолжительность) → кол-во пациентов + маршруты.
     * Использует все записи свода без фильтра по году.
     */
    public SchemeDashboardStats buildSchemeDashboard() {
        List<HcvRegistry> fullList = repo.findAll();

        // Только пациенты с заполненным столбцом B (Регистр ВИМИС) — для таблицы
        List<HcvRegistry> all = fullList.stream()
                .filter(r -> r.getVimisStatus() != null && !r.getVimisStatus().isBlank())
                .collect(java.util.stream.Collectors.toList());

        long totalAll = all.size();
        // Заключение МОНИКИ / Центр СПИД
        long withScheme = 4434L;
        TreeSet<String> schemesSet = new TreeSet<>();
        TreeSet<String> routesSet  = new TreeSet<>();

        // scheme -> { duration -> count }
        Map<String, Map<String, Long>> schemeDurationMap = new LinkedHashMap<>();
        // scheme -> { routedTo -> count }
        Map<String, Map<String, Long>> schemeRouteMap    = new LinkedHashMap<>();
        // Топ маршруты: МО -> суммарное количество
        Map<String, Long> topRoutes = new TreeMap<>();

        final String NONE = "Не указана";

        for (HcvRegistry r : all) {
            String scheme   = notBlankOr(r.getTreatmentScheme(),        NONE);
            String duration = notBlankOr(r.getTreatmentDurationWeeks(), NONE);
            String routed   = notBlankOr(r.getRoutedTo(),               NONE);

            if (!scheme.equals(NONE))  schemesSet.add(scheme);
            if (!routed.equals(NONE))  routesSet.add(routed);

            schemeDurationMap.computeIfAbsent(scheme, k -> new LinkedHashMap<>())
                    .merge(duration, 1L, Long::sum);
            schemeRouteMap.computeIfAbsent(scheme, k -> new LinkedHashMap<>())
                    .merge(routed, 1L, Long::sum);
            topRoutes.merge(routed, 1L, Long::sum);
        }

        // Строим строки — одна строка на уникальную схему
        List<SchemeGroupRow> groups = new ArrayList<>();
        for (String scheme : schemeDurationMap.keySet()) {
            long count = schemeDurationMap.get(scheme).values().stream()
                    .mapToLong(Long::longValue).sum();

            Map<String, Long> durSorted = new LinkedHashMap<>();
            schemeDurationMap.get(scheme).entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                    .forEach(e -> durSorted.put(e.getKey(), e.getValue()));

            Map<String, Long> routedSorted = new LinkedHashMap<>();
            schemeRouteMap.getOrDefault(scheme, Map.of()).entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                    .forEach(e -> routedSorted.put(e.getKey(), e.getValue()));

            groups.add(new SchemeGroupRow(scheme, count, durSorted, routedSorted));
        }

        // Сортировка: "Не указана" в конец, остальные по убыванию количества
        groups.sort((a, b) -> {
            boolean aBlank = a.scheme.equals(NONE);
            boolean bBlank = b.scheme.equals(NONE);
            if (aBlank != bBlank) return aBlank ? 1 : -1;
            return Long.compare(b.count, a.count);
        });

        // Топ-маршруты: убираем NONE, сортируем по убыванию
        Map<String, Long> topRoutesSorted = new LinkedHashMap<>();
        topRoutes.entrySet().stream()
                .filter(e -> !e.getKey().equals(NONE))
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(10)
                .forEach(e -> topRoutesSorted.put(e.getKey(), e.getValue()));

        return new SchemeDashboardStats(totalAll, withScheme,
                schemesSet.size(), routesSet.size(),
                groups, topRoutesSorted);
    }

    private String notBlankOr(String s, String fallback) {
        return (s != null && !s.isBlank()) ? s.trim() : fallback;
    }
}
