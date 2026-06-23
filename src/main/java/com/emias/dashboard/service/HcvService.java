package com.emias.dashboard.service;

import com.emias.dashboard.entity.HcvPlan;
import com.emias.dashboard.entity.HcvProgress;
import com.emias.dashboard.entity.HcvWeeklyPlanRow;
import com.emias.dashboard.model.HcvOrgRow;
import com.emias.dashboard.model.HcvProjectData;
import com.emias.dashboard.repository.HcvPlanRepository;
import com.emias.dashboard.repository.HcvProgressRepository;
import com.emias.dashboard.repository.HcvWeeklyPlanRepository;
import jakarta.annotation.PostConstruct;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.*;

@Service
public class HcvService {

    // Мета-данные по проектам
    private static final int[]    PROJECT_IDS     = {1, 2, 3};
    private static final String[] PROJECT_TITLES  = {
            "Дневной стационар",
            "Амбулаторная помощь",
            "ПЦР-тестирование"
    };
    // Целевые значения на 2026 год согласно доп. соглашению № 056-2024-Д50055-1/2:
    // Проект 1 (дневной стационар): 4 105 чел. (п. 1.5)
    // Проект 2 (амбулаторное лечение): 1 416 чел. (п. 1.4)
    // Проект 3 (УВО / излечены): 5 163 чел. (п. 1.6)
    private static final int[]    PROJECT_TARGETS  = {4105, 1416, 5163};
    private static final int[]    PROJECT_LEADS    = {411, 142, 99};
    private static final String[] PROJECT_DEADLINES = {
            "01.08.2026", "01.08.2026", "31.12.2026"
    };

    // Планы по МО для проекта 1 (Лечение ГС)
    private static final Object[][] PLANS_P1 = {
        {"ГБУЗ МО \"Одинцовская областная больница\"",     214, 21},
        {"ГБУЗ МО \"Наро-Фоминская больница\"",           97,  10},
        {"ГБУЗ МО \"Коломенская больница\"",               113, 11},
        {"ГБУЗ МО \"Щелковская больница\"",                186, 19},
        {"ГБУЗ МО \"Клинская больница\"",                  62,  6},
        {"ГБУЗ МО \"Пушкинская клиническая больница им. проф. Розанова В.Н.\"", 189, 19},
        {"ГБУЗ МО \"Волоколамская больница\"",             22,  2},
        {"ГБУЗ МО \"Ногинская больница\"",                 134, 13},
        {"ГБУЗ МО \"Реутовская клиническая больница\"",    41,  4},
        {"ГБУЗ МО \"Красногорская больница\"",             79,  8},
        {"ГБУЗ МО \"Балашихинская больница\"",             319, 32},
        {"ГБУЗ МО \"Подольская ОКБ\"",                    292, 29},
        {"ГБУЗ МО \"Сергиево-Посадская больница\"",       96,  10},
        {"ГБУЗ МО \"Серпуховская больница\"",              85,  9},
        {"ГБУЗ МО \"Лобненская больница\"",                56,  6},
        {"ГБУЗ МО \"Луховицкая больница\"",                29,  3},
        {"ГБУЗ МО \"Дубненская больница\"",                15,  2},
        {"ГБУЗ МО \"Электростальская больница\"",          83,  8},
        {"ГБУЗ МО \"Королевская больница\"",               135, 14},
        {"ГБУЗ МО \"Котельниковская поликлиника\"",        32,  3},
        {"ГБУЗ МО \"Чеховская больница\"",                 48,  5},
        {"ГБУЗ МО \"Домодедовская больница\"",             113, 11},
        {"ГБУЗ МО \"Рузская больница\"",                   30,  3},
        {"ГБУЗ МО \"Видновская клиническая больница\"",    76,  8},
        {"ГБУЗ МО \"Шатурская больница\"",                 46,  5},
        {"ГБУЗ МО \"Люберецкая областная больница\"",      290, 29},
        {"ГБУЗ МО \"Химкинская клиническая больница\"",    147, 15},
        {"ГБУЗ МО \"Воскресенская больница\"",             126, 13},
        {"ГБУЗ МО \"Егорьевская больница\"",               30,  3},
        {"ГБУЗ МО \"Жуковская ОКБ\"",                      62,  6},
        {"ГБУЗ МО \"Истринская клиническая больница\"",    74,  7},
        {"ГБУЗ МО \"Краснознаменская поликлиника\"",       13,  1},
        {"ГБУЗ МО \"Лотошинская больница\"",               6,   1},
        {"ГБУЗ МО \"Можайская больница\"",                 17,  2},
        {"ГБУЗ МО \"Орехово-Зуевская больница\"",         99,  10},
        {"ГБУЗ МО \"Раменская больница\"",                 125, 13},
        {"ГБУЗ МО \"Серебряно-Прудская больница\"",        12,  1},
        {"ГБУЗ МО МОНИКИ",                                 0,   0},
        {"ГБУЗ МО \"Дмитровская больница\"",               95,  10},
        {"ГБУЗ МО \"Каширская больница\"",                 22,  2},
        {"ГБУЗ МО \"Зарайская больница\"",                 11,  1},
        {"ГБУЗ МО \"Павлово-Посадская больница\"",         60,  6},
        {"ГБУЗ МО \"Солнечногорская больница\"",           42,  4},
        {"ГБУЗ МО \"Долгопрудненская больница\"",          25,  3},
        {"ГБУЗ МО \"Ступинская клиническая больница\"",    37,  4},
        {"ГБУЗ МО \"Мытищинская ОКБ\"",                   202, 20},
        {"ГБУЗ МО \"Лыткаринская больница\"",              38,  4},
        {"ГБУЗ МО \"Шаховская больница\"",                 9,   1}
    };

