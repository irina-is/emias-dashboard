package com.emias.dashboard.controller;

<<<<<<< HEAD
=======
import com.emias.dashboard.entity.FacilityPlan;
>>>>>>> dev
import com.emias.dashboard.model.AgeDiagram;
import com.emias.dashboard.model.Conclusions;
import com.emias.dashboard.model.DashboardConfig;
import com.emias.dashboard.model.FacilityRating;
import com.emias.dashboard.model.MonthlyChartData;
import com.emias.dashboard.model.PatientRecord;
import com.emias.dashboard.model.ScreeningStats;
import com.emias.dashboard.service.DiagramService;
<<<<<<< HEAD
=======
import com.emias.dashboard.service.FacilityMappingService;
import com.emias.dashboard.service.FacilityPlanService;
>>>>>>> dev
import com.emias.dashboard.service.ReportService;
import com.emias.dashboard.service.SettingsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
<<<<<<< HEAD
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
=======

>>>>>>> dev
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class PageController {

<<<<<<< HEAD
    private final ReportService   reportService;
    private final DiagramService  diagramService;
    private final SettingsService settingsService;

    public PageController(ReportService reportService,
                          DiagramService diagramService,
                          SettingsService settingsService) {
        this.reportService   = reportService;
        this.diagramService  = diagramService;
        this.settingsService = settingsService;
=======
    private final ReportService          reportService;
    private final DiagramService         diagramService;
    private final SettingsService        settingsService;
    private final FacilityMappingService facilityMappingService;
    private final FacilityPlanService    facilityPlanService;

    public PageController(ReportService reportService,
                          DiagramService diagramService,
                          SettingsService settingsService,
                          FacilityMappingService facilityMappingService,
                          FacilityPlanService facilityPlanService) {
        this.reportService          = reportService;
        this.diagramService         = diagramService;
        this.settingsService        = settingsService;
        this.facilityMappingService = facilityMappingService;
        this.facilityPlanService    = facilityPlanService;
>>>>>>> dev
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
<<<<<<< HEAD
    public String index(@RequestParam(required = false) String date, Model model) {
=======
    public String index(Model model) {
>>>>>>> dev
        DashboardConfig config = settingsService.getConfig();
        model.addAttribute("config", config);

        try {
            List<String> dates = reportService.getUploadedDates();

            if (dates.isEmpty()) {
                throw new Exception("Нет загруженных файлов");
            }

<<<<<<< HEAD
            String selectedDate = (date != null && dates.contains(date)) ? date : dates.get(0);
            boolean isLatest = selectedDate.equals(dates.get(0));

            List<PatientRecord> records = reportService.readRecords(selectedDate);
=======
            // Агрегируем данные по всем загруженным месяцам
            List<PatientRecord> records = reportService.readAllRecords();
>>>>>>> dev
            long annualPlan = config.getAnnualPlan();

            ScreeningStats stats = diagramService.buildStats(records, annualPlan);
            model.addAttribute("stats", stats);

            MonthlyChartData monthly = diagramService.buildMonthlyChartData(records, annualPlan);
            model.addAttribute("monthly", monthly);

            Map<String, List<PatientRecord>> singleFile = new LinkedHashMap<>();
<<<<<<< HEAD
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
=======
            singleFile.put("Все данные", records);
            AgeDiagram diagram = diagramService.buildAgeDiagram(singleFile);
            model.addAttribute("diagram", diagram);

            Map<String, String> mappingMap = facilityMappingService.getMappingMap();
            Map<String, FacilityPlan> plansByName = new LinkedHashMap<>();
            for (FacilityPlan plan : facilityPlanService.getAllPlans()) {
                plansByName.put(plan.getFacilityName(), plan);
            }

            List<FacilityRating> rating = diagramService.buildFacilityRating(records, mappingMap, plansByName);
            // Факт месяц — данные последнего загруженного месяца
            List<PatientRecord> monthlyRecords = reportService.readRecordsForMonth(dates.get(0));
            diagramService.enrichWithMonthlyFact(rating, monthlyRecords);
            model.addAttribute("rating", rating);

            Conclusions conclusions = diagramService.buildConclusions(records, stats, rating, annualPlan, dates.get(0));
            model.addAttribute("conclusions", conclusions);

            model.addAttribute("lastUploadDate", formatMonth(dates.get(0)));
>>>>>>> dev

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
<<<<<<< HEAD
=======

    private String formatMonth(String date) {
        String[] months = {"Январь","Февраль","Март","Апрель","Май","Июнь",
                           "Июль","Август","Сентябрь","Октябрь","Ноябрь","Декабрь"};
        String[] parts = date.split("-");
        int month = Integer.parseInt(parts[1]) - 1;
        return months[month] + " " + parts[0];
    }
>>>>>>> dev
}
