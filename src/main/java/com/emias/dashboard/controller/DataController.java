package com.emias.dashboard.controller;

import com.emias.dashboard.entity.FacilityMapping;
import com.emias.dashboard.entity.FacilityPlan;
import com.emias.dashboard.entity.Screening;
import com.emias.dashboard.repository.ScreeningRepository;
import com.emias.dashboard.service.FacilityMappingService;
import com.emias.dashboard.service.FacilityPlanService;
import com.emias.dashboard.service.FileValidationException;
import com.emias.dashboard.service.ReportService;
import com.emias.dashboard.service.SettingsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Контроллер для операций с файлами отчётов.
 */
@RestController
@RequestMapping("/api")
public class DataController {

    @Value("${logging.file.name:}")
    private String logFilePath;

    @Value("${app.upload-log.file:}")
    private String uploadLogFilePath;

    @Value("${report.upload.dir}")
    private String uploadDir;

    private final ReportService          reportService;
    private final ScreeningRepository    screeningRepository;
    private final SettingsService        settingsService;
    private final FacilityPlanService    facilityPlanService;
    private final FacilityMappingService facilityMappingService;

    public DataController(ReportService reportService,
                          ScreeningRepository screeningRepository,
                          SettingsService settingsService,
                          FacilityPlanService facilityPlanService,
                          FacilityMappingService facilityMappingService) {
        this.reportService          = reportService;
        this.screeningRepository    = screeningRepository;
        this.settingsService        = settingsService;
        this.facilityPlanService    = facilityPlanService;
        this.facilityMappingService = facilityMappingService;
    }

    /**
     * Принимает Excel-файл и дату, сохраняет файл на диск.
     * Если за эту дату уже был файл — перезаписывает его.
     *
     * @param file загруженный файл
     * @param date дата в формате "2026-05-16" (приходит из <input type="date">)
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("date") String date) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Файл не выбран");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
            return ResponseEntity.badRequest().body("Нужен файл формата .xlsx");
        }

        if (date == null || date.isBlank()) {
            return ResponseEntity.badRequest().body("Не выбрана дата");
        }

        try {
            reportService.saveUploadedFile(file, date);
            return ResponseEntity.ok(Map.of("success", true, "message", "Файл за " + date + " загружен успешно"));
        } catch (FileValidationException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "errors", e.getErrors()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "errors", List.of("Ошибка при сохранении: " + e.getMessage())));
        }
    }

    /**
     * Возвращает список дат загруженных файлов.
     * Используется в панели администратора для отображения списка.
     */
    @GetMapping("/dates")
    public List<String> getUploadedDates() {
        return reportService.getUploadedDates();
    }

