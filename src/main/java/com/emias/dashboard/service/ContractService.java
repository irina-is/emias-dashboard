package com.emias.dashboard.service;

import com.emias.dashboard.entity.Contract;
import com.emias.dashboard.repository.ContractRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.ByteArrayOutputStream;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ContractService {

    private static final Logger log = LoggerFactory.getLogger(ContractService.class);

    private final ContractRepository repository;

    public ContractService(ContractRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public int upload(MultipartFile file) throws IOException {
        log.info("=== Загрузка файла контрактов '{}', размер: {} байт ===",
                file.getOriginalFilename(), file.getSize());

        List<Contract> rows = parseFile(file.getInputStream());

        repository.deleteAll();
        repository.saveAll(rows);

        log.info("=== Контракты загружены: {} строк ===", rows.size());
        return rows.size();
    }

    public List<Contract> getAll() {
        return repository.findAllByOrderByOrgNameAscDrugNameAsc();
    }

    public byte[] generateTemplate() throws IOException {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Контракты");

            CellStyle headerStyle = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Строка-заголовок: заполняем столбцы A–AA (0–26), нужные подписываем
            Row header = sheet.createRow(0);
            String[] labels = new String[27];
            labels[2]  = "Наименование ЛПУ";
            labels[3]  = "МНН";
            labels[21] = "№ ГК ЕИС";
            labels[22] = "Статус контракта";
            labels[23] = "Сумма контракта";
            labels[26] = "Сумма исполненного";
            for (int i = 0; i < labels.length; i++) {
                Cell cell = header.createCell(i);
                if (labels[i] != null) {
                    cell.setCellValue(labels[i]);
                    cell.setCellStyle(headerStyle);
                    sheet.setColumnWidth(i, i == 2 || i == 3 ? 14000 : 6000);
                }
            }

            CellStyle exampleStyle = wb.createCellStyle();
            exampleStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            exampleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row ex = sheet.createRow(1);
            ex.createCell(2).setCellValue("ГБУЗ МО \"НАЗВАНИЕ БОЛЬНИЦЫ\"");
            ex.createCell(3).setCellValue("Глекапревир + пибрентасвир");
            ex.createCell(21).setCellValue("№ 2500507141326000024");
            ex.createCell(22).setCellValue("Исполнен");
            ex.createCell(23).setCellValue(12279540);
            ex.createCell(26).setCellValue(12279540);
            for (int i = 0; i < 27; i++) {
                Cell c = ex.getCell(i);
                if (c != null) c.setCellStyle(exampleStyle);
            }

            wb.write(out);
            return out.toByteArray();
        }
    }

    public Map<String, Object> getStats() {
        List<Contract> all = repository.findAllByOrderByOrgNameAscDrugNameAsc();
        long total = all.size();
        long executed = all.stream().filter(c -> "исполнен".equals(c.getStatus())).count();
        long noOrders = all.stream().filter(c -> "без_заявок".equals(c.getStatus())).count();
        long totalAmt = all.stream().filter(c -> c.getTotalAmount() != null)
                .mapToLong(Contract::getTotalAmount).sum();
        long executedAmt = all.stream().filter(c -> c.getExecutedAmount() != null)
                .mapToLong(Contract::getExecutedAmount).sum();
        double pct = totalAmt > 0 ? (double) executedAmt / totalAmt * 100.0 : 0.0;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("executed", executed);
        stats.put("noOrders", noOrders);
        stats.put("totalAmount", totalAmt);
        stats.put("executedAmount", executedAmt);
        stats.put("pct", Math.round(pct * 10.0) / 10.0);
        return stats;
    }

    /**
     * Формат файла ДК_Гепатит_С_2026:
     *   Строка 1 — заголовок
     *   C(2)  — Наименование ЛПУ
     *   D(3)  — МНН (действующее вещество)
     *   V(21) — № ГК ЕИС
     *   W(22) — Статус контракта (Исполнен / Исполнение / …)
     *   X(23) — Сумма контракта
     *   AA(26)— Сумма исполненного
     */
    private List<Contract> parseFile(InputStream is) throws IOException {
        List<Contract> result = new ArrayList<>();
        try (Workbook wb = new XSSFWorkbook(is)) {
            Sheet sheet = wb.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();
            LocalDateTime now = LocalDateTime.now();

            for (int i = 1; i <= lastRow; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String orgName = str(row, 2);
                if (orgName.isBlank()) continue;
                String lower = orgName.toLowerCase();
                if (lower.startsWith("итого") || lower.startsWith("всего")) continue;

                String drugName = str(row, 3);
                // Пропускаем строки-продолжения МО без данных о препарате и контракте
                if (drugName.isBlank() && str(row, 21).isBlank()) continue;

                Contract c = new Contract();
                c.setOrgName(orgName.replaceAll("\\s+", " ").trim());
                c.setDrugName(drugName.replaceAll("\\s+", " ").trim());
                c.setContractNumber(str(row, 21));
                c.setContractStatus(str(row, 22));
                c.setTotalAmount(longVal(row, 23));
                c.setExecutedAmount(longVal(row, 26));
                c.setUploadedAt(now);
                result.add(c);

                log.debug("Строка {}: {} / {} / контракт {}", i + 1, orgName, drugName, c.getContractNumber());
            }
        }

        if (result.isEmpty()) {
            throw new FileValidationException(List.of(
                "Файл не содержит данных. Ожидается файл ДК_Гепатит_С_2026: " +
                "C — МО, D — МНН, V — № ГК ЕИС, W — Статус, X — Сумма, AA — Исполнено."));
        }
        return result;
    }

    private String str(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case FORMULA -> {
                CellType t = cell.getCachedFormulaResultType();
                if (t == CellType.STRING)  yield cell.getStringCellValue().trim();
                if (t == CellType.NUMERIC) yield String.valueOf((long) cell.getNumericCellValue());
                yield "";
            }
            default -> "";
        };
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
        } catch (Exception e) {
            return null;
        }
    }
}
