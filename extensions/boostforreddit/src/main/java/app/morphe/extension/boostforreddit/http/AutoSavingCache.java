/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.boostforreddit.http;

import android.util.LruCache;

import java.util.Optional;

import app.morphe.extension.boostforreddit.http.cache.CacheDao;
import app.morphe.extension.boostforreddit.http.cache.CacheDatabase;
import app.morphe.extension.boostforreddit.http.cache.CacheEntry;

public class AutoSavingCache {
    private final String cacheName;
    private final int maxSize;
    private final LruCache<String, String> memoryCache;
    private final CacheDao cacheDao;

    public AutoSavingCache(String name, int maxSize) {
        this.cacheName = name;
        this.maxSize = maxSize;
        this.memoryCache = new LruCache<>(maxSize);
        this.cacheDao = CacheDatabase.getInstance().cacheDao();
    }

    public Optional<String> get(String key) {
        // Check in-memory cache first
        String memoryValue = memoryCache.get(key);
        if (memoryValue != null) {
            cacheDao.updateLastAccessed(cacheName, key, System.currentTimeMillis());
            return Optional.of(memoryValue);
        }

        // Fall back to database
        String dbValue = cacheDao.get(cacheName, key);
        if (dbValue != null) {
            memoryCache.put(key, dbValue);
            cacheDao.updateLastAccessed(cacheName, key, System.currentTimeMillis());
            return Optional.of(dbValue);
        }

        return Optional.empty();
    }

    public void put(String key, String value) {
        memoryCache.put(key, value);

        long now = System.currentTimeMillis();
        CacheEntry entry = new CacheEntry(cacheName, key, value, now);
        cacheDao.put(entry);

        // Evict oldest entries if over capacity
        int count = cacheDao.getCount(cacheName);
        if (count > maxSize) {
            cacheDao.evictOldest(cacheName, count - maxSize);
        }
    }
}
