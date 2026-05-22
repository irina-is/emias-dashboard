package com.emias.dashboard.service;

import com.emias.dashboard.entity.FacilityPlan;
import com.emias.dashboard.repository.FacilityPlanRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Парсит Excel-файл с плановыми показателями скрининга по медицинским организациям.
 *
 * Фактический формат файла:
 *   Столбец 0:  № п/п (порядковый номер строки — игнорируется)
 *   Столбец 1:  Наименование ЛПУ
 *   Столбец 2:  План скрининг 25,35,45 (годовой)
 *   Столбец 3:  План скрининг все возраста (годовой)
 *   Столбец 4:  План скрининг общий (годовой)
 *   Столбец 5:  План скрининг 25,35,45 (месячный)
 *   Столбец 6:  План скрининг все возраста (месячный)
 *   Столбец 7:  План скрининг общий (месячный)
 *   Столбец 8:  План скрининг 25,35,45 (недельный)
 *   Столбец 9:  План скрининг все возраста (недельный)
 *   Столбец 10: План скрининг общий (недельный)
 *
 * Заголовок ищется автоматически — по наличию слова "наименование" или "лпу"
 * в строке. Данные начинаются со следующей строки после заголовка.
 */
@Service
public class FacilityPlanService {

    private static final Logger log = LoggerFactory.getLogger(FacilityPlanService.class);

    // Индекс столбца с названием организации
    private static final int COL_FACILITY = 1;
    // Индексы столбцов с плановыми данными (годовые, месячные, недельные)
    private static final int COL_ANNUAL_254545    = 2;
    private static final int COL_ANNUAL_ALL_AGES  = 3;
    private static final int COL_ANNUAL_TOTAL     = 4;
    private static final int COL_MONTHLY_254545   = 5;
    private static final int COL_MONTHLY_ALL_AGES = 6;
    private static final int COL_MONTHLY_TOTAL    = 7;
    private static final int COL_WEEKLY_254545    = 8;
    private static final int COL_WEEKLY_ALL_AGES  = 9;
    private static final int COL_WEEKLY_TOTAL     = 10;

    // Минимальное количество столбцов с планами (без недельных — если их нет)
    private static final int MIN_DATA_COLS = 8;

    private final FacilityPlanRepository repository;

    public FacilityPlanService(FacilityPlanRepository repository) {
        this.repository = repository;
    }

    /**
     * Загружает файл планов: очищает старые данные и сохраняет новые.
     *
     * @param file загруженный Excel (.xlsx)
     * @return количество сохранённых строк
     * @throws IOException             при ошибке чтения файла
     * @throws FileValidationException если структура файла неверная
     */
    @Transactional
    public int uploadPlans(MultipartFile file) throws IOException {
        log.info("=== Начало загрузки файла планов '{}', размер: {} байт ===",
                file.getOriginalFilename(), file.getSize());

        List<FacilityPlan> plans = parseFile(file.getInputStream());

        // Полная замена: удаляем все старые планы
        long oldCount = repository.count();
        if (oldCount > 0) {
            log.info("Удаляю {} старых записей планов", oldCount);
            repository.deleteAll();
        }

        repository.saveAll(plans);
        log.info("=== Файл планов загружен успешно: {} организаций ===", plans.size());
        return plans.size();
    }

    /**
     * Возвращает все планы, отсортированные по названию организации.
     */
    public List<FacilityPlan> getAllPlans() {
        return repository.findAllByOrderByFacilityNameAsc();
    }

    /**
     * Возвращает план по точному названию организации, или null если не найден.
     */
    public FacilityPlan getPlanByFacility(String facilityName) {
        return repository.findByFacilityName(facilityName).orElse(null);
    }

    /**
     * Возвращает сводку: сколько организаций загружено и когда.
     */
    public Map<String, Object> getSummary() {
        List<FacilityPlan> all = repository.findAllByOrderByFacilityNameAsc();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("count", all.size());
        if (!all.isEmpty()) {
            summary.put("uploadedAt", all.get(0).getUploadedAt().toString());
        }
        return summary;
    }

    // ── Парсинг ──────────────────────────────────────────────────────────────

