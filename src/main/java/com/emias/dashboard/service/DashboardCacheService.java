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

    private volatile String            cachedKey;
    private volatile DashboardSnapshot cached;

    /** Возвращает кэш, если ключ совпадает, иначе null. */
    public DashboardSnapshot get(String key) {
        if (key != null && key.equals(cachedKey)) {
            return cached;
        }
        return null;
    }

    /** Сохраняет снапшот под указанным ключом. */
    public void put(String key, DashboardSnapshot snapshot) {
        cached    = snapshot;
        cachedKey = key;
    }

    /** Инвалидирует кэш при изменении данных. */
    public void invalidate() {
        cachedKey = null;
        cached    = null;
    }
}