    // Планы по МО для проекта 2 (Амбулаторная помощь)
    private static final Object[][] PLANS_P2 = {
        {"ГБУЗ МО \"Одинцовская областная больница\"",     76,  8},
        {"ГБУЗ МО \"Наро-Фоминская больница\"",           34,  3},
        {"ГБУЗ МО \"Коломенская больница\"",               40,  4},
        {"ГБУЗ МО \"Щелковская больница\"",                66,  7},
        {"ГБУЗ МО \"Клинская больница\"",                  22,  2},
        {"ГБУЗ МО \"Пушкинская клиническая больница им. проф. Розанова В.Н.\"", 67, 7},
        {"ГБУЗ МО \"Волоколамская больница\"",             8,   1},
        {"ГБУЗ МО \"Ногинская больница\"",                 48,  5},
        {"ГБУЗ МО \"Реутовская клиническая больница\"",    14,  1},
        {"ГБУЗ МО \"Красногорская больница\"",             28,  3},
        {"ГБУЗ МО \"Балашихинская больница\"",             113, 11},
        {"ГБУЗ МО \"Подольская ОКБ\"",                    101, 10},
        {"ГБУЗ МО \"Сергиево-Посадская больница\"",       34,  3},
        {"ГБУЗ МО \"Серпуховская больница\"",              30,  3},
        {"ГБУЗ МО \"Лобненская больница\"",                19,  2},
        {"ГБУЗ МО \"Луховицкая больница\"",                10,  1},
        {"ГБУЗ МО \"Дубненская больница\"",                5,   1},
        {"ГБУЗ МО \"Электростальская больница\"",          29,  3},
        {"ГБУЗ МО \"Королевская больница\"",               48,  5},
        {"ГБУЗ МО \"Котельниковская поликлиника\"",        11,  1},
        {"ГБУЗ МО \"Чеховская больница\"",                 17,  2},
        {"ГБУЗ МО \"Домодедовская больница\"",             40,  4},
        {"ГБУЗ МО \"Рузская больница\"",                   11,  1},
        {"ГБУЗ МО \"Видновская клиническая больница\"",    27,  3},
        {"ГБУЗ МО \"Шатурская больница\"",                 16,  2},
        {"ГБУЗ МО \"Люберецкая областная больница\"",      102, 10},
        {"ГБУЗ МО \"Химкинская клиническая больница\"",    52,  5},
        {"ГБУЗ МО \"Воскресенская больница\"",             45,  5},
        {"ГБУЗ МО \"Егорьевская больница\"",               11,  1},
        {"ГБУЗ МО \"Жуковская ОКБ\"",                      22,  2},
        {"ГБУЗ МО \"Истринская клиническая больница\"",    26,  3},
        {"ГБУЗ МО \"Краснознаменская поликлиника\"",       4,   0},
        {"ГБУЗ МО \"Лотошинская больница\"",               2,   0},
        {"ГБУЗ МО \"Можайская больница\"",                 6,   1},
        {"ГБУЗ МО \"Орехово-Зуевская больница\"",         34,  3},
        {"ГБУЗ МО \"Протвинская больница\"",               4,   0},
        {"ГБУЗ МО \"Серебряно-Прудская больница\"",        4,   0},
        {"ГБУЗ МО \"Центр профилактики и борьбы со СПИД\"", 0, 0},
        {"ГБУЗ МО \"Дмитровская больница\"",               33,  3},
        {"ГБУЗ МО \"Каширская больница\"",                 8,   1},
        {"ГБУЗ МО \"Зарайская больница\"",                 4,   0},
        {"ГБУЗ МО \"Павлово-Посадская больница\"",         21,  2},
        {"ГБУЗ МО \"Солнечногорская больница\"",           15,  2},
        {"ГБУЗ МО \"Долгопрудненская больница\"",          9,   1},
        {"ГБУЗ МО \"Ступинская клиническая больница\"",    13,  1},
        {"ГБУЗ МО \"Мытищинская ОКБ\"",                   72,  7},
        {"ГБУЗ МО \"Лыткаринская больница\"",              13,  1},
        {"ГБУЗ МО \"Шаховская больница\"",                 3,   0}
    };