    private List<FacilityPlan> parseFile(InputStream is) throws IOException {
        List<FacilityPlan> plans = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();
            log.info("Листов: {}, используем лист 0. Всего строк: {}",
                    workbook.getNumberOfSheets(), lastRow + 1);

            // Строка 0 (Excel-строка 1) — заголовок, данные начинаются с индекса 1
            validateHeader(sheet.getRow(0));

            LocalDateTime now = LocalDateTime.now();
            int skipped = 0;

            for (int i = 1; i <= lastRow; i++) {
                Row row = sheet.getRow(i);
                if (row == null) { skipped++; continue; }

                // Столбец B (индекс 1) — Наименование ЛПУ
                String facilityName = getStringCell(row, COL_FACILITY).trim();
                if (facilityName.isBlank()) {
                    log.debug("Строка {} пропущена — нет названия организации", i + 1);
                    skipped++;
                    continue;
                }

                FacilityPlan plan = new FacilityPlan();
                plan.setFacilityName(facilityName);
                plan.setUploadedAt(now);
                plan.setAnnualPlan254545(getLongCell(row, COL_ANNUAL_254545));
                plan.setAnnualPlanAllAges(getLongCell(row, COL_ANNUAL_ALL_AGES));
                plan.setAnnualPlanTotal(getLongCell(row, COL_ANNUAL_TOTAL));
                plan.setMonthlyPlan254545(getLongCell(row, COL_MONTHLY_254545));
                plan.setMonthlyPlanAllAges(getLongCell(row, COL_MONTHLY_ALL_AGES));
                plan.setMonthlyPlanTotal(getLongCell(row, COL_MONTHLY_TOTAL));
                plan.setWeeklyPlan254545(getLongCell(row, COL_WEEKLY_254545));
                plan.setWeeklyPlanAllAges(getLongCell(row, COL_WEEKLY_ALL_AGES));
                plan.setWeeklyPlanTotal(getLongCell(row, COL_WEEKLY_TOTAL));

                plans.add(plan);
                log.debug("Строка {}: '{}' — годовой общий: {}", i + 1,
                        facilityName, plan.getAnnualPlanTotal());
            }

            if (skipped > 0) {
                log.info("Пропущено пустых строк: {}", skipped);
            }
        }

        if (plans.isEmpty()) {
            throw new FileValidationException(List.of(
                "Файл не содержит строк с данными. " +
                "Проверьте, что данные начинаются со второй строки (первая — заголовок)."));
        }

        return plans;
    }

    /**
     * Проверяет, что строка 1 (индекс 0) содержит «Наименование ЛПУ» в столбце B (индекс 1).
     */
    private void validateHeader(Row header) {
        if (header == null) {
            throw new IllegalArgumentException("Файл пустой — нет строки заголовка");
        }
        String cell = getStringCell(header, COL_FACILITY).toLowerCase().trim();
        if (!cell.contains("наименование") && !cell.contains("лпу") && !cell.contains("учреждение")) {
            log.warn("Ячейка B1 не содержит ожидаемого заголовка, получено: '{}'. Продолжаем.", cell);
        } else {
            log.info("Заголовок подтверждён: B1 = '{}'", getStringCell(header, COL_FACILITY));
        }
    }

    /** Читает ячейку как строку. Возвращает "" если ячейка пустая или null. */
    private String getStringCell(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                    : String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                CellType cached = cell.getCachedFormulaResultType();
                if (cached == CellType.NUMERIC) yield String.valueOf((long) cell.getNumericCellValue());
                if (cached == CellType.STRING)  yield cell.getStringCellValue();
                yield "";
            }
            default -> "";
        };
    }

    /**
     * Читает ячейку как Long.
     * Возвращает null если ячейка пустая или содержит не-число.
     */
    private Long getLongCell(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> (long) cell.getNumericCellValue();
                case STRING  -> {
                    String s = cell.getStringCellValue().trim();
                    yield s.isEmpty() ? null : Long.parseLong(s.replace(" ", "").replace(",", ""));
                }
                case FORMULA -> {
                    CellType cached = cell.getCachedFormulaResultType();
                    if (cached == CellType.NUMERIC) yield (long) cell.getNumericCellValue();
                    yield null;
                }
                default -> null;
            };
        } catch (NumberFormatException e) {
            log.warn("Не удалось прочитать число в столбце {}: {}", col, e.getMessage());
            return null;
        }
    }
}
