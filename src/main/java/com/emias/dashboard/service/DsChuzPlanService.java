package com.emias.dashboard.service;

import com.emias.dashboard.entity.DsChuzPlan;
import com.emias.dashboard.repository.DsChuzPlanRepository;
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
 * Загрузчик «Стационарная помощь: план 2026 и ЧУЗ» — файл «дс_с чуз.xlsx».
 * Строки 1–2 — двухуровневая шапка (№ | МО | Стационарная помощь → План 2026, ЧУЗ (Гармония)),
 * со строки 3 — по строке на МО.
 */
@Service
public class DsChuzPlanService {

    private static final Logger log = LoggerFactory.getLogger(DsChuzPlanService.class);

    private static final int NUM = 0, MO = 1, PLAN = 2, CHUZ = 3;
    private static final int FIRST_DATA_ROW = 2;

    private final DsChuzPlanRepository repository;

    public DsChuzPlanService(DsChuzPlanRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public int upload(MultipartFile file) throws IOException {
        log.info("=== Загрузка «План 2026 / ЧУЗ» '{}', размер: {} байт ===",
                file.getOriginalFilename(), file.getSize());

        List<DsChuzPlan> records = parseFile(file);

        // Файл — полный срез, заменяем всё целиком
        repository.deleteAllInBatch();
        repository.saveAll(records);

        log.info("=== План 2026 / ЧУЗ: загружено {} МО ===", records.size());
        return records.size();
    }

    public LocalDateTime lastUploadedAt() {
        return repository.lastUploadedAt();
    }

    public List<Map<String, Object>> getAll() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (DsChuzPlan r : repository.findAllByOrderByMoAsc()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("num",   r.getNum());
            m.put("mo",    r.getMo());
            m.put("plan",  r.getPlan2026());
            m.put("chuz",  r.getChuzTreated());
            result.add(m);
        }
        return result;
    }

    // ── Парсинг ──────────────────────────────────────────────────────────────

    private List<DsChuzPlan> parseFile(MultipartFile file) throws IOException {
        List<DsChuzPlan> result = new ArrayList<>();

        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            // ОБЯЗАТЕЛЬНО: без evaluator формулы вернут пустую строку
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();

            validateHeader(sheet, fmt, evaluator);

            LocalDateTime now = LocalDateTime.now();
            List<String> errors = new ArrayList<>();
            Set<String> seen = new HashSet<>();

            for (int i = FIRST_DATA_ROW; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String mo = str(row, MO, fmt, evaluator);
                // Пропускаем пустые строки и строки «Итого» (пользователи добавляют их в Excel)
                if (mo.isEmpty() || mo.toLowerCase().contains("итого")) continue;

                if (!seen.add(mo.toLowerCase())) {
                    errors.add("Строка " + (i + 1) + ": МО «" + mo + "» встречается повторно");
                    continue;
                }

                try {
                    DsChuzPlan r = new DsChuzPlan();
                    r.setNum(intVal(row, NUM, fmt, evaluator));
                    r.setMo(mo.length() > 300 ? mo.substring(0, 300) : mo);
                    r.setPlan2026(intVal(row, PLAN, fmt, evaluator));
                    r.setChuzTreated(intVal(row, CHUZ, fmt, evaluator));
                    r.setUploadedAt(now);
                    result.add(r);
                } catch (IllegalArgumentException e) {
                    errors.add("Строка " + (i + 1) + ": " + e.getMessage());
                }
            }

            if (!errors.isEmpty()) throw new FileValidationException(errors);
        }

        if (result.isEmpty()) {
            throw new FileValidationException(List.of(
                    "Файл не содержит данных. Строки 1–2 — шапка, со строки 3 — МО."));
        }
        return result;
    }

    private void validateHeader(Sheet sheet, DataFormatter fmt, FormulaEvaluator evaluator) {
        Row r1 = sheet.getRow(0), r2 = sheet.getRow(1);
        String mo   = r1 == null ? "" : str(r1, MO, fmt, evaluator);
        String plan = r2 == null ? "" : str(r2, PLAN, fmt, evaluator);
        String chuz = r2 == null ? "" : str(r2, CHUZ, fmt, evaluator);

        List<String> errors = new ArrayList<>();
        if (!mo.equalsIgnoreCase("МО"))
            errors.add("Ячейка B1: ожидается «МО», в файле «" + mo + "»");
        if (!plan.toLowerCase().startsWith("план"))
            errors.add("Ячейка C2: ожидается «План 2026», в файле «" + plan + "»");
        if (!chuz.toLowerCase().startsWith("чуз"))
            errors.add("Ячейка D2: ожидается «ЧУЗ (Гармония)», в файле «" + chuz + "»");
        if (!errors.isEmpty()) {
            errors.add(0, "Неверная структура файла. Нужна таблица «№ | МО | План 2026 | ЧУЗ» — скачайте шаблон.");
            throw new FileValidationException(errors);
        }
    }

    private String str(Row row, int col, DataFormatter fmt, FormulaEvaluator evaluator) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        return fmt.formatCellValue(cell, evaluator).trim();
    }

    private Integer intVal(Row row, int col, DataFormatter fmt, FormulaEvaluator evaluator) {
        String s = str(row, col, fmt, evaluator)
                .replace(" ", "").replace(" ", "").replace(",", ".");
        if (s.isEmpty() || s.equals("—") || s.equals("-")) return null;
        try {
            return (int) Double.parseDouble(s);
        } catch (NumberFormatException e) {
            String colName = org.apache.poi.ss.util.CellReference.convertNumToColString(col);
            throw new IllegalArgumentException("столбец " + colName + ": ожидается число, в файле «" + s + "»");
        }
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
            Row h1 = sh.createRow(0), h2 = sh.createRow(1);
            String[] top = {"№", "МО", "Стационарная помощь", ""};
            String[] sub = {"", "", "План 2026", "ЧУЗ (Гармония)"};
            for (int i = 0; i < 4; i++) {
                Cell c1 = h1.createCell(i); c1.setCellValue(top[i]); c1.setCellStyle(headerStyle);
                Cell c2 = h2.createCell(i); c2.setCellValue(sub[i]); c2.setCellStyle(headerStyle);
            }
            sh.addMergedRegion(new CellRangeAddress(0, 1, 0, 0));
            sh.addMergedRegion(new CellRangeAddress(0, 1, 1, 1));
            sh.addMergedRegion(new CellRangeAddress(0, 0, 2, 3));
            sh.setColumnWidth(0, 1800);
            sh.setColumnWidth(1, 7000);
            sh.setColumnWidth(2, 4500);
            sh.setColumnWidth(3, 4500);

            Object[][] ex = {{6, "Балашиха", 319, 44}, {35, "Видное", 76, 14}};
            for (int r = 0; r < ex.length; r++) {
                Row row = sh.createRow(FIRST_DATA_ROW + r);
                for (int i = 0; i < 4; i++) {
                    Cell c = row.createCell(i);
                    if (ex[r][i] instanceof Number n) c.setCellValue(n.doubleValue());
                    else c.setCellValue((String) ex[r][i]);
                    c.setCellStyle(exStyle);
                }
            }

            wb.write(out);
            return out.toByteArray();
        }
    }
}
