package com.emias.dashboard.service;

import com.emias.dashboard.entity.DsHospitalization;
import com.emias.dashboard.repository.DsHospitalizationRepository;
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
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Загрузчик «Госпитализировано в дневной стационар» —
 * файл «Путь пациента при лечении гепатита.xlsx» (выгрузка ЕМИАС).
 * Строка 1 — заголовки, строка 2 — итоговые суммы (пропускается), дальше — по строке на пациента.
 */
@Service
public class DsHospitalizationService {

    private static final Logger log = LoggerFactory.getLogger(DsHospitalizationService.class);

    private static final DateTimeFormatter RU_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /** Заголовки столбцов A..AF — как в исходной выгрузке. */
    static final String[] HEADERS = {
            "#", "ОГРН прикрепления", "ЛПУ прикрепления", "Дата рождения", "Номер МКАБ",
            "ОГРН", "ЛПУ", "Отделение", "Дата госпитализации", "number",
            "Дата заполнения шаблона", "Рекомендации", "Заключение", "Название препарата", "Комментарий",
            "Дата назначения", "Количество на курс",
            "Отделение 2 курс", "Дата госпитализации 2 курс", "Название препарата 2 курс",
            "Комментарий 2 курс", "Дата назначения 2 курс", "Количество на курс 2 курс",
            "Дата отмены назначения 2 курс",
            "Отделение 3 курс", "Дата госпитализации 3 курс", "Название препарата 3 курс",
            "Комментарий 3 курс", "Дата назначения 3 курс", "Количество на курс 3 курс",
            "Дата отмены назначения 3 курс",
            "Количество курсов"
    };

    // Индексы столбцов
    private static final int ATTACH_OGRN = 1, ATTACH_MO = 2, BIRTH = 3, MKAB = 4, OGRN = 5, MO = 6,
            DEPT1 = 7, HOSP1 = 8, TPL_NUM = 9, TPL_DATE = 10, RECOMM = 11, CONCL = 12,
            DRUG1 = 13, COMM1 = 14, PRESC1 = 15, QTY1 = 16,
            DEPT2 = 17, HOSP2 = 18, DRUG2 = 19, COMM2 = 20, PRESC2 = 21, QTY2 = 22, CANCEL2 = 23,
            DEPT3 = 24, HOSP3 = 25, DRUG3 = 26, COMM3 = 27, PRESC3 = 28, QTY3 = 29, CANCEL3 = 30,
            COURSES = 31;

    private final DsHospitalizationRepository repository;

