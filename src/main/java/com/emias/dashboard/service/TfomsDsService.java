package com.emias.dashboard.service;

import com.emias.dashboard.entity.TfomsDsRecord;
import com.emias.dashboard.repository.TfomsDsRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class TfomsDsService {

    private static final Logger log = LoggerFactory.getLogger(TfomsDsService.class);

    // Excel serial date base: 1899-12-30
    private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);

    private final TfomsDsRepository repository;

    public TfomsDsService(TfomsDsRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public int upload(MultipartFile file) throws IOException {
        log.info("=== Загрузка ДС ТФОМС СВОД '{}', размер: {} байт ===",
                file.getOriginalFilename(), file.getSize());

        List<TfomsDsRecord> records = parseFile(file);

        repository.deleteAll();
        repository.saveAll(records);

        log.info("=== ДС ТФОМС: загружено {} записей ===", records.size());
        return records.size();
    }

    public long count() {
        return repository.count();
    }

    /** Сводка по МО для предпросмотра в админке */
    public List<Map<String, Object>> getSummaryByMo() {
        List<Object[]> raw = repository.summaryByMo();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : raw) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("moShort",      row[0]);
            m.put("moFull",       row[1]);
            m.put("totalRecords", row[2]);
            m.put("totalCost",    row[3]);
            result.add(m);
        }
        return result;
    }

    /** Список всех записей */
    public List<TfomsDsRecord> getAll() {
        return repository.findAllByOrderByMoShortAscLastNameAsc();
    }

    private static final Map<String, String> SCHEME_DISPLAY = new LinkedHashMap<>();
    static {
        SCHEME_DISPLAY.put("thc03",    "Мавирет");
        SCHEME_DISPLAY.put("thc16",    "ДакСоф");
        SCHEME_DISPLAY.put("thc09",    "Гразовир");
        SCHEME_DISPLAY.put("thc01",    "Велпатасвир+Соф");
        SCHEME_DISPLAY.put("st12.004", "Стационар");
        SCHEME_DISPLAY.put("ds12.005", "Другие");
    }

    private String resolveScheme(TfomsDsRecord r) {
        String thc = r.getThcCode();
        if (thc != null && !thc.isBlank()) {
            String d = SCHEME_DISPLAY.get(thc.trim().toLowerCase());
            if (d != null) return d;
        }
        String svc = r.getServiceCode();
        if (svc != null && !svc.isBlank()) {
            String d = SCHEME_DISPLAY.get(svc.trim().toLowerCase());
            if (d != null) return d;
        }
        return "—";
    }

    /**
     * Данные для дашборда: по каждой МО — кол-во уникальных пациентов
     * с разбивкой по курсам (1/2/3+) и общая стоимость.
     * Уникальность пациента: ФИО + дата рождения.
     */
    public Map<String, Object> getDashboardData() {
        List<TfomsDsRecord> all = repository.findAllByOrderByMoShortAscLastNameAsc();

        // Глобальная дедупликация для статистики шапки
        Map<String, Integer> globalPatients = new LinkedHashMap<>();
        // Сортировка по МО
        Map<String, List<TfomsDsRecord>> byMo = new LinkedHashMap<>();
        for (TfomsDsRecord r : all) {
            String mo = r.getMoShort() != null ? r.getMoShort() : "—";
            byMo.computeIfAbsent(mo, k -> new ArrayList<>()).add(r);
            String gKey = patientKey(r);
            globalPatients.merge(gKey, 1, Integer::sum);
        }

        // Глобальные статы
        long totalUnique = globalPatients.size();
        long globalCourse1 = globalPatients.values().stream().filter(v -> v == 1).count();
        long globalCourse2 = globalPatients.values().stream().filter(v -> v == 2).count();
        long globalCourse3 = globalPatients.values().stream().filter(v -> v >= 3).count();

        // Данные по МО
        List<Map<String, Object>> moRows = new ArrayList<>();
        for (Map.Entry<String, List<TfomsDsRecord>> e : byMo.entrySet()) {
            String mo = e.getKey();
            List<TfomsDsRecord> rows = e.getValue();

            // Дедупликация внутри МО
            Map<String, Integer> patients = new LinkedHashMap<>();
            long cost = 0;
            for (TfomsDsRecord r : rows) {
                patients.merge(patientKey(r), 1, Integer::sum);
                if (r.getCost() != null) cost += r.getCost();
            }

            long c1 = patients.values().stream().filter(v -> v == 1).count();
            long c2 = patients.values().stream().filter(v -> v == 2).count();
            long c3 = patients.values().stream().filter(v -> v >= 3).count();

            // moFull — первый попавшийся
            String moFull = rows.stream()
                    .map(TfomsDsRecord::getMoFull)
                    .filter(s -> s != null && !s.isBlank())
                    .findFirst().orElse(mo);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("moShort",      mo);
            m.put("moFull",       moFull);
            m.put("totalRecords", rows.size());
            m.put("unique",       patients.size());
            m.put("course1",      c1);
            m.put("course2",      c2);
            m.put("course3plus",  c3);
            m.put("totalCost",    cost);
            moRows.add(m);
        }

        // Сортируем по убыванию записей
        moRows.sort((a, b) -> Integer.compare((int)(Number)b.get("totalRecords"), (int)(Number)a.get("totalRecords")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalMo",      byMo.size());
        result.put("totalRecords", all.size());
        result.put("totalUnique",  totalUnique);
        result.put("globalCourse1", globalCourse1);
        result.put("globalCourse2", globalCourse2);
        result.put("globalCourse3", globalCourse3);
        result.put("rows",         moRows);
        return result;
    }

    /** Динамика по месяцам: кол-во записей по дате начала */
    public List<Map<String, Object>> getDynamics() {
        List<TfomsDsRecord> all = repository.findAllByOrderByMoShortAscLastNameAsc();
        Map<String, Long> byMonth = new TreeMap<>();
        long total = 0;
        for (TfomsDsRecord r : all) {
            if (r.getDateStart() == null) continue;
            String key = r.getDateStart().getYear() + "-" + String.format("%02d", r.getDateStart().getMonthValue());
            byMonth.merge(key, 1L, Long::sum);
            total++;
        }
        long finalTotal = total;
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, Long> e : byMonth.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("month", formatMonth(e.getKey()));
            m.put("monthKey", e.getKey());
            m.put("count", e.getValue());
            m.put("pct", finalTotal > 0 ? Math.round(e.getValue() * 1000.0 / finalTotal) / 10.0 : 0);
            result.add(m);
        }
        return result;
    }

    private String formatMonth(String key) {
        String[] months = {"Январь","Февраль","Март","Апрель","Май","Июнь",
                           "Июль","Август","Сентябрь","Октябрь","Ноябрь","Декабрь"};
        try {
            String[] parts = key.split("-");
            int m = Integer.parseInt(parts[1]);
            return months[m - 1] + " " + parts[0];
        } catch (Exception e) { return key; }
    }

    /** Схемы лечения: группировка по коду схемы */
    public List<Map<String, Object>> getSchemes() {
        List<TfomsDsRecord> all = repository.findAllByOrderByMoShortAscLastNameAsc();
        Map<String, Long> counts = new LinkedHashMap<>();
        Map<String, String> codeToDisplay = new LinkedHashMap<>();
        long total = all.size();

        for (TfomsDsRecord r : all) {
            String display = resolveScheme(r);
            counts.merge(display, 1L, Long::sum);
            codeToDisplay.putIfAbsent(display, resolveSchemeCode(r));
        }

        long finalTotal = total;
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("code",    codeToDisplay.getOrDefault(e.getKey(), ""));
                    m.put("display", e.getKey());
                    m.put("count",   e.getValue());
                    m.put("pct",     finalTotal > 0 ? Math.round(e.getValue() * 1000.0 / finalTotal) / 10.0 : 0);
                    return m;
                })
                .collect(Collectors.toList());
    }

    private String resolveSchemeCode(TfomsDsRecord r) {
        String thc = r.getThcCode();
        if (thc != null && !thc.isBlank()) return thc.trim().toLowerCase();
        String svc = r.getServiceCode();
        if (svc != null && !svc.isBlank()) return svc.trim().toLowerCase();
        return "";
    }

    /**
     * Список пациентов по МО с кол-вом курсов, датой начала и схемой.
     * Персональные данные (имя, отчество, дата рождения) не передаются.
     */
    public List<Map<String, Object>> getPatientsByMo(String moShort) {
        List<TfomsDsRecord> records = repository.findAllByOrderByMoShortAscLastNameAsc()
                .stream()
                .filter(r -> moShort.equalsIgnoreCase(r.getMoShort()))
                .toList();

        Map<String, Integer> patients = new LinkedHashMap<>();
        Map<String, String> keyToLastName = new LinkedHashMap<>();
        Map<String, String> keyToDateStart = new LinkedHashMap<>();
        Map<String, String> keyToScheme = new LinkedHashMap<>();

        for (TfomsDsRecord r : records) {
            String key = patientKey(r);
            patients.merge(key, 1, Integer::sum);
            keyToLastName.putIfAbsent(key, r.getLastName() != null ? r.getLastName() : "—");
            if (!keyToDateStart.containsKey(key) && r.getDateStart() != null) {
                keyToDateStart.put(key, r.getDateStart().toString());
            }
            if (!keyToScheme.containsKey(key)) {
                String s = resolveScheme(r);
                if (!"—".equals(s)) keyToScheme.put(key, s);
            }
        }

        return patients.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                    .thenComparing(e -> keyToLastName.getOrDefault(e.getKey(), "")))
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("lastName",  keyToLastName.get(e.getKey()));
                    m.put("courses",   e.getValue());
                    m.put("dateStart", keyToDateStart.getOrDefault(e.getKey(), ""));
                    m.put("scheme",    keyToScheme.getOrDefault(e.getKey(), "—"));
                    return m;
                })
                .collect(Collectors.toList());
    }

    private String patientKey(TfomsDsRecord r) {
        String ln = r.getLastName()   != null ? r.getLastName().trim().toUpperCase()   : "";
        String fn = r.getFirstName()  != null ? r.getFirstName().trim().toUpperCase()  : "";
        String mn = r.getMiddleName() != null ? r.getMiddleName().trim().toUpperCase() : "";
        String bd = r.getBirthDate()  != null ? r.getBirthDate().toString()            : "";
        return ln + "|" + fn + "|" + mn + "|" + bd;
    }

    public byte[] generateTemplate() throws IOException {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle headerStyle = wb.createCellStyle();
            Font boldFont = wb.createFont();
            boldFont.setBold(true);
            headerStyle.setFont(boldFont);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle exStyle = wb.createCellStyle();
            exStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            exStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // ─── Лист 1: Сводная ───
            Sheet sv = wb.createSheet("Сводная");
            String[] svCols = {"МО", "Всего записей", "Уникальных пациентов",
                    "Прошли 1 курс", "Прошли 2 курса", "Прошли 3+ курса", "Стоимость (руб.)"};
            Row svH = sv.createRow(0);
            for (int i = 0; i < svCols.length; i++) {
                Cell c = svH.createCell(i);
                c.setCellValue(svCols[i]);
                c.setCellStyle(headerStyle);
                sv.setColumnWidth(i, i == 0 ? 8000 : 5500);
            }
            Row svEx = sv.createRow(1);
            Object[] svData = {"Балашиха", 17, 17, 17, 0, 0, 803258};
            for (int i = 0; i < svData.length; i++) {
                Cell c = svEx.createCell(i);
                if (svData[i] instanceof String) c.setCellValue((String) svData[i]);
                else c.setCellValue(((Number) svData[i]).doubleValue());
                c.setCellStyle(exStyle);
            }

            // ─── Лист 2: Все МО ───
            Sheet all = wb.createSheet("Все МО");
            String[] allCols = {"МО (краткое)", "Наименование МО", "Фамилия", "Имя", "Отчество",
                    "Дата рождения", "Код диагноза", "Наименование услуги/КСГ", "Код услуги",
                    "DKK1 (thc)", "Схема лечения", "Дата начала", "Дата окончания",
                    "Стоимость (руб.)", "МО прикрепления"};
            Row allH = all.createRow(0);
            for (int i = 0; i < allCols.length; i++) {
                Cell c = allH.createCell(i);
                c.setCellValue(allCols[i]);
                c.setCellStyle(headerStyle);
                int w = (i == 1 || i == 7 || i == 10 || i == 14) ? 14000 : 5500;
                all.setColumnWidth(i, w);
            }
            Row allEx = all.createRow(1);
            String[] allData = {"Балашиха",
                    "ГБУЗ МО \"БАЛАШИХИНСКАЯ БОЛЬНИЦА\"",
                    "ИВАНОВ", "ИВАН", "ИВАНОВИЧ",
                    "01.01.1980", "B18.2",
                    "ЛЕЧЕНИЕ ХРОНИЧЕСКОГО ВИРУСНОГО ГЕПАТИТА C (УРОВЕНЬ 1)",
                    "ds12.022", "thc09",
                    "[ГРАЗОПРЕВИР + ЭЛБАСВИР] ТАБЛЕТКИ 100 МГ + 50 МГ",
                    "01.04.2026", "28.04.2026", "51806",
                    "ГБУЗ МО \"БАЛАШИХИНСКАЯ БОЛЬНИЦА\""};
            for (int i = 0; i < allData.length; i++) {
                Cell c = allEx.createCell(i);
                c.setCellValue(allData[i]);
                c.setCellStyle(exStyle);
            }

            wb.write(out);
            return out.toByteArray();
        }
    }

    // ── Парсинг ──────────────────────────────────────────────────────────────

    private List<TfomsDsRecord> parseFile(MultipartFile file) throws IOException {
        List<TfomsDsRecord> result = new ArrayList<>();

        try (Workbook wb = new XSSFWorkbook(file.getInputStream())) {
            // Читаем лист «Все МО» (индекс 1, или ищем по имени)
            Sheet sheet = findSheet(wb, "Все МО", "все мо", "все_мо");
            if (sheet == null) {
                throw new FileValidationException(List.of(
                    "Не найден лист «Все МО». Убедитесь, что файл содержит листы «Сводная» и «Все МО»."));
            }

            int lastRow = sheet.getLastRowNum();
            LocalDateTime now = LocalDateTime.now();
            int skipped = 0;

            for (int i = 1; i <= lastRow; i++) {
                Row row = sheet.getRow(i);
                if (row == null) { skipped++; continue; }

                String moShort = str(row, 0);
                if (moShort.isBlank()) { skipped++; continue; }

                TfomsDsRecord r = new TfomsDsRecord();
                r.setMoShort(moShort.trim());
                r.setMoFull(str(row, 1));
                r.setLastName(str(row, 2));
                r.setFirstName(str(row, 3));
                r.setMiddleName(str(row, 4));
                r.setBirthDate(dateVal(row, 5));
                r.setDiagnosisCode(str(row, 6));
                r.setServiceName(str(row, 7));
                r.setServiceCode(str(row, 8));
                r.setThcCode(str(row, 9));
                r.setTreatmentScheme(str(row, 10));
                r.setDateStart(dateVal(row, 11));
                r.setDateEnd(dateVal(row, 12));
                r.setCost(longVal(row, 13));
                r.setAttachmentMo(str(row, 14));
                r.setUploadedAt(now);
                result.add(r);
            }

            if (skipped > 0) log.info("Пропущено строк: {}", skipped);
        }

        if (result.isEmpty()) {
            throw new FileValidationException(List.of(
                "Файл не содержит данных на листе «Все МО». " +
                "Строка 1 — заголовок, со строки 2 — данные."));
        }
        return result;
    }

    private Sheet findSheet(Workbook wb, String... names) {
        for (String name : names) {
            Sheet s = wb.getSheet(name);
            if (s != null) return s;
        }
        // fallback: второй лист
        if (wb.getNumberOfSheets() > 1) return wb.getSheetAt(1);
        return null;
    }

    private String str(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                double d = cell.getNumericCellValue();
                yield d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
            }
            case FORMULA -> {
                CellType t = cell.getCachedFormulaResultType();
                if (t == CellType.STRING)  yield cell.getStringCellValue().trim();
                if (t == CellType.NUMERIC) yield String.valueOf((long) cell.getNumericCellValue());
                yield "";
            }
            default -> "";
        };
    }

    private LocalDate dateVal(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC || cell.getCellType() == CellType.FORMULA) {
                double v = cell.getNumericCellValue();
                if (v > 0) return EXCEL_EPOCH.plusDays((long) v);
            }
        } catch (Exception e) {
            log.warn("Не удалось распознать дату в строке {}, столбец {}", row.getRowNum(), col);
        }
        return null;
    }

    private Long longVal(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) return (long) cell.getNumericCellValue();
            if (cell.getCellType() == CellType.FORMULA &&
                cell.getCachedFormulaResultType() == CellType.NUMERIC)
                return (long) cell.getNumericCellValue();
            String s = str(row, col).replaceAll("[^\\d]", "");
            return s.isBlank() ? null : Long.parseLong(s);
        } catch (Exception e) { return null; }
    }
}