    // Планы по МО для проекта 3 (ПЦР-тестирование)
    private static final Object[][] PLANS_P3 = {
        {"ГБУЗ МО \"Балашихинская больница\"",             426, 8},
        {"ГБУЗ МО \"Видновская клиническая больница\"",    102, 2},
        {"ГБУЗ МО \"Волоколамская больница\"",             29,  1},
        {"ГБУЗ МО \"Воскресенская больница\"",             168, 3},
        {"ГБУЗ МО \"Дмитровская больница\"",               127, 2},
        {"ГБУЗ МО \"Долгопрудненская больница\"",          34,  1},
        {"ГБУЗ МО \"Домодедовская больница\"",             151, 3},
        {"ГБУЗ МО \"Дубненская больница\"",                19,  0},
        {"ГБУЗ МО \"Егорьевская больница\"",               40,  1},
        {"ГБУЗ МО \"Жуковская ОКБ\"",                      83,  2},
        {"ГБУЗ МО \"Зарайская больница\"",                 14,  0},
        {"ГБУЗ МО \"Истринская клиническая больница\"",    99,  2},
        {"ГБУЗ МО \"Каширская больница\"",                 29,  1},
        {"ГБУЗ МО \"Клинская больница\"",                  83,  2},
        {"ГБУЗ МО \"Коломенская больница\"",               150, 3},
        {"ГБУЗ МО \"Королевская больница\"",               180, 3},
        {"ГБУЗ МО \"Котельниковская поликлиника\"",        43,  1},
        {"ГБУЗ МО \"Красногорская больница\"",             106, 2},
        {"ГБУЗ МО \"Краснознаменская поликлиника\"",       17,  0},
        {"ГБУЗ МО \"Лобненская больница\"",                75,  1},
        {"ГБУЗ МО \"Лотошинская больница\"",               7,   0},
        {"ГБУЗ МО \"Луховицкая больница\"",                38,  1},
        {"ГБУЗ МО \"Лыткаринская больница\"",              51,  1},
        {"ГБУЗ МО \"Люберецкая областная больница\"",      388, 7},
        {"ГБУЗ МО \"Дзержинская больница\"",               0,   0},
        {"ГБУЗ МО \"Можайская больница\"",                 22,  0},
        {"ГБУЗ МО МОНИКИ им. М.Ф. Владимирского",          0,   0},
        {"ГБУЗ МО \"Мытищинская ОКБ\"",                   270, 5},
        {"ГБУЗ МО \"Наро-Фоминская больница\"",           130, 3},
        {"ГБУЗ МО \"Ногинская больница\"",                 179, 3},
        {"ГБУЗ МО \"Одинцовская областная больница\"",     286, 6},
        {"ГБУЗ МО \"Орехово-Зуевская больница\"",         132, 3},
        {"ГБУЗ МО \"Павлово-Посадская больница\"",         80,  2},
        {"ГБУЗ МО \"Подольская ОКБ\"",                    390, 8},
        {"ГБУЗ МО \"Протвинская больница\"",               0,   0},
        {"ГБУЗ МО \"Пушкинская клиническая больница им. проф. Розанова В.Н.\"", 252, 5},
        {"ГБУЗ МО \"Раменская больница\"",                 166, 3},
        {"ГБУЗ МО \"Реутовская клиническая больница\"",    55,  1},
        {"ГБУЗ МО \"Рузская больница\"",                   41,  1},
        {"ГБУЗ МО \"Сергиево-Посадская больница\"",       129, 2},
        {"ГБУЗ МО \"Серебряно-Прудская больница\"",        16,  0},
        {"ГБУЗ МО \"Серпуховская больница + Протвино\"",   114, 2},
        {"ГБУЗ МО \"Солнечногорская больница\"",           56,  1},
        {"ГБУЗ МО \"Ступинская клиническая больница\"",    49,  1},
        {"ГБУЗ МО \"Химкинская клиническая больница\"",    197, 4},
        {"ГБУЗ МО \"Центр профилактики и борьбы со СПИД\"", 0,  0},
        {"ГБУЗ МО \"Чеховская больница\"",                 65,  1},
        {"ГБУЗ МО \"Шатурская больница\"",                 62,  1},
        {"ГБУЗ МО \"Шаховская больница\"",                 12,  0},
        {"ГБУЗ МО \"Щелковская больница\"",                248, 5},
        {"ГБУЗ МО \"Электростальская больница\"",          110, 2}
    };