    public DsHospitalizationService(DsHospitalizationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public int upload(MultipartFile file) throws IOException {
        log.info("=== Загрузка «Госпитализировано в ДС» '{}', размер: {} байт ===",
                file.getOriginalFilename(), file.getSize());

        long t0 = System.currentTimeMillis();
        List<DsHospitalization> records = parseFile(file);
        log.info("Разбор файла: {} мс", System.currentTimeMillis() - t0);

        // Файл — полный срез на дату выгрузки, поэтому заменяем всё целиком
        repository.deleteAllInBatch();
        repository.saveAll(records);

        log.info("=== Госпитализировано в ДС: загружено {} записей ===", records.size());
        return records.size();
    }

    public long count() {
        return repository.count();
    }

    public LocalDateTime lastUploadedAt() {
        return repository.lastUploadedAt();
    }

    /** Сводка по ЛПУ госпитализации для предпросмотра в админке. */
    public List<Map<String, Object>> getSummaryByMo() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : repository.summaryByMo()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("mo",          row[0]);
            m.put("patients",    row[1]);
            m.put("course1",     row[2]);
            m.put("course2",     row[3]);
            m.put("course3plus", row[4]);
            result.add(m);
        }
        return result;
    }

    // ── Парсинг ──────────────────────────────────────────────────────────────

    private List<DsHospitalization> parseFile(MultipartFile file) throws IOException {
        List<DsHospitalization> result = new ArrayList<>();

        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            // ОБЯЗАТЕЛЬНО: без evaluator формулы вернут пустую строку
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();

            validateHeader(sheet.getRow(0), fmt, evaluator);

            LocalDateTime now = LocalDateTime.now();
            List<String> errors = new ArrayList<>();
            int skipped = 0;

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) { skipped++; continue; }

                String mo       = str(row, MO, fmt, evaluator);
                String attachMo = str(row, ATTACH_MO, fmt, evaluator);

                // Строка 2 — итоговые суммы (в ЛПУ пусто или «0»), плюс пустые строки и «Итого»
                if (!hasLetters(mo) && !hasLetters(attachMo)) { skipped++; continue; }
                if (mo.toLowerCase().contains("итого") || attachMo.toLowerCase().contains("итого")) {
                    skipped++; continue;
                }

                try {
                    DsHospitalization r = new DsHospitalization();
                    r.setAttachOgrn(cut(str(row, ATTACH_OGRN, fmt, evaluator), 20));
                    r.setAttachMo(cut(attachMo, 500));
                    r.setBirthDate(date(row, BIRTH, fmt, evaluator));
                    r.setMkab(cut(str(row, MKAB, fmt, evaluator), 100));
                    r.setOgrn(cut(str(row, OGRN, fmt, evaluator), 20));
                    r.setMo(cut(mo, 500));
                    r.setTemplateNumber(cut(str(row, TPL_NUM, fmt, evaluator), 50));
                    r.setTemplateDate(date(row, TPL_DATE, fmt, evaluator));
                    r.setRecommendations(cut(str(row, RECOMM, fmt, evaluator), 10000));
                    r.setConclusion(cut(str(row, CONCL, fmt, evaluator), 10000));

                    r.setDept1(cut(str(row, DEPT1, fmt, evaluator), 500));
                    r.setHospDate1(date(row, HOSP1, fmt, evaluator));
                    r.setDrug1(cut(str(row, DRUG1, fmt, evaluator), 1000));
                    r.setComment1(cut(str(row, COMM1, fmt, evaluator), 2000));
                    r.setPrescribedDate1(date(row, PRESC1, fmt, evaluator));
                    r.setQty1(intVal(row, QTY1, fmt, evaluator));

                    r.setDept2(cut(str(row, DEPT2, fmt, evaluator), 500));
                    r.setHospDate2(date(row, HOSP2, fmt, evaluator));
                    r.setDrug2(cut(str(row, DRUG2, fmt, evaluator), 1000));
                    r.setComment2(cut(str(row, COMM2, fmt, evaluator), 2000));
                    r.setPrescribedDate2(date(row, PRESC2, fmt, evaluator));
                    r.setQty2(intVal(row, QTY2, fmt, evaluator));
                    r.setCancelDate2(date(row, CANCEL2, fmt, evaluator));

                    r.setDept3(cut(str(row, DEPT3, fmt, evaluator), 500));
                    r.setHospDate3(date(row, HOSP3, fmt, evaluator));
                    r.setDrug3(cut(str(row, DRUG3, fmt, evaluator), 1000));
                    r.setComment3(cut(str(row, COMM3, fmt, evaluator), 2000));
                    r.setPrescribedDate3(date(row, PRESC3, fmt, evaluator));
                    r.setQty3(intVal(row, QTY3, fmt, evaluator));
                    r.setCancelDate3(date(row, CANCEL3, fmt, evaluator));

                    r.setCourses(intVal(row, COURSES, fmt, evaluator));
                    r.setUploadedAt(now);
                    result.add(r);
                } catch (IllegalArgumentException e) {
                    if (errors.size() < 20) errors.add("Строка " + (i + 1) + ": " + e.getMessage());
                }
            }

            if (!errors.isEmpty()) throw new FileValidationException(errors);
            if (skipped > 0) log.info("Пропущено строк: {}", skipped);
        }

        if (result.isEmpty()) {
            throw new FileValidationException(List.of(
                    "Файл не содержит данных. Строка 1 — заголовок, строка 2 — итоги, со строки 3 — пациенты."));
        }
        return result;
    }

    private void validateHeader(Row header, DataFormatter fmt, FormulaEvaluator evaluator) {
        if (header == null) {
            throw new FileValidationException(List.of("Пустая первая строка — ожидаются заголовки столбцов."));
        }
        List<String> errors = new ArrayList<>();
        for (int col : new int[]{ATTACH_MO, OGRN, MO, DEPT1, HOSP1, DRUG1, COURSES}) {
            String actual = str(header, col, fmt, evaluator);
            if (!actual.equalsIgnoreCase(HEADERS[col])) {
                errors.add("Столбец " + colName(col) + ": ожидается «" + HEADERS[col]
                        + "», в файле «" + actual + "»");
            }
        }
        if (!errors.isEmpty()) {
            errors.add(0, "Неверная структура файла. Нужна выгрузка «Путь пациента при лечении гепатита» — скачайте шаблон.");
            throw new FileValidationException(errors);
        }
    }

    private static String colName(int col) {
        return org.apache.poi.ss.util.CellReference.convertNumToColString(col);
    }

    private String str(Row row, int col, DataFormatter fmt, FormulaEvaluator evaluator) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        return fmt.formatCellValue(cell, evaluator).trim();
    }

    /** Даты в выгрузке — текст «дд.мм.гггг»; 01.01.1900 означает «нет даты». */
    private LocalDate date(Row row, int col, DataFormatter fmt, FormulaEvaluator evaluator) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        LocalDate d;
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            d = cell.getLocalDateTimeCellValue().toLocalDate();
        } else {
            String s = fmt.formatCellValue(cell, evaluator).trim();
            if (s.isEmpty() || s.equals("-") || s.equals("—")) return null;
            try {
                d = LocalDate.parse(s, RU_DATE);
            } catch (Exception e) {
                throw new IllegalArgumentException("столбец " + colName(col)
                        + " («" + HEADERS[col] + "»): не распознана дата «" + s + "»");
            }
        }
        return d.getYear() <= 1900 ? null : d;
    }

    private Integer intVal(Row row, int col, DataFormatter fmt, FormulaEvaluator evaluator) {
        String s = str(row, col, fmt, evaluator)
                .replace(" ", "").replace(" ", "").replace(",", ".");
        if (s.isEmpty() || s.equals("—") || s.equals("-")) return null;
        try {
            return (int) Double.parseDouble(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("столбец " + colName(col)
                    + " («" + HEADERS[col] + "»): ожидается число, в файле «" + s + "»");
        }
    }

    private static boolean hasLetters(String s) {
        return s.codePoints().anyMatch(Character::isLetter);
    }

    private static String cut(String s, int max) {
        if (s == null || s.isEmpty()) return null;
        return s.length() <= max ? s : s.substring(0, max);
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

            CellStyle exStyle = wb.createCellStyle();
            exStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            exStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Sheet sh = wb.createSheet("Путь пациента при лечении гепат");

            Row h = sh.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell c = h.createCell(i);
                c.setCellValue(HEADERS[i]);
                c.setCellStyle(headerStyle);
                sh.setColumnWidth(i, i == 0 ? 1500 : 6000);
            }

            // Строка 2 — итоговые суммы, как в выгрузке ЕМИАС (при загрузке пропускается)
            Row totals = sh.createRow(1);
            totals.createCell(QTY1).setCellFormula("SUM(Q3:Q1048576)");
            totals.createCell(QTY2).setCellFormula("SUM(W3:W1048576)");
            totals.createCell(QTY3).setCellFormula("SUM(AD3:AD1048576)");
            totals.createCell(COURSES).setCellFormula("SUM(AF3:AF1048576)");

            String drug = "ГРОЗАВИР, табл. п.п.о., 100 мг + 50 мг, №28 - 7 шт. - уп. контурн. яч.";
            String comm = "Принимать 1 доз(а) ЛФ для приема внутрь 1 раз в день в течение 28 дней";
            String dept = "ДС Взрослое инфекционное отделение";
            String mo   = "[010101] ГБУЗ МОСКОВСКОЙ ОБЛАСТИ \"БАЛАШИХИНСКАЯ БОЛЬНИЦА\"";
            Object[] ex = {
                    1, "1035000701592", "ГБУЗ МОСКОВСКОЙ ОБЛАСТИ \"БАЛАШИХИНСКАЯ БОЛЬНИЦА\"", "01.01.1980", "000000",
                    "1035000701592", mo, dept, "17.06.2026", "2162812",
                    "09.04.2026", "-", "-", drug, comm,
                    "17.06.2026", 28,
                    dept, "15.07.2026", drug, comm, "15.07.2026", 28, "01.01.1900",
                    "", "", "", "", "", "", "",
                    2
            };
            Row exRow = sh.createRow(2);
            for (int i = 0; i < ex.length; i++) {
                Cell c = exRow.createCell(i);
                if (ex[i] instanceof Number n) c.setCellValue(n.doubleValue());
                else c.setCellValue((String) ex[i]);
                c.setCellStyle(exStyle);
            }

            wb.write(out);
            return out.toByteArray();
        }
    }
}
