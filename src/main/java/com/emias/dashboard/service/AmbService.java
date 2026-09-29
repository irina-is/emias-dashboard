package com.emias.dashboard.service;

import com.emias.dashboard.entity.AmbDispense;
import com.emias.dashboard.entity.AmbPlanPatient;
import com.emias.dashboard.repository.AmbDispenseRepository;
import com.emias.dashboard.repository.AmbPlanPatientRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * Амбулаторное лечение: план — пофамильный список пациентов («Гроза_...xlsx»),
 * факт — плановый пациент найден в выгрузке отпуска препаратов «МО_Гепатит_ДДММГГГГ.xlsx»
 * (по СНИЛС или ФИО). Динамика — первый отпуск попал в выбранный период.
 */
@Service
public class AmbService {

    private static final Logger log = LoggerFactory.getLogger(AmbService.class);

    public static final String TASK_KEY     = "amb.task";
    public static final String DEADLINE_KEY = "amb.deadline";

    private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);
    private static final DateTimeFormatter RU_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final AmbPlanPatientRepository planRepo;
    private final AmbDispenseRepository dispRepo;
    private final SettingsService settingsService;

    public AmbService(AmbPlanPatientRepository planRepo,
                      AmbDispenseRepository dispRepo,
                      SettingsService settingsService) {
        this.planRepo = planRepo;
        this.dispRepo = dispRepo;
        this.settingsService = settingsService;
    }

    // ── План: пофамильный список ─────────────────────────────────────────────

    /**
     * @param append false — заменить весь план; true — добавить к текущему
     *               (пациенты с теми же ФИО и датой рождения не дублируются)
     * @return {added, skippedDuplicates, removedReplaced, total}
     */
    @Transactional
    public Map<String, Integer> uploadPlan(MultipartFile file, boolean append) throws IOException {
        log.info("=== Загрузка плана амбулаторного лечения '{}' (добавить: {}) ===", file.getOriginalFilename(), append);

        List<AmbPlanPatient> parsed = new ArrayList<>();
        Set<String> replacedKeys = new HashSet<>();
        String source = file.getOriginalFilename();
        LocalDateTime now = LocalDateTime.now();

        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            // ОБЯЗАТЕЛЬНО: без evaluator формулы вернут пустую строку
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();

            Map<String, Integer> h = headers(sheet.getRow(0), fmt, evaluator);
            Integer cAttach = find(h, "прикрепление");
            Integer cFio    = find(h, "фио");
            Integer cRepl   = find(h, "замена");
            Integer cBirth  = find(h, "дата рожд");
            Integer cSnils  = find(h, "снилс");
            Integer cDrug   = find(h, "препарат");
            Integer cPacks  = find(h, "кол-во");
            List<String> missing = new ArrayList<>();
            if (cAttach == null) missing.add("«Прикрепление»");
            if (cFio == null)    missing.add("«ФИО» или «ФИО (кто получает)»");
            if (!missing.isEmpty()) {
                throw new FileValidationException(List.of(
                        "Неверная структура файла: в первой строке не найдены столбцы " + String.join(", ", missing) + ".",
                        "Нужен пофамильный список (например, «Гроза_..._СВОД с заменами.xlsx»)."));
            }

            List<String> errors = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String repl = cRepl == null ? "" : str(row, cRepl, fmt, evaluator);
                if (!repl.isEmpty()) replacedKeys.add(fioKey(repl));
                String fio = str(row, cFio, fmt, evaluator);
                if (fio.isEmpty() || fio.toLowerCase().contains("итого")) continue;

                AmbPlanPatient p = new AmbPlanPatient();
                p.setReplacedFioKey(repl.isEmpty() ? null : cut(fioKey(repl), 300));
                p.setAttachMo(cut(str(row, cAttach, fmt, evaluator), 300));
                p.setFio(cut(fio, 300));
                p.setFioKey(cut(fioKey(fio), 300));
                try {
                    p.setBirthDate(cBirth == null ? null : date(row.getCell(cBirth), fmt, evaluator));
                } catch (IllegalArgumentException e) {
                    errors.add("Строка " + (i + 1) + ": " + e.getMessage());
                    continue;
                }
                p.setSnils(cSnils == null ? null : cut(digits(str(row, cSnils, fmt, evaluator)), 20));
                p.setDrug(cDrug == null ? null : cut(str(row, cDrug, fmt, evaluator), 300));
                p.setPacks(cPacks == null ? null : intOrNull(str(row, cPacks, fmt, evaluator)));
                p.setSourceFile(cut(source, 300));
                p.setUploadedAt(now);
                parsed.add(p);
                if (errors.size() >= 20) break;
            }
            if (!errors.isEmpty()) throw new FileValidationException(errors);
        }
        if (parsed.isEmpty()) {
            throw new FileValidationException(List.of("В файле нет пациентов: строка 1 — заголовки, дальше — по строке на пациента."));
        }

        int removedReplaced = 0;
        List<AmbPlanPatient> existing = append ? planRepo.findAll() : List.of();
        // Замены из уже загруженного плана тоже действуют: заменённый пациент не возвращается с новым файлом
        for (AmbPlanPatient p : existing) {
            if (p.getReplacedFioKey() != null) replacedKeys.add(p.getReplacedFioKey());
        }
        if (!append) {
            planRepo.deleteAllInBatch();
        } else {
            // Замены из нового файла убирают заменённых пациентов и из уже загруженного плана
            List<AmbPlanPatient> toRemove = existing.stream()
                    .filter(p -> replacedKeys.contains(p.getFioKey())).toList();
            planRepo.deleteAll(toRemove);
            removedReplaced = toRemove.size();
            existing = existing.stream().filter(p -> !replacedKeys.contains(p.getFioKey())).toList();
        }

        Set<String> seen = new HashSet<>();
        for (AmbPlanPatient p : existing) seen.add(dedupKey(p));
        List<AmbPlanPatient> toSave = new ArrayList<>();
        int duplicates = 0;
        for (AmbPlanPatient p : parsed) {
            if (replacedKeys.contains(p.getFioKey())) { removedReplaced++; continue; }
            if (!seen.add(dedupKey(p))) { duplicates++; continue; }
            toSave.add(p);
        }
        planRepo.saveAll(toSave);

        Map<String, Integer> res = new LinkedHashMap<>();
        res.put("added", toSave.size());
        res.put("skippedDuplicates", duplicates);
        res.put("removedReplaced", removedReplaced);
        res.put("total", (int) planRepo.count());
        log.info("=== План амбулаторно: {} ===", res);
        return res;
    }

    // ── Факт: выгрузка отпуска препаратов ────────────────────────────────────

    @Transactional
    public int uploadDispenses(MultipartFile file) throws IOException {
        log.info("=== Загрузка выгрузки амбулаторного отпуска '{}', размер: {} байт ===",
                file.getOriginalFilename(), file.getSize());
        List<AmbDispense> rows = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();

            Map<String, Integer> h = headers(sheet.getRow(0), fmt, evaluator);
            Integer cSnils = find(h, "снилс");
            Integer cMun   = find(h, "муниципальное");
            Integer cMo    = find(h, "наименование мо");
            Integer cFio   = find(h, "фио");
            Integer cMnn   = find(h, "мнн");
            Integer cQty   = find(h, "кол-во");
            Integer cDate  = find(h, "дата отпуска");
            if (cFio == null || cDate == null) {
                throw new FileValidationException(List.of(
                        "Неверная структура файла: в первой строке нужны столбцы «ФИО льготника» и «Дата отпуска».",
                        "Нужна выгрузка «МО_Гепатит_ДДММГГГГ.xlsx»."));
            }

            List<String> errors = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String fio = str(row, cFio, fmt, evaluator);
                if (fio.isEmpty() || fio.toLowerCase().contains("итого")) continue;

                AmbDispense d = new AmbDispense();
                d.setSnils(cSnils == null ? null : cut(digits(str(row, cSnils, fmt, evaluator)), 20));
                d.setMunicipality(cMun == null ? null : cut(str(row, cMun, fmt, evaluator), 300));
                d.setMo(cMo == null ? null : cut(str(row, cMo, fmt, evaluator), 1000));
                d.setFio(cut(fio, 300));
                d.setFioKey(cut(fioKey(fio), 300));
                d.setMnn(cMnn == null ? null : cut(str(row, cMnn, fmt, evaluator), 300));
                if (cQty != null) {
                    String q = str(row, cQty, fmt, evaluator).replace(",", ".").replace(" ", "");
                    try { d.setQty(q.isEmpty() ? null : Double.parseDouble(q)); } catch (NumberFormatException ignored) { }
                }
                try {
                    d.setDispenseDate(date(row.getCell(cDate), fmt, evaluator));
                } catch (IllegalArgumentException e) {
                    if (errors.size() < 20) errors.add("Строка " + (i + 1) + ": " + e.getMessage());
                    continue;
                }
                d.setUploadedAt(now);
                rows.add(d);
            }
            if (!errors.isEmpty()) throw new FileValidationException(errors);
        }
        if (rows.isEmpty()) throw new FileValidationException(List.of("В выгрузке нет строк с отпуском препаратов."));

        // Выгрузка — полный срез с начала года, заменяем целиком
        dispRepo.deleteAllInBatch();
        dispRepo.saveAll(rows);
        log.info("=== Амбулаторный отпуск: загружено {} строк ===", rows.size());
        return rows.size();
    }

    // ── Дашборд ─────────────────────────────────────────────────────────────

    /** Дата первого отпуска для каждого планового пациента (null — не найден в выгрузке). */
    private Map<AmbPlanPatient, LocalDate> firstDispense(List<AmbPlanPatient> plan) {
        Map<String, LocalDate> bySnils = new HashMap<>();
        Map<String, LocalDate> byFio = new HashMap<>();
        for (AmbDispense d : dispRepo.findAll()) {
            if (d.getDispenseDate() == null) continue;
            if (d.getSnils() != null && !d.getSnils().isEmpty()) bySnils.merge(d.getSnils(), d.getDispenseDate(), AmbService::min);
            if (d.getFioKey() != null) byFio.merge(d.getFioKey(), d.getDispenseDate(), AmbService::min);
        }
        Map<AmbPlanPatient, LocalDate> res = new HashMap<>();
        for (AmbPlanPatient p : plan) {
            LocalDate a = p.getSnils() != null && !p.getSnils().isEmpty() ? bySnils.get(p.getSnils()) : null;
            LocalDate b = byFio.get(p.getFioKey());
            res.put(p, a == null ? b : (b == null ? a : min(a, b)));
        }
        return res;
    }

    public Map<String, Object> getDashboard(LocalDate from, LocalDate to) {
        List<AmbPlanPatient> plan = planRepo.findAll();
        Map<AmbPlanPatient, LocalDate> first = firstDispense(plan);

        // По умолчанию — последняя полная неделя (пн–вс) перед загрузкой выгрузки
        LocalDateTime uploadedAt = dispRepo.lastUploadedAt();
        LocalDate base = uploadedAt != null ? uploadedAt.toLocalDate() : LocalDate.now();
        LocalDate defaultTo = base.minusDays(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        LocalDate defaultFrom = defaultTo.minusDays(6);
        LocalDate reportDate = to != null ? to : defaultTo;
        LocalDate weekStart = from != null ? from : reportDate.minusDays(6);
        if (weekStart.isAfter(reportDate)) {
            throw new IllegalArgumentException("Дата начала периода позже даты окончания");
        }

        Map<String, int[]> byMo = new LinkedHashMap<>();   // mo → {plan, fact, week}
        LocalDate minDate = null;
        for (AmbPlanPatient p : plan) {
            String city = DsInpatientService.cityOfAttachment(p.getAttachMo());
            String mo = city != null ? city : (p.getAttachMo() == null ? "(не указано)" : p.getAttachMo());
            int[] c = byMo.computeIfAbsent(mo, k -> new int[3]);
            c[0]++;
            LocalDate d = first.get(p);
            if (d == null) continue;
            if (minDate == null || d.isBefore(minDate)) minDate = d;
            if (!d.isAfter(reportDate)) {
                c[1]++;
                if (!d.isBefore(weekStart)) c[2]++;
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        int tPlan = 0, tFact = 0, tWeek = 0;
        for (Map.Entry<String, int[]> e : byMo.entrySet()) {
            int[] c = e.getValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("mo",   e.getKey());
            m.put("plan", c[0]);
            m.put("fact", c[1]);
            m.put("pct",  c[0] > 0 ? (int) Math.round(c[1] * 100.0 / c[0]) : null);
            m.put("week", c[2]);
            rows.add(m);
            tPlan += c[0]; tFact += c[1]; tWeek += c[2];
        }
        rows.sort(Comparator.comparingDouble((Map<String, Object> m) -> ((Integer) m.get("fact")) * 1.0 / (Integer) m.get("plan"))
                .thenComparing(m -> (String) m.get("mo")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hasData",     !plan.isEmpty());
        result.put("hasFact",     dispRepo.count() > 0);
        result.put("defaultFrom", defaultFrom);
        result.put("defaultTo",   defaultTo);
        result.put("minDate",     minDate);
        result.put("maxDate",     dispRepo.maxDispenseDate());
        result.put("reportDate",  reportDate);
        result.put("weekStart",   weekStart);
        result.put("totalPlan",   tPlan);
        result.put("totalFact",   tFact);
        result.put("totalPct",    tPlan > 0 ? Math.round(tFact * 100.0 / tPlan) : null);
        result.put("totalWeek",   tWeek);
        result.put("rows",        rows);
        result.put("task",        settingsService.get(TASK_KEY, "Пригласить пациентов и выписать рецепты оставшимся пациентам"));
        result.put("deadline",    settingsService.get(DEADLINE_KEY, ""));
        return result;
    }

    /** Для админки: сводка по загрузкам и плановые пациенты, которых нет в выгрузке. */
    public Map<String, Object> getAdminSummary() {
        List<AmbPlanPatient> plan = planRepo.findAll();
        Map<AmbPlanPatient, LocalDate> first = firstDispense(plan);
        List<Map<String, Object>> notFound = new ArrayList<>();
        Map<String, Integer> bySource = new TreeMap<>();
        for (AmbPlanPatient p : plan) {
            bySource.merge(p.getSourceFile() == null ? "—" : p.getSourceFile(), 1, Integer::sum);
            if (first.get(p) != null) continue;
            String city = DsInpatientService.cityOfAttachment(p.getAttachMo());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("mo",  city != null ? city : p.getAttachMo());
            m.put("fio", p.getFio());
            m.put("birthDate", p.getBirthDate());
            notFound.add(m);
        }
        notFound.sort(Comparator.comparing((Map<String, Object> m) -> String.valueOf(m.get("mo")))
                .thenComparing(m -> String.valueOf(m.get("fio"))));

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("planCount",        plan.size());
        res.put("planUploadedAt",   planRepo.lastUploadedAt());
        res.put("planBySource",     bySource);
        res.put("dispenseCount",    dispRepo.count());
        res.put("dispenseUploadedAt", dispRepo.lastUploadedAt());
        res.put("maxDispenseDate",  dispRepo.maxDispenseDate());
        res.put("notFound",         notFound);
        res.put("task",             settingsService.get(TASK_KEY, "Пригласить пациентов и выписать рецепты оставшимся пациентам"));
        res.put("deadline",         settingsService.get(DEADLINE_KEY, ""));
        return res;
    }

    public void saveTask(String task, String deadline) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(TASK_KEY, task == null ? "" : task.trim());
        values.put(DEADLINE_KEY, deadline == null ? "" : deadline.trim());
        settingsService.save(values);
    }

    // ── Вспомогательное ─────────────────────────────────────────────────────

    private static LocalDate min(LocalDate a, LocalDate b) { return a.isBefore(b) ? a : b; }

    private static String dedupKey(AmbPlanPatient p) {
        return p.getFioKey() + "|" + (p.getBirthDate() == null ? "" : p.getBirthDate());
    }

    /** ФИО для сопоставления: верхний регистр, Ё→Е, одиночные пробелы. */
    static String fioKey(String fio) {
        return fio == null ? "" : fio.toUpperCase().replace('Ё', 'Е').replaceAll("\\s+", " ").trim();
    }

    private static String digits(String s) {
        return s == null ? null : s.replaceAll("\\D", "");
    }

    private static Map<String, Integer> headers(Row row, DataFormatter fmt, FormulaEvaluator evaluator) {
        Map<String, Integer> h = new LinkedHashMap<>();
        if (row == null) return h;
        for (Cell c : row) {
            String v = fmt.formatCellValue(c, evaluator).trim().toLowerCase().replaceAll("\\s+", " ");
            if (!v.isEmpty()) h.putIfAbsent(v, c.getColumnIndex());
        }
        return h;
    }

    /** Первый столбец, заголовок которого начинается с {@code prefix}. */
    private static Integer find(Map<String, Integer> headers, String prefix) {
        for (Map.Entry<String, Integer> e : headers.entrySet()) {
            if (e.getKey().startsWith(prefix)) return e.getValue();
        }
        return null;
    }

    private static String str(Row row, int col, DataFormatter fmt, FormulaEvaluator evaluator) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        return fmt.formatCellValue(cell, evaluator).trim();
    }

    /** Дата: ячейка-дата, число Excel (в т.ч. с временем) или текст «дд.мм.гггг». */
    private static LocalDate date(Cell cell, DataFormatter fmt, FormulaEvaluator evaluator) {
        if (cell == null) return null;
        CellType t = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (t == CellType.NUMERIC) {
            double v = cell.getNumericCellValue();
            return v > 0 ? EXCEL_EPOCH.plusDays((long) v) : null;
        }
        String s = fmt.formatCellValue(cell, evaluator).trim();
        if (s.isEmpty() || s.equals("-") || s.equals("—")) return null;
        try {
            return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s, RU_DATE);
        } catch (Exception e) {
            throw new IllegalArgumentException("столбец " + CellReference.convertNumToColString(cell.getColumnIndex())
                    + ": не распознана дата «" + s + "»");
        }
    }

    private static Integer intOrNull(String s) {
        String v = s.replace(" ", "").replace(",", ".");
        if (v.isEmpty()) return null;
        try { return (int) Double.parseDouble(v); } catch (NumberFormatException e) { return null; }
    }

    private static String cut(String s, int max) {
        if (s == null || s.isEmpty()) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
