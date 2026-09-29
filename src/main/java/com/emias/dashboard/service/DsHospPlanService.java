package com.emias.dashboard.service;

import com.emias.dashboard.entity.DsHospPlan;
import com.emias.dashboard.repository.DsHospPlanRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Загрузчик «План госпитализации по ЛПУ» — файл «стационар по госпитализации.xlsx».
 * Столбец A — МО (город больницы или «ЧУЗ»), столбец B — План 2026.
 * Шапка — строки 1–2, строка «ИТОГО ГБУЗ» и прочие столбцы игнорируются.
 */
@Service
public class DsHospPlanService {

    private static final Logger log = LoggerFactory.getLogger(DsHospPlanService.class);

    private static final int MO = 0, PLAN = 1;

    private final DsHospPlanRepository repository;

    public DsHospPlanService(DsHospPlanRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public int upload(MultipartFile file) throws IOException {
        log.info("=== Загрузка «План госпитализации по ЛПУ» '{}', размер: {} байт ===",
                file.getOriginalFilename(), file.getSize());

        List<DsHospPlan> records = parseFile(file);

        // Файл — полный срез, заменяем всё целиком
        repository.deleteAllInBatch();
        repository.saveAll(records);

        log.info("=== План госпитализации по ЛПУ: загружено {} строк ===", records.size());
        return records.size();
    }

    public LocalDateTime lastUploadedAt() {
        return repository.lastUploadedAt();
    }

    public List<Map<String, Object>> getAll() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (DsHospPlan r : repository.findAllByOrderBySortOrderAsc()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("mo",   r.getMo());
            m.put("plan", r.getPlan2026());
            m.put("chuz", r.isChuz());
            result.add(m);
        }
        return result;
    }

    // ── Парсинг ──────────────────────────────────────────────────────────────

    private List<DsHospPlan> parseFile(MultipartFile file) throws IOException {
        List<DsHospPlan> result = new ArrayList<>();

        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            // ОБЯЗАТЕЛЬНО: без evaluator формулы вернут пустую строку
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();

            Row header = sheet.getRow(0);
            String hMo   = header == null ? "" : str(header, MO, fmt, evaluator);
            String hPlan = header == null ? "" : str(header, PLAN, fmt, evaluator);
            if (!hMo.equalsIgnoreCase("МО") || !hPlan.toLowerCase().startsWith("план")) {
                throw new FileValidationException(List.of(
                        "Неверная структура файла. Нужна таблица «МО | План 2026» — скачайте шаблон.",
                        "Ячейка A1: ожидается «МО», в файле «" + hMo + "»",
                        "Ячейка B1: ожидается «План 2026», в файле «" + hPlan + "»"));
            }

            LocalDateTime now = LocalDateTime.now();
            List<String> errors = new ArrayList<>();
            Set<String> seen = new HashSet<>();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String mo   = str(row, MO, fmt, evaluator);
                String plan = str(row, PLAN, fmt, evaluator)
                        .replace(" ", "").replace(" ", "").replace(",", ".");
                // Вторая строка шапки, пустые строки и «ИТОГО ГБУЗ»
                if (mo.isEmpty() || mo.toLowerCase().contains("итого")) continue;

                if (plan.isEmpty()) {
                    errors.add("Строка " + (i + 1) + ": у МО «" + mo + "» не заполнен план");
                    continue;
                }
                int planVal;
                try {
                    planVal = (int) Double.parseDouble(plan);
                } catch (NumberFormatException e) {
                    errors.add("Строка " + (i + 1) + ": план должен быть числом, в файле «" + plan + "»");
                    continue;
                }
                if (!seen.add(mo.toLowerCase())) {
                    errors.add("Строка " + (i + 1) + ": МО «" + mo + "» встречается повторно");
                    continue;
                }

                DsHospPlan r = new DsHospPlan();
                r.setSortOrder(result.size() + 1);
                r.setMo(mo.length() > 300 ? mo.substring(0, 300) : mo);
                r.setPlan2026(planVal);
                r.setUploadedAt(now);
                result.add(r);
            }

            if (!errors.isEmpty()) throw new FileValidationException(errors);
        }

        if (result.isEmpty()) {
            throw new FileValidationException(List.of("Файл не содержит данных: нет строк с МО и планом."));
        }
        return result;
    }

    private String str(Row row, int col, DataFormatter fmt, FormulaEvaluator evaluator) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        return fmt.formatCellValue(cell, evaluator).trim();
    }

    // ── Шаблон ───────────────────────────────────────────────────────────────

    public byte[] generateTemplate() throws IOException {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle headerStyle = wb.createCellStyle();
            Font boldFont = wb.createFont();
            boldFont.setBold(true);
            headerStyle.setFont(boldFont);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            CellStyle exStyle = wb.createCellStyle();
            exStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            exStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Sheet sh = wb.createSheet("Лист1");
            Row h1 = sh.createRow(0);
            sh.createRow(1);
            String[] cols = {"МО", "План 2026"};
            for (int i = 0; i < cols.length; i++) {
                Cell c = h1.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(headerStyle);
                sh.getRow(1).createCell(i).setCellStyle(headerStyle);
                sh.addMergedRegion(new CellRangeAddress(0, 1, i, i));
            }
            sh.setColumnWidth(0, 7500);
            sh.setColumnWidth(1, 4500);

            Object[][] ex = {{"ЧУЗ", 942}, {"ДУБНА", 1122}, {"МОНИКИ", 285}};
            for (int r = 0; r < ex.length; r++) {
                Row row = sh.createRow(2 + r);
                Cell a = row.createCell(0); a.setCellValue((String) ex[r][0]); a.setCellStyle(exStyle);
                Cell b = row.createCell(1); b.setCellValue(((Number) ex[r][1]).doubleValue()); b.setCellStyle(exStyle);
            }

            wb.write(out);
            return out.toByteArray();
        }
    }
}
