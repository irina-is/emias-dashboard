package com.emias.dashboard.controller;

import com.emias.dashboard.repository.ScreeningRepository;
import com.emias.dashboard.service.ReportService;
import com.emias.dashboard.service.SettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Контроллер для операций с файлами отчётов.
 */
@RestController
@RequestMapping("/api")
public class DataController {

    private final ReportService       reportService;
    private final ScreeningRepository screeningRepository;
    private final SettingsService     settingsService;

    public DataController(ReportService reportService,
                          ScreeningRepository screeningRepository,
                          SettingsService settingsService) {
        this.reportService       = reportService;
        this.screeningRepository = screeningRepository;
        this.settingsService     = settingsService;
    }

    /**
     * Принимает Excel-файл и дату, сохраняет файл на диск.
     * Если за эту дату уже был файл — перезаписывает его.
     *
     * @param file загруженный файл
     * @param date дата в формате "2026-05-16" (приходит из <input type="date">)
     */
    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(
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
            return ResponseEntity.ok("Файл за " + date + " загружен успешно");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Ошибка при сохранении: " + e.getMessage());
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
