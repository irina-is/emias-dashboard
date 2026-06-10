package com.emias.dashboard.model;

/**
 * Статистика «Пути пациента» по своду ВГС.
 * Фильтр: только пациенты с датой начала лечения в 2026 году.
 */
public class PathwayStats {

    /** Всего пациентов, начавших лечение в 2026 */
    public final long total;

    /** Всего ПЦР тестов проведено в 2026 (есть любой результат: + или −) */
    public final long pcrTotal;

    /** ПЦР положительный */
    public final long pcrPositive;

    /** ПЦР отрицательный */
    public final long pcrNegative;

    /** Внесены в ВИМИС */
    public final long vimisYes;

    /** Есть заключение МОНИКИ/Центр СПИД */
    public final long monikYes;

    /** Есть назначенная схема лечения */
    public final long schemeAssigned;

    /** Статусы лечения */
    public final long statusQueue;        // в очереди
    public final long statusHospitalized; // госпитализирован
    public final long statusCompleted;    // завершен
    public final long statusInterrupted;  // прервал

    /** УВО12: отрицательный результат (излечение) */
    public final long uvo12Negative;
    /** УВО12: проведено (любой результат) */
    public final long uvo12Done;

    public PathwayStats(long total, long pcrTotal, long pcrPositive, long pcrNegative, long vimisYes, long monikYes,
                        long schemeAssigned,
                        long statusQueue, long statusHospitalized,
                        long statusCompleted, long statusInterrupted,
                        long uvo12Negative, long uvo12Done) {
        this.total              = total;
        this.pcrTotal           = pcrTotal;
        this.pcrPositive        = pcrPositive;
        this.pcrNegative        = pcrNegative;
        this.vimisYes           = vimisYes;
        this.monikYes           = monikYes;
        this.schemeAssigned     = schemeAssigned;
        this.statusQueue        = statusQueue;
        this.statusHospitalized = statusHospitalized;
        this.statusCompleted    = statusCompleted;
        this.statusInterrupted  = statusInterrupted;
        this.uvo12Negative      = uvo12Negative;
        this.uvo12Done          = uvo12Done;
    }

    /** Процент (0–100) от total, защита от деления на 0 */
    public int pct(long val) {
        return total > 0 ? (int) Math.min(100, val * 100 / total) : 0;
    }
}
