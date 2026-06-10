package com.emias.dashboard.service;

import com.emias.dashboard.entity.FacilityPlan;
import com.emias.dashboard.model.DashboardSnapshot;
import com.emias.dashboard.model.FacilityRating;
import com.emias.dashboard.model.PatientRecord;
import com.emias.dashboard.model.ScreeningStats;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Прогревает кэш дашборда при старте приложения в фоновом потоке,
 * чтобы первое открытие страницы было мгновенным.
 */
@Service
public class DashboardWarmupService {

    private static final Logger log = LoggerFactory.getLogger(DashboardWarmupService.class);

    private final ReportService          reportService;
    private final DiagramService         diagramService;
    private final SettingsService        settingsService;
    private final FacilityMappingService facilityMappingService;
    private final FacilityPlanService    facilityPlanService;
    private final DashboardCacheService  dashboardCache;

    public DashboardWarmupService(ReportService reportService,
                                  DiagramService diagramService,
                                  SettingsService settingsService,
                                  FacilityMappingService facilityMappingService,
                                  FacilityPlanService facilityPlanService,
                                  DashboardCacheService dashboardCache) {
        this.reportService          = reportService;
        this.diagramService         = diagramService;
        this.settingsService        = settingsService;
        this.facilityMappingService = facilityMappingService;
        this.facilityPlanService    = facilityPlanService;
        this.dashboardCache         = dashboardCache;
    }

    @PostConstruct
    @Async
    public void warmUp() {
        try {
            List<String> dates = reportService.getUploadedDates();
            if (dates.isEmpty()) return;

            long annualPlan = settingsService.getConfig().getAnnualPlan();
            String cacheKey = dates + "|" + annualPlan;

            if (dashboardCache.get(cacheKey) != null) return;

            log.info("Прогрев кэша дашборда...");

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

            String lastUploadDate = formatMonth(dates.get(0));

            DashboardSnapshot snap = new DashboardSnapshot(
                    stats,
                    diagramService.buildScreeningAgeCounts(records),
                    diagramService.buildMonthlyChartData(records, annualPlan),
                    diagramService.buildAgeDiagram(singleFile),
                    rating,
                    diagramService.buildConclusions(records, stats, rating, annualPlan, dates.get(0)),
                    lastUploadDate
            );
            dashboardCache.put(cacheKey, snap);
            log.info("Кэш дашборда готов.");
        } catch (Exception e) {
            log.warn("Прогрев кэша не удался: {}", e.getMessage());
        }
    }

    private String formatMonth(String date) {
        String[] months = {"Январь","Февраль","Март","Апрель","Май","Июнь",
                           "Июль","Август","Сентябрь","Октябрь","Ноябрь","Декабрь"};
        String[] parts = date.split("-");
        int month = Integer.parseInt(parts[1]) - 1;
        return months[month] + " " + parts[0];
    }
}
