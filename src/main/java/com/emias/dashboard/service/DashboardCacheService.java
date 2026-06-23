package com.emias.dashboard.service;

import com.emias.dashboard.model.DashboardSnapshot;
import org.springframework.stereotype.Component;

/**
 * Хранит последний вычисленный снапшот дашборда скрининга.
 * Сбрасывается при загрузке или удалении файла — тогда
 * следующий запрос пересчитывает данные и кладёт новый снапшот.
 */
@Component
public class DashboardCacheService {

    private record Entry(String key, DashboardSnapshot snapshot) {}

    private volatile Entry entry;

    /** Возвращает кэш, если ключ совпадает, иначе null. */
    public DashboardSnapshot get(String key) {
        Entry e = entry;
        return (e != null && e.key().equals(key)) ? e.snapshot() : null;
    }

    /** Сохраняет снапшот под указанным ключом. */
    public void put(String key, DashboardSnapshot snapshot) {
        entry = new Entry(key, snapshot);
    }

    /** Инвалидирует кэш при изменении данных. */
    public void invalidate() {
        entry = null;
    }
}
