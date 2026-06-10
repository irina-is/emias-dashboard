package com.emias.dashboard.model;

import java.util.List;

public class DashboardSnapshot {

    public final ScreeningStats      stats;
    public final List<AgeGroupStat>  ageCounts;
    public final MonthlyChartData    monthly;
    public final AgeDiagram          diagram;
    public final List<FacilityRating> rating;
    public final Conclusions         conclusions;
    public final String              lastUploadDate;

    public DashboardSnapshot(ScreeningStats stats,
                             List<AgeGroupStat> ageCounts,
                             MonthlyChartData monthly,
                             AgeDiagram diagram,
                             List<FacilityRating> rating,
                             Conclusions conclusions,
                             String lastUploadDate) {
        this.stats          = stats;
        this.ageCounts      = ageCounts;
        this.monthly        = monthly;
        this.diagram        = diagram;
        this.rating         = rating;
        this.conclusions    = conclusions;
        this.lastUploadDate = lastUploadDate;
    }
}
