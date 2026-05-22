package com.emias.dashboard.service;

import com.emias.dashboard.entity.FacilityMapping;
import com.emias.dashboard.repository.FacilityMappingRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Парсит Excel-файл с таблицей соответствий названий ЛПУ.
 *
 * Формат файла (строка 1 — заголовок, строки 2+ — данные):
 *   Столбец A: Название ЛПУ в скрининговых данных  → "[010101] ГБУЗ МО «БАЛАШИХИНСКАЯ БОЛЬНИЦА»"
 *   Столбец B: Название ЛПУ в файле планов         → "ГБУЗ МО «БАЛАШИХИНСКАЯ БОЛЬНИЦА»"
 */
@Service
public class FacilityMappingService {

    private static final Logger log = LoggerFactory.getLogger(FacilityMappingService.class);

    private final FacilityMappingRepository repository;

    public FacilityMappingService(FacilityMappingRepository repository) {
        this.repository = repository;
    }

    /**
     * Загружает файл соответствий. Все старые соответствия заменяются новыми.
     *
     * @return количество загруженных строк
     */
    @Transactional
    public int uploadMappings(MultipartFile file) throws IOException {
        log.info("=== Загрузка файла соответствий '{}', размер: {} байт ===",
                file.getOriginalFilename(), file.getSize());

        List<FacilityMapping> mappings = parseFile(file.getInputStream());

        long oldCount = repository.count();
        if (oldCount > 0) {
            log.info("Удаляю {} старых соответствий", oldCount);
            repository.deleteAll();
        }

        repository.saveAll(mappings);
        log.info("=== Соответствия загружены: {} строк ===", mappings.size());
        return mappings.size();
    }

    /**
     * Возвращает все соответствия, отсортированные по screeningName.
     */
    public List<FacilityMapping> getAllMappings() {
        return repository.findAllByOrderByScreeningNameAsc();
    }

    /**
     * Возвращает словарь: screeningName → planName.
     * Используется при сопоставлении данных скрининга с планами.
     */
    public Map<String, String> getMappingMap() {
        List<FacilityMapping> all = repository.findAllByOrderByScreeningNameAsc();
        Map<String, String> map = new LinkedHashMap<>();
        for (FacilityMapping m : all) {
            map.put(m.getScreeningName(), m.getPlanName());
        }
        return map;
    }

    /**
     * По названию из скрининговых данных возвращает соответствующее название из планов.
     * Если соответствие не найдено — возвращает исходное название.
     */
    public String resolvePlanName(String screeningName, Map<String, String> mappingMap) {
        if (screeningName == null || screeningName.isBlank()) return screeningName;
        return mappingMap.getOrDefault(screeningName.trim(), screeningName.trim());
    }

    // ── Парсинг ──────────────────────────────────────────────────────────────

    private List<FacilityMapping> parseFile(InputStream is) throws IOException {
        List<FacilityMapping> mappings = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();
            log.info("Всего строк в файле: {}", lastRow + 1);

            LocalDateTime now = LocalDateTime.now();
            int skipped = 0;

            // Определяем с какой строки начинаются данные:
            // если строка 0 выглядит как заголовок (нет кода вида [XXXXXX]) — пропускаем её,
            // иначе данные начинаются прямо с первой строки.
            int startRow = isHeaderRow(sheet.getRow(0)) ? 1 : 0;
            log.info("Данные начинаются с индекса {} (строка {} в Excel)", startRow, startRow + 1);

            for (int i = startRow; i <= lastRow; i++) {
                Row row = sheet.getRow(i);
                if (row == null) { skipped++; continue; }

                String screeningName = getStringCell(row, 0).trim();
                String planName      = getStringCell(row, 1).trim();

                if (screeningName.isBlank() && planName.isBlank()) {
                    skipped++;
                    continue;
                }

                if (screeningName.isBlank()) {
                    log.warn("Строка {}: пропущена — нет названия в столбце A", i + 1);
                    skipped++;
                    continue;
                }

                if (planName.isBlank()) {
                    log.warn("Строка {}: пропущена — нет названия в столбце B (скрининг: '{}')",
                            i + 1, screeningName);
                    skipped++;
                    continue;
                }

                FacilityMapping mapping = new FacilityMapping();
                mapping.setScreeningName(screeningName);
                mapping.setPlanName(planName);
                mapping.setUploadedAt(now);
                mappings.add(mapping);

                log.debug("Строка {}: '{}' → '{}'", i + 1, screeningName, planName);
            }

            if (skipped > 0) log.info("Пропущено строк: {}", skipped);
        }

        if (mappings.isEmpty()) {
            throw new FileValidationException(List.of(
                "Файл не содержит данных. " +
                "Убедитесь, что столбец A — название в скрининге, столбец B — название в планах, " +
                "данные начинаются со второй строки."));
        }

        return mappings;
    }

    /**
     * Строка считается заголовком, если в столбце A нет кода вида [XXXXXX]
     * и содержит типичные слова заголовка: "название", "наименование", "скрининг", "лпу".
     */
    private boolean isHeaderRow(Row row) {
        if (row == null) return false;
        String cellA = getStringCell(row, 0).trim().toLowerCase();
        if (cellA.isBlank()) return false;
        // Данные всегда начинаются с кода в скобках [XXXXXX] или с ГБУЗ
        if (cellA.startsWith("[") || cellA.startsWith("гбуз")) return false;
        // Иначе проверяем по ключевым словам заголовка
        return cellA.contains("название") || cellA.contains("наименование")
                || cellA.contains("скрининг") || cellA.contains("лпу")
                || cellA.contains("организ");
    }

    private String getStringCell(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                CellType cached = cell.getCachedFormulaResultType();
                if (cached == CellType.STRING)  yield cell.getStringCellValue();
                if (cached == CellType.NUMERIC) yield String.valueOf((long) cell.getNumericCellValue());
                yield "";
            }
            default -> "";
        };
    }
}
