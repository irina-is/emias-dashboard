package com.emias.dashboard.controller;

import com.emias.dashboard.model.AgeDiagram;
import com.emias.dashboard.model.Conclusions;
import com.emias.dashboard.model.DashboardConfig;
import com.emias.dashboard.model.FacilityRating;
import com.emias.dashboard.model.MonthlyChartData;
import com.emias.dashboard.model.PatientRecord;
import com.emias.dashboard.model.ScreeningStats;
import com.emias.dashboard.service.DiagramService;
import com.emias.dashboard.service.ReportService;
import com.emias.dashboard.service.SettingsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class PageController {

    private final ReportService   reportService;
    private final DiagramService  diagramService;
    private final SettingsService settingsService;

    public PageController(ReportService reportService,
                          DiagramService diagramService,
                          SettingsService settingsService) {
        this.reportService   = reportService;
        this.diagramService  = diagramService;
        this.settingsService = settingsService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("tile1Name", settingsService.get(SettingsService.TILE_1_NAME,
                "Контроль выполнения плана скрининга ХВГС"));
        model.addAttribute("tile2Name", settingsService.get(SettingsService.TILE_2_NAME,
                "Регистры"));
        model.addAttribute("tile3Name", settingsService.get(SettingsService.TILE_3_NAME,
                "Доступ к записям спецконтингента"));

        List<String> dates = reportService.getUploadedDates();
        if (!dates.isEmpty()) {
            model.addAttribute("lastUploadDate", formatDate(dates.get(0)));
        }

        return "home";
    }

    @GetMapping("/dashboard")
    public String index(@RequestParam(required = false) String date, Model model) {
        DashboardConfig config = settingsService.getConfig();
        model.addAttribute("config", config);

        try {
            List<String> dates = reportService.getUploadedDates();

            if (dates.isEmpty()) {
                throw new Exception("Нет загруженных файлов");
            }

            String selectedDate = (date != null && dates.contains(date)) ? date : dates.get(0);
            boolean isLatest = selectedDate.equals(dates.get(0));

            List<PatientRecord> records = reportService.readRecords(selectedDate);
            long annualPlan = config.getAnnualPlan();

            ScreeningStats stats = diagramService.buildStats(records, annualPlan);
            model.addAttribute("stats", stats);

            MonthlyChartData monthly = diagramService.buildMonthlyChartData(records, annualPlan);
            model.addAttribute("monthly", monthly);

            Map<String, List<PatientRecord>> singleFile = new LinkedHashMap<>();
            singleFile.put(formatDate(selectedDate), records);
            AgeDiagram diagram = diagramService.buildAgeDiagram(singleFile);
            model.addAttribute("diagram", diagram);

            List<FacilityRating> rating = diagramService.buildFacilityRating(records);
            model.addAttribute("rating", rating);

            Conclusions conclusions = diagramService.buildConclusions(records, stats, rating, annualPlan);
            model.addAttribute("conclusions", conclusions);

            model.addAttribute("lastUploadDate", formatDate(selectedDate));
            model.addAttribute("isLatest", isLatest);
            model.addAttribute("selectedDateRaw", selectedDate);
            model.addAttribute("uploadedDates", dates);
            List<String> formattedDates = new ArrayList<>();
            for (String d : dates) formattedDates.add(formatDate(d));
            model.addAttribute("uploadedDatesFormatted", formattedDates);

        } catch (Exception e) {
            model.addAttribute("error", "Файл отчёта не загружен. Перейдите в панель администратора и загрузите файл.");
        }

        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/admin")
    public String admin(Model model) {
        model.addAttribute("config", settingsService.getConfig());
        model.addAttribute("tile1Name", settingsService.get(SettingsService.TILE_1_NAME,
                "Контроль выполнения плана скрининга ХВГС"));
        model.addAttribute("tile2Name", settingsService.get(SettingsService.TILE_2_NAME,
                "Регистры"));
        model.addAttribute("tile3Name", settingsService.get(SettingsService.TILE_3_NAME,
                "Доступ к записям спецконтингента"));
        return "admin";
    }

    private String formatDate(String date) {
        String[] months = {"января","февраля","марта","апреля","мая","июня",
                           "июля","августа","сентября","октября","ноября","декабря"};
        String[] parts = date.split("-");
        int month = Integer.parseInt(parts[1]) - 1;
        return parts[2] + " " + months[month] + " " + parts[0];
    }
}
