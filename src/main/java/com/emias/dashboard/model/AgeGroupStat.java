package com.emias.dashboard.model;

public class AgeGroupStat {

    private final String label;
    private final long   count;
    private final String percentText; // null для карточки «Всего»

    public AgeGroupStat(String label, long count, String percentText) {
        this.label       = label;
        this.count       = count;
        this.percentText = percentText;
    }

    public String  getLabel()       { return label; }
    public long    getCount()       { return count; }
    public String  getPercentText() { return percentText; }
    public boolean hasPercent()     { return percentText != null; }
}