    /**
     * Текущий прогресс обработки файла на сервере.
     * Клиент опрашивает этот эндпоинт каждые 500 мс во время загрузки.
     */
    @GetMapping("/upload-progress")
    public Map<String, Object> getUploadProgress() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("inProgress", reportService.isUploadInProgress());
        result.put("current",    reportService.getProgressCurrent());
        result.put("total",      reportService.getProgressTotal());
        return result;
    }

    @GetMapping("/settings")
    public Map<String, String> getSettings() {
        return Map.of(
            SettingsService.DASHBOARD_TITLE,       settingsService.get(SettingsService.DASHBOARD_TITLE,       "Контроль выполнения плана скрининга ХВГС"),
            SettingsService.ANNUAL_PLAN,            settingsService.get(SettingsService.ANNUAL_PLAN,            "430270"),
            SettingsService.CHART_MONTHLY_TITLE,   settingsService.get(SettingsService.CHART_MONTHLY_TITLE,   "Динамика выполнения плана скрининга по месяцам"),
            SettingsService.CHART_MONTHLY_ENABLED, settingsService.get(SettingsService.CHART_MONTHLY_ENABLED, "true"),
            SettingsService.CHART_AGES_TITLE,      settingsService.get(SettingsService.CHART_AGES_TITLE,      "Распределение по возрасту"),
            SettingsService.CHART_AGES_ENABLED,    settingsService.get(SettingsService.CHART_AGES_ENABLED,    "true")
        );
    }

    @PostMapping("/settings")
    public ResponseEntity<String> saveSettings(@RequestBody Map<String, String> values) {
        try {
            // Валидация плана — должен быть положительным числом
            if (values.containsKey(SettingsService.ANNUAL_PLAN)) {
                long plan = Long.parseLong(values.get(SettingsService.ANNUAL_PLAN));
                if (plan <= 0) {
                    return ResponseEntity.badRequest().body("Годовой план должен быть положительным числом");
                }
            }
            settingsService.save(values);
            return ResponseEntity.ok("Настройки сохранены");
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body("Годовой план должен быть числом");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Ошибка: " + e.getMessage());
        }
    }

    private static final Map<String, String> SORTABLE_FIELDS = Map.ofEntries(
        Map.entry("mkab",                  "mkabNumber"),
        Map.entry("lastName",              "lastName"),
        Map.entry("firstName",             "firstName"),
        Map.entry("middleName",            "middleName"),
        Map.entry("visitType",             "visitType"),
        Map.entry("snils",                 "snils"),
        Map.entry("omsPolicy",             "omsPolicy"),
        Map.entry("dispensarizationDate",  "dispensarizationDate"),
        Map.entry("researchDate",          "researchDate"),
        Map.entry("cardClosingDate",       "cardClosingDate"),
        Map.entry("birthDate",             "birthDate"),
        Map.entry("tfomsServiceCode",      "tfomsServiceCode"),
        Map.entry("valueText",             "valueText"),
        Map.entry("referralNumber",        "referralNumber"),
        Map.entry("refusal",               "refusal"),
        Map.entry("researchResult",        "researchResult"),
        Map.entry("serviceCode",           "serviceCode"),
        Map.entry("researchStatus",        "researchStatus"),
        Map.entry("doctorName",            "doctorName"),
        Map.entry("ogrnFrom",              "ogrnFrom"),
        Map.entry("facilityFrom",          "facilityFrom"),
        Map.entry("ogrnTo",                "ogrnTo"),
        Map.entry("facilityTo",            "facilityTo"),
        Map.entry("pcrResult",             "pcrResult"),
        Map.entry("pcrDone",               "pcrDone"),
        Map.entry("ageAtExport",           "ageAtExport"),
        Map.entry("ageAtResearch",         "ageAtResearch"),
        Map.entry("biomaterialDate",       "biomaterialDate"),
        Map.entry("deliveryDate",          "deliveryDate"),
        Map.entry("researchConductedDate", "researchConductedDate")
    );

    @GetMapping("/records")
    public ResponseEntity<?> getRecords(
            @RequestParam String date,
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "50")  int size,
            @RequestParam(defaultValue = "")    String search,
            @RequestParam(defaultValue = "")    String sort,
            @RequestParam(defaultValue = "asc") String dir) {
        try {
            LocalDate reportDate = LocalDate.parse(date);
            Sort.Direction direction = "desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC;
            String fieldName = SORTABLE_FIELDS.getOrDefault(sort, "id");
            PageRequest pageable = PageRequest.of(
                    page, Math.min(size, 200), Sort.by(direction, fieldName));
            Page<Screening> pageResult = screeningRepository.searchByReportDate(
                    reportDate, search.trim(), pageable);
            List<Map<String, String>> records = pageResult.getContent().stream()
                    .map(this::screeningToMap)
                    .collect(Collectors.toList());
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("records", records);
            response.put("totalCount", pageResult.getTotalElements());
            response.put("page", page);
            response.put("size", size);
            response.put("totalPages", pageResult.getTotalPages());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Ошибка: " + e.getMessage());
        }
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> downloadFile(@RequestParam String date) {
        try {
            LocalDate.parse(date);
            Path filePath = Paths.get(uploadDir).resolve("report_" + date + ".xlsx");
            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }
            Resource resource = new FileSystemResource(filePath);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"report_" + date + ".xlsx\"")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private Map<String, String> screeningToMap(Screening s) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("mkab",                  nvl(s.getMkabNumber()));
        m.put("lastName",              nvl(s.getLastName()));
        m.put("firstName",             nvl(s.getFirstName()));
        m.put("middleName",            nvl(s.getMiddleName()));
        m.put("visitType",             nvl(s.getVisitType()));
        m.put("snils",                 nvl(s.getSnils()));
        m.put("omsPolicy",             nvl(s.getOmsPolicy()));
        m.put("dispensarizationDate",  nvl(s.getDispensarizationDate()));
        m.put("researchDate",          nvl(s.getResearchDate()));
        m.put("cardClosingDate",       nvl(s.getCardClosingDate()));
        m.put("birthDate",             nvl(s.getBirthDate()));
        m.put("tfomsServiceCode",      nvl(s.getTfomsServiceCode()));
        m.put("valueText",             nvl(s.getValueText()));
        m.put("referralNumber",        nvl(s.getReferralNumber()));
        m.put("refusal",               nvl(s.getRefusal()));
        m.put("researchResult",        nvl(s.getResearchResult()));
        m.put("serviceCode",           nvl(s.getServiceCode()));
        m.put("researchStatus",        nvl(s.getResearchStatus()));
        m.put("doctorName",            nvl(s.getDoctorName()));
        m.put("ogrnFrom",              nvl(s.getOgrnFrom()));
        m.put("facilityFrom",          nvl(s.getFacilityFrom()));
        m.put("ogrnTo",                nvl(s.getOgrnTo()));
        m.put("facilityTo",            nvl(s.getFacilityTo()));
        m.put("pcrResult",             nvl(s.getPcrResult()));
        m.put("pcrDone",               nvl(s.getPcrDone()));
        m.put("ageAtExport",           nvl(s.getAgeAtExport()));
        m.put("ageAtResearch",         nvl(s.getAgeAtResearch()));
        m.put("biomaterialDate",       nvl(s.getBiomaterialDate()));
        m.put("deliveryDate",          nvl(s.getDeliveryDate()));
        m.put("researchConductedDate", nvl(s.getResearchConductedDate()));
        return m;
    }

    private String nvl(String v) { return v != null ? v : ""; }

    @GetMapping("/logs")
    public ResponseEntity<String> getLogs(@RequestParam(defaultValue = "100") int lines) {
        return readLogFile(logFilePath, lines);
    }

    @GetMapping("/upload-logs")
    public ResponseEntity<String> getUploadLogs(@RequestParam(defaultValue = "100") int lines) {
        return readLogFile(uploadLogFilePath, lines);
    }

    private ResponseEntity<String> readLogFile(String filePath, int lines) {
        if (filePath == null || filePath.isBlank()) {
            return ResponseEntity.ok("Лог-файл не настроен");
        }
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                return ResponseEntity.ok("Лог-файл ещё не создан: " + path.toAbsolutePath());
            }
            List<String> allLines = Files.readAllLines(path);
            int from = Math.max(0, allLines.size() - lines);
            return ResponseEntity.ok(String.join("\n", allLines.subList(from, allLines.size())));
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body("Ошибка чтения лог-файла: " + e.getMessage());
        }
    }

    // ── Планы по медицинским организациям ────────────────────────────────────

    /**
     * Принимает Excel-файл с планами скрининга по медицинским организациям.
     * При загрузке все старые планы заменяются новыми.
     */
    @PostMapping("/upload-plans")
    public ResponseEntity<?> uploadPlans(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Файл не выбран");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
            return ResponseEntity.badRequest().body("Нужен файл формата .xlsx");
        }
        try {
            int count = facilityPlanService.uploadPlans(file);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Планы загружены успешно: " + count + " организаций"));
        } catch (FileValidationException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "errors", e.getErrors()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "errors", List.of("Ошибка при загрузке: " + e.getMessage())));
        }
    }

    /**
     * Возвращает список всех планов по медицинским организациям.
     */
    @GetMapping("/plans")
    public ResponseEntity<?> getPlans() {
        try {
            List<FacilityPlan> plans = facilityPlanService.getAllPlans();
            List<Map<String, Object>> result = new ArrayList<>();
            for (FacilityPlan p : plans) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("facilityName",        p.getFacilityName());
                m.put("annualPlan254545",     p.getAnnualPlan254545());
                m.put("annualPlanAllAges",    p.getAnnualPlanAllAges());
                m.put("annualPlanTotal",      p.getAnnualPlanTotal());
                m.put("monthlyPlan254545",    p.getMonthlyPlan254545());
                m.put("monthlyPlanAllAges",   p.getMonthlyPlanAllAges());
                m.put("monthlyPlanTotal",     p.getMonthlyPlanTotal());
                m.put("weeklyPlan254545",     p.getWeeklyPlan254545());
                m.put("weeklyPlanAllAges",    p.getWeeklyPlanAllAges());
                m.put("weeklyPlanTotal",      p.getWeeklyPlanTotal());
                result.add(m);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Ошибка: " + e.getMessage()));
        }
    }

    /**
     * Краткая сводка по загруженным планам (количество + дата загрузки).
     */
    @GetMapping("/plans/summary")
    public ResponseEntity<?> getPlansSummary() {
        return ResponseEntity.ok(facilityPlanService.getSummary());
    }

    // ── Соответствия названий ЛПУ ────────────────────────────────────────────

    /**
     * Загружает Excel-файл с таблицей соответствий названий ЛПУ.
     * Столбец A — название в скрининге, столбец B — название в планах.
     */
    @PostMapping("/upload-mapping")
    public ResponseEntity<?> uploadMapping(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Файл не выбран");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
            return ResponseEntity.badRequest().body("Нужен файл формата .xlsx");
        }
        try {
            int count = facilityMappingService.uploadMappings(file);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Соответствия загружены: " + count + " организаций"));
        } catch (FileValidationException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "errors", e.getErrors()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "errors", List.of("Ошибка: " + e.getMessage())));
        }
    }

    /**
     * Возвращает список всех соответствий.
     */
    @GetMapping("/mapping")
    public ResponseEntity<?> getMapping() {
        try {
            List<FacilityMapping> list = facilityMappingService.getAllMappings();
            List<Map<String, String>> result = new ArrayList<>();
            for (FacilityMapping m : list) {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("screeningName", m.getScreeningName());
                row.put("planName",      m.getPlanName());
                result.add(row);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Ошибка: " + e.getMessage()));
        }
    }

    // Диагностика: показывает что лежит в базе
    @GetMapping("/debug")
    public List<String> debug() {
        List<com.emias.dashboard.entity.Screening> all = screeningRepository.findAll();
        List<String> result = new ArrayList<>();

        result.add("Всего записей в БД: " + all.size());

        java.util.Set<String> statuses = new java.util.LinkedHashSet<>();
        java.util.Set<String> results  = new java.util.LinkedHashSet<>();
        java.util.Set<String> refusals = new java.util.LinkedHashSet<>();

        for (com.emias.dashboard.entity.Screening s : all) {
            statuses.add("[" + s.getResearchStatus() + "]");
            results.add("[" + s.getResearchResult() + "]");
            refusals.add("[" + s.getRefusal() + "]");
        }

        result.add("Статусы (researchStatus): " + statuses);
        result.add("Результаты (researchResult): " + results);
        result.add("Отказ (refusal): " + refusals);

        return result;
    }
}
