package com.emias.dashboard.controller;

import com.emias.dashboard.entity.FacilityPlan;
import com.emias.dashboard.entity.MoTask;
import com.emias.dashboard.repository.MoTaskRepository;
import com.emias.dashboard.repository.ScreeningRepository;
import com.emias.dashboard.model.AgeDiagram;
import com.emias.dashboard.model.Conclusions;
import com.emias.dashboard.model.DashboardConfig;
import com.emias.dashboard.model.FacilityRating;
import com.emias.dashboard.model.MonthlyChartData;
import com.emias.dashboard.model.DashboardSnapshot;
import com.emias.dashboard.model.HcvProjectData;
import com.emias.dashboard.model.PathwayStats;
import com.emias.dashboard.model.PatientRecord;
import com.emias.dashboard.model.ScreeningStats;
import com.emias.dashboard.service.DashboardCacheService;
import com.emias.dashboard.service.HcvRegistryService;
import com.emias.dashboard.service.HcvService;
import com.emias.dashboard.service.DiagramService;
import com.emias.dashboard.service.FacilityMappingService;
import com.emias.dashboard.service.FacilityPlanService;
import com.emias.dashboard.service.ReportService;
import com.emias.dashboard.service.SettingsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class PageController {

    private final ReportService          reportService;
    private final DiagramService         diagramService;
    private final SettingsService        settingsService;
    private final FacilityMappingService facilityMappingService;
    private final FacilityPlanService    facilityPlanService;
    private final HcvService             hcvService;
    private final HcvRegistryService     hcvRegistryService;
    private final DashboardCacheService  dashboardCache;
    private final MoTaskRepository       moTaskRepository;
    private final ScreeningRepository    screeningRepository;

    public PageController(ReportService reportService,
                          DiagramService diagramService,
                          SettingsService settingsService,
                          FacilityMappingService facilityMappingService,
                          FacilityPlanService facilityPlanService,
                          HcvService hcvService,
                          HcvRegistryService hcvRegistryService,
                          DashboardCacheService dashboardCache,
                          MoTaskRepository moTaskRepository,
                          ScreeningRepository screeningRepository) {
        this.reportService          = reportService;
        this.diagramService         = diagramService;
        this.settingsService        = settingsService;
        this.facilityMappingService = facilityMappingService;
        this.facilityPlanService    = facilityPlanService;
        this.hcvService             = hcvService;
        this.hcvRegistryService     = hcvRegistryService;
        this.dashboardCache         = dashboardCache;
        this.moTaskRepository       = moTaskRepository;
        this.screeningRepository    = screeningRepository;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("tile1Name", settingsService.get(SettingsService.TILE_1_NAME,
                "Контроль выполнения плана скрининга ХВГС"));
        model.addAttribute("tile2Name", settingsService.get(SettingsService.TILE_2_NAME,
                "Гепатит С"));
        model.addAttribute("tile3Name", settingsService.get(SettingsService.TILE_3_NAME,
                "Доступ к записям спецконтингента"));

        List<String> dates = reportService.getUploadedDates();
        if (!dates.isEmpty()) {
            model.addAttribute("lastUploadDate", formatDate(dates.get(0)));
        }

        return "home";
    }

    @GetMapping("/dashboard")
    public String index(Model model) {
        DashboardConfig config = settingsService.getConfig();
        model.addAttribute("config", config);

        try {
            List<String> dates = reportService.getUploadedDates();
            if (dates.isEmpty()) {
                throw new Exception("Нет загруженных файлов");
            }

            // Ключ кэша: список дат + годовой план (план влияет на вычисления)
            String cacheKey = dates + "|" + config.getAnnualPlan();
            DashboardSnapshot snap = dashboardCache.get(cacheKey);

            if (snap == null) {
                long annualPlan = config.getAnnualPlan();

                List<PatientRecord> records = reportService.readAllRecords();

                ScreeningStats stats = diagramService.buildStats(records, annualPlan);

                Map<String, List<PatientRecord>> singleFile = new LinkedHashMap<>();
                singleFile.put("Все данные", records);

                Map<String, String> mappingMap = facilityMappingService.getMappingMap();
                Map<String, FacilityPlan> plansByName = new LinkedHashMap<>();
                for (FacilityPlan plan : facilityPlanService.getAllPlans()) {
                    plansByName.put(plan.getFacilityName(), plan);
                }

                List<FacilityRating> rating = diagramService.buildFacilityRating(records, mappingMap, plansByName);
                List<PatientRecord> monthlyRecords = reportService.readRecordsForMonth(dates.get(0));
                diagramService.enrichWithMonthlyFact(rating, monthlyRecords);

                snap = new DashboardSnapshot(
                        stats,
                        diagramService.buildScreeningAgeCounts(records),
                        diagramService.buildMonthlyChartData(records, annualPlan),
                        diagramService.buildAgeDiagram(singleFile),
                        rating,
                        diagramService.buildConclusions(records, stats, rating, annualPlan, dates.get(0)),
                        formatMonth(dates.get(0))
                );
                dashboardCache.put(cacheKey, snap);
            }

            model.addAttribute("stats",          snap.stats);
            model.addAttribute("ageCounts",       snap.ageCounts);
            model.addAttribute("monthly",         snap.monthly);
            model.addAttribute("diagram",         snap.diagram);
            model.addAttribute("rating",          snap.rating);
            model.addAttribute("conclusions",     snap.conclusions);
            model.addAttribute("lastUploadDate",  snap.lastUploadDate);

        } catch (Exception e) {
            model.addAttribute("error", "Файл отчёта не загружен. Перейдите в панель администратора и загрузите файл.");
        }

        return "index";
    }

    @GetMapping("/hepatitis")
    public String hepatitis(Model model) {
        List<HcvProjectData> projects = List.of(
                hcvService.getProjectData(1),
                hcvService.getProjectData(2),
                hcvService.getProjectData(3));
        model.addAttribute("projects", projects);

        // Статистика скрининга для плитки "Путь пациента"
        try {
            List<String> dates = reportService.getUploadedDates();
            if (!dates.isEmpty()) {
                long annualPlan = settingsService.getConfig().getAnnualPlan();
                String cacheKey = dates + "|" + annualPlan;
                DashboardSnapshot snap = dashboardCache.get(cacheKey);
                if (snap == null) {
                    List<PatientRecord> records = reportService.readAllRecords();
                    model.addAttribute("screeningStats", diagramService.buildStats(records, annualPlan));
                } else {
                    model.addAttribute("screeningStats", snap.stats);
                }
            }
        } catch (Exception ignored) {}

        // Статистика пути пациента из свода ВГС (только 2026 год)
        try {
            if (hcvRegistryService.count() > 0) {
                model.addAttribute("pathwayStats", hcvRegistryService.buildPathwayStats());
                model.addAttribute("pcrStats", hcvRegistryService.buildPcrDashboardForYear(2026));
                model.addAttribute("vimisFilledCount", hcvRegistryService.countVimisNonEmpty());
                model.addAttribute("treatmentEndCount", hcvRegistryService.countTreatmentEndDateFilled());
                model.addAttribute("uvo12PcrCount", hcvRegistryService.countUvo12PcrDateFilled());
                model.addAttribute("monikConclusion2026Count", hcvRegistryService.countMonikConclusion2026());
                model.addAttribute("monikAmbulatory2026Count", hcvRegistryService.countMonikAmbulatory2026());
                model.addAttribute("monikDayHospital2026Count", hcvRegistryService.countMonikDayHospital2026());
                model.addAttribute("dayHospitalRating", hcvRegistryService.buildDayHospitalRatingRows());
                model.addAttribute("ambulatoryRating", hcvRegistryService.buildAmbulatoryRatingRows());
                model.addAttribute("uvoRating", hcvRegistryService.buildUvoRatingRows());
                model.addAttribute("ambulatory2026Count", hcvRegistryService.countAmbulatory2026());
                model.addAttribute("dayHospital2026Count", hcvRegistryService.countDayHospital2026());
                model.addAttribute("analyticsStats", hcvRegistryService.buildAnalyticsStats());
            }
        } catch (Exception ignored) {}

        model.addAttribute("moTasks", moTaskRepository.findAllByOrderByIdAsc());

        try {
            model.addAttribute("screeningTotalCount", screeningRepository.count());
        } catch (Exception ignored) {}

        try {
            List<com.emias.dashboard.entity.HcvWeeklyPlanRow> wp = hcvService.getLatestWeeklyPlan();
            if (!wp.isEmpty()) {
                model.addAttribute("weeklyPlanRows", wp);
                model.addAttribute("weeklyPlanDate", wp.get(0).getReportWeek().toString());
            }
        } catch (Exception ignored) {}

        return "hepatitis";
    }


    @GetMapping("/pcr-dashboard")
    public String pcrDashboard(Model model) {
        try {
            if (hcvRegistryService.count() > 0) {
                model.addAttribute("stats", hcvRegistryService.buildPcrDashboardForYear(2026));
            }
        } catch (Exception e) {
            model.addAttribute("error", "Данные не загружены. Перейдите в панель администратора и загрузите свод ВГС.");
        }
        return "pcr-dashboard";
    }

    @GetMapping("/naznachenie")
    public String naznachenieDashboard(Model model) {
        try {
            if (hcvRegistryService.count() > 0) {
                model.addAttribute("nazn", hcvRegistryService.buildNaznachenieDashboard());
                model.addAttribute("treatmentEndCount", hcvRegistryService.countTreatmentEndDateFilled());
                model.addAttribute("uvo12PcrCount", hcvRegistryService.countUvo12PcrDateFilled());
                model.addAttribute("pathwayStats", hcvRegistryService.buildPathwayStats());
            }
        } catch (Exception e) {
            model.addAttribute("error", "Данные не загружены.");
        }
        return "naznachenie";
    }

    @GetMapping("/scheme-dashboard")
    public String schemeDashboard(Model model) {
        try {
            if (hcvRegistryService.count() > 0) {
                model.addAttribute("stats", hcvRegistryService.buildSchemeDashboard());
            }
        } catch (Exception e) {
            model.addAttribute("error", "Данные не загружены. Перейдите в панель администратора и загрузите свод ВГС.");
        }
        return "scheme-dashboard";
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
                "Гепатит С"));
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

    private String formatMonth(String date) {
        String[] months = {"Январь","Февраль","Март","Апрель","Май","Июнь",
                           "Июль","Август","Сентябрь","Октябрь","Ноябрь","Декабрь"};
        String[] parts = date.split("-");
        int month = Integer.parseInt(parts[1]) - 1;
        return months[month] + " " + parts[0];
    }
}