    private static final Object[][][] ALL_PLANS = {null, PLANS_P1, PLANS_P2, PLANS_P3};

    private final HcvPlanRepository       planRepo;
    private final HcvProgressRepository   progressRepo;
    private final HcvWeeklyPlanRepository weeklyPlanRepo;
    private final KpiService              kpiService;

    public HcvService(HcvPlanRepository planRepo, HcvProgressRepository progressRepo,
                      HcvWeeklyPlanRepository weeklyPlanRepo, KpiService kpiService) {
        this.planRepo       = planRepo;
        this.progressRepo   = progressRepo;
        this.weeklyPlanRepo = weeklyPlanRepo;
        this.kpiService     = kpiService;
    }

    @PostConstruct
    @Transactional
    public void initPlans() {
        for (int pid = 1; pid <= 3; pid++) {
            if (planRepo.countByProjectId(pid) == 0) {
                for (Object[] row : ALL_PLANS[pid]) {
                    planRepo.save(new HcvPlan(pid, (String) row[0], (int) row[1], (int) row[2]));
                }
            }
        }
    }

    public HcvProjectData getProjectData(int projectId) {
        int idx = projectId - 1;
        List<HcvPlan>     plans    = planRepo.findByProjectIdOrderByOrgName(projectId);
        List<HcvProgress> progress = progressRepo.findByProjectId(projectId);

        Map<String, HcvProgress> progMap = new LinkedHashMap<>();
        for (HcvProgress p : progress) progMap.put(p.getOrgName(), p);

        List<HcvOrgRow> rows = new ArrayList<>();
        int totalAcc = 0, totalWeek = 0;

        for (HcvPlan plan : plans) {
            HcvProgress prog = progMap.get(plan.getOrgName());
            int acc  = prog != null ? prog.getAccumulated()    : 0;
            int week = prog != null ? prog.getLastWeekActual() : 0;
            totalAcc  += acc;
            totalWeek += week;
            rows.add(new HcvOrgRow(plan.getOrgName(), plan.getAnnualPlan(), plan.getWeeklyPlan(), acc, week));
        }

        rows.sort((a, b) -> {
            int cmp = Double.compare(b.getPlanCompletionDouble(), a.getPlanCompletionDouble());
            if (cmp != 0) return cmp;
            return Double.compare(b.getWeeklyCompletionDouble(), a.getWeeklyCompletionDouble());
        });

        String[] targetKeys = {KpiService.TARGET_STAT, KpiService.TARGET_AMB, KpiService.TARGET_UVO};
        int target = kpiService.getTarget(targetKeys[idx], PROJECT_TARGETS[idx]);

        return new HcvProjectData(
                projectId,
                PROJECT_TITLES[idx],
                target,
                PROJECT_LEADS[idx],
                PROJECT_DEADLINES[idx],
                totalAcc,
                totalWeek,
                rows);
    }

