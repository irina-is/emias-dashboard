package com.emias.dashboard.model;

import java.util.Map;

public class PcrTerritoryRow {

    public final String              territory;
    public final long                total;
    public final long                positive;
    public final long                negative;
    public final long                noInfo;
    /** год → количество всех ПЦР записей за этот год */
    public final Map<String, Long>   byYear;

    public PcrTerritoryRow(String territory, long total, long positive, long negative,
                           long noInfo, Map<String, Long> byYear) {
        this.territory = territory;
        this.total     = total;
        this.positive  = positive;
        this.negative  = negative;
        this.noInfo    = noInfo;
        this.byYear    = byYear;
    }

    public int positivePct() {
        return total > 0 ? (int) (positive * 100 / total) : 0;
    }
}
