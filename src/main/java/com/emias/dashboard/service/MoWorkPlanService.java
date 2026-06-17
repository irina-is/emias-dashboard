package com.emias.dashboard.service;

import com.emias.dashboard.entity.MoWorkPlan;
import com.emias.dashboard.repository.MoWorkPlanRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Service
public class MoWorkPlanService {

    private final MoWorkPlanRepository repo;

    public MoWorkPlanService(MoWorkPlanRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public int upload(MultipartFile file, YearMonth yearMonth) throws IOException {
        LocalDate reportMonth = yearMonth.atDay(1);
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();

            repo.deleteByReportMonth(reportMonth);

            List<MoWorkPlan> rows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String orgName = fmt.formatCellValue(row.getCell(0)).trim();
                if (orgName.isEmpty()) continue;

                rows.add(new MoWorkPlan(
                        orgName,
                        parseIntOrNull(fmt, row.getCell(1)),
                        parseIntOrNull(fmt, row.getCell(2)),
                        parseIntOrNull(fmt, row.getCell(3)),
                        parseIntOrNull(fmt, row.getCell(4)),
                        parseIntOrNull(fmt, row.getCell(5)),
                        reportMonth
                ));
            }
            repo.saveAll(rows);
            return rows.size();
        }
    }

    public List<MoWorkPlan> getByMonth(YearMonth yearMonth) {
        return repo.findByReportMonthOrderByOrgNameAsc(yearMonth.atDay(1));
    }

    public List<MoWorkPlan> getLatest() {
        List<LocalDate> months = repo.findDistinctMonths();
        if (months.isEmpty()) return List.of();
        return repo.findByReportMonthOrderByOrgNameAsc(months.get(0));
    }

    public LocalDate getLatestMonth() {
        List<LocalDate> months = repo.findDistinctMonths();
        return months.isEmpty() ? null : months.get(0);
    }

    public List<LocalDate> getAvailableMonths() {
        return repo.findDistinctMonths();
    }

    public byte[] generateTemplate() throws IOException {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Понедельный план");

            CellStyle headerStyle = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row header = sheet.createRow(0);
            String[] cols = {"Название МО", "Неделя 1", "Неделя 2", "Неделя 3", "Неделя 4", "Отработано"};
            for (int i = 0; i < cols.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, i == 0 ? 15000 : 4000);
            }

            // пример строки
            Row example = sheet.createRow(1);
            example.createCell(0).setCellValue("ГБУЗ МО \"Название больницы\"");
            example.createCell(1).setCellValue(10);
            example.createCell(2).setCellValue(10);
            example.createCell(3).setCellValue(10);
            example.createCell(4).setCellValue(0);
            example.createCell(5).setCellValue(5);

            wb.write(out);
            return out.toByteArray();
        }
    }

    private Integer parseIntOrNull(DataFormatter fmt, Cell cell) {
        if (cell == null) return null;
        String s = fmt.formatCellValue(cell).trim().replace(" ", "").replace(" ", "");
        if (s.isEmpty() || s.equals("—") || s.equals("-")) return null;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }
}