    /**
     * Загружает фактические данные из Excel-файла.
     * Формат: столбец A — название МО, столбец B — накоплено, столбец C — за последнюю неделю (опц.)
     */
    @Transactional
    public int uploadProgress(MultipartFile file, int projectId) throws Exception {
        List<HcvPlan> plans = planRepo.findByProjectIdOrderByOrgName(projectId);
        Map<String, HcvPlan> planMap = new LinkedHashMap<>();
        for (HcvPlan p : plans) planMap.put(normalize(p.getOrgName()), p);

        int updated = 0;
        LocalDate today = LocalDate.now();

        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String orgName = fmt.formatCellValue(row.getCell(0), evaluator).trim();
                if (orgName.isEmpty() || orgName.equalsIgnoreCase("итого")) continue;

                String accStr  = fmt.formatCellValue(row.getCell(1), evaluator).trim().replace(" ", "").replace(",", ".");
                String weekStr = row.getCell(2) != null ? fmt.formatCellValue(row.getCell(2), evaluator).trim().replace(" ", "").replace(",", ".") : "";

                int acc  = accStr.isEmpty()  ? 0 : (int) Double.parseDouble(accStr);
                int week = weekStr.isEmpty() ? 0 : (int) Double.parseDouble(weekStr);

                String key = normalize(orgName);
                HcvPlan plan = planMap.get(key);
                if (plan == null) continue;

                Optional<HcvProgress> existing = progressRepo.findByProjectIdAndOrgName(projectId, plan.getOrgName());
                HcvProgress prog = existing.orElse(new HcvProgress(projectId, plan.getOrgName(), 0, 0, today));
                prog.setAccumulated(acc);
                prog.setLastWeekActual(week);
                prog.setReportDate(today);
                progressRepo.save(prog);
                updated++;
            }
        }
        return updated;
    }

    /**
     * Загружает «Недельный план.xlsx» в таблицу hcv_weekly_plan для указанной недели.
     * Столбцы (0-based):
     *   B(1) — МО
     *   C(2) — план год амбулаторно
     *   D(3) — план месяц амбулаторно
     *   E(4) — план месяц стационар
     *   H(7) — план год стационар
     * Первые 2 строки — заголовки, пропускаются.
     * Данные за ту же неделю перезаписываются, остальные недели не затрагиваются.
     */
    @Transactional
    public int uploadWeeklyPlan(MultipartFile file, LocalDate reportWeek) throws Exception {
        weeklyPlanRepo.deleteByReportWeek(reportWeek);

        List<HcvWeeklyPlanRow> rows = new ArrayList<>();

        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();

            for (int r = 2; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String orgName = fmt.formatCellValue(row.getCell(1), evaluator).trim();
                if (orgName.isEmpty() || orgName.equalsIgnoreCase("итого")) continue;

                Integer planYearAmb    = parseIntOrNull(fmt, evaluator, row.getCell(2));  // C
                Integer waitingAmb     = parseIntOrNull(fmt, evaluator, row.getCell(3));  // D
                Integer pctAmb         = parseIntOrNull(fmt, evaluator, row.getCell(4));  // E
                Integer weeklyPlanAmb  = parseIntOrNull(fmt, evaluator, row.getCell(5));  // F
                Integer weeklyFactAmb  = parseIntOrNull(fmt, evaluator, row.getCell(6));  // G
                Integer dynamicAmb     = parseIntOrNull(fmt, evaluator, row.getCell(7));  // H
                Integer planYearStat   = parseIntOrNull(fmt, evaluator, row.getCell(8));  // I
                Integer referralsStat  = parseIntOrNull(fmt, evaluator, row.getCell(9));  // J
                Integer pctStat        = parseIntOrNull(fmt, evaluator, row.getCell(10)); // K
                Integer weeklyPlanStat = parseIntOrNull(fmt, evaluator, row.getCell(11)); // L
                Integer weeklyFactStat = parseIntOrNull(fmt, evaluator, row.getCell(12)); // M
                Integer dynamicStat    = parseIntOrNull(fmt, evaluator, row.getCell(13)); // N

                rows.add(new HcvWeeklyPlanRow(orgName,
                        planYearAmb, waitingAmb, pctAmb, weeklyPlanAmb, weeklyFactAmb, dynamicAmb,
                        planYearStat, referralsStat, pctStat, weeklyPlanStat, weeklyFactStat, dynamicStat,
                        reportWeek));
            }
        }

        weeklyPlanRepo.saveAll(rows);
        return rows.size();
    }

    public List<HcvWeeklyPlanRow> getWeeklyPlan(LocalDate reportWeek) {
        return weeklyPlanRepo.findByReportWeekOrderByOrgNameAsc(reportWeek);
    }

    public List<LocalDate> getWeeklyPlanWeeks() {
        return weeklyPlanRepo.findDistinctWeeks();
    }

    public byte[] generateWeeklyPlanTemplate() throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Недельный план");

            CellStyle groupStyle = wb.createCellStyle();
            Font gf = wb.createFont(); gf.setBold(true);
            groupStyle.setFont(gf);
            groupStyle.setFillForegroundColor(IndexedColors.CORNFLOWER_BLUE.getIndex());
            groupStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            groupStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle headerStyle = wb.createCellStyle();
            Font hf = wb.createFont(); hf.setBold(true);
            headerStyle.setFont(hf);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setWrapText(true);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            // Строка 1 — группы
            Row g = sheet.createRow(0);
            cell(g, 0, "№", groupStyle);
            cell(g, 1, "МО", groupStyle);
            cell(g, 2, "Амбулаторная помощь", groupStyle);
            cell(g, 8, "Стационарная помощь", groupStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 2, 7));
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 8, 13));

            // Строка 2 — заголовки столбцов
            Row h = sheet.createRow(1);
            String[] cols = {"№", "МО",
                "План 2026", "Ожидают лечение", "% плана", "Неделя план", "Неделя факт", "Динамика (амб.)",
                "План 2026", "Направлены на лечение", "% от плана", "Недельный план (стац.)", "Неделя факт", "Динамика (стац.)"};
            for (int i = 0; i < cols.length; i++) {
                cell(h, i, cols[i], headerStyle);
            }

            sheet.setColumnWidth(0, 1500);
            sheet.setColumnWidth(1, 14000);
            for (int i = 2; i <= 13; i++) sheet.setColumnWidth(i, 4500);

            wb.write(out);
            return out.toByteArray();
        }
    }

    private void cell(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        if (style != null) c.setCellStyle(style);
    }

    /** Возвращает данные последней загруженной недели, либо пустой список. */
    public List<HcvWeeklyPlanRow> getLatestWeeklyPlan() {
        List<LocalDate> weeks = weeklyPlanRepo.findDistinctWeeks();
        if (weeks.isEmpty()) return List.of();
        return weeklyPlanRepo.findByReportWeekOrderByOrgNameAsc(weeks.get(0));
    }

    private Integer parseIntOrNull(DataFormatter fmt, FormulaEvaluator evaluator, org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) return null;
        String s = fmt.formatCellValue(cell, evaluator).trim().replace(" ", "").replace(",", ".");
        if (s.isEmpty()) return null;
        try { return (int) Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
    }

    private Integer parseIntOrNull(DataFormatter fmt, org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) return null;
        String s = fmt.formatCellValue(cell).trim().replace(" ", "").replace(",", ".");
        if (s.isEmpty()) return null;
        try { return (int) Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
    }

    private String normalize(String s) {
        return s.toLowerCase().replaceAll("[\"«»'`]", "").replaceAll("\\s+", " ").trim();
    }
}
