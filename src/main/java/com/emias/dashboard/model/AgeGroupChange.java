package com.emias.dashboard.model;

/**
 * Изменение количества пациентов в одной возрастной группе между двумя неделями.
 */
public class AgeGroupChange {

    private String label;         // название группы, например "до 18"
    private long previousCount;   // количество в предыдущей неделе
    private long currentCount;    // количество в текущей неделе

    public AgeGroupChange(String label, long previousCount, long currentCount) {
        this.label         = label;
        this.previousCount = previousCount;
        this.currentCount  = currentCount;
    }

    public String getLabel()         { return label; }
    public long getPreviousCount()   { return previousCount; }
    public long getCurrentCount()    { return currentCount; }

    // Текст изменения: "+50%", "-20%", "—" (если оба нуля), "новые" (если раньше не было)
    public String getChangeText() {
        if (previousCount == 0 && currentCount == 0) {
            return "—";
        }
        if (previousCount == 0) {
            return "новые";
        }
        double percent = (double) (currentCount - previousCount) / previousCount * 100;
        String sign = percent > 0 ? "+" : "";
        return sign + Math.round(percent) + "%";
    }

    // CSS-класс Bootstrap для цвета: зелёный — рост, красный — убыль, серый — без изменений
    public String getChangeClass() {
        if (previousCount == 0 && currentCount == 0) return "text-muted";
        if (previousCount == 0)        return "text-success fw-bold";
        if (currentCount > previousCount) return "text-success fw-bold";
        if (currentCount < previousCount) return "text-danger fw-bold";
        return "text-muted";
    }
}
