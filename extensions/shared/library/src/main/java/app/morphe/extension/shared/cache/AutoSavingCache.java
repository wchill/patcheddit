/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Modified for the Breal undelete audit: lazy, fail-open database and fewer writes (2026-10-13).
 * Modifications Copyright 2026 brealorg.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 */

package app.morphe.extension.shared.cache;

import android.util.LruCache;

import java.util.Optional;

import app.morphe.extension.shared.debug.LoggingUtils;

/**
 * Memory LRU backed by a lazily opened Room database. Storage failures leave the in-memory cache usable.
 */
public class AutoSavingCache {
    // Eviction counts rows, so it only needs to run now and then.
    private static final int EVICT_EVERY_PUTS = 32;

    private final String cacheName;
    private final int maxSize;
    private final LruCache<String, String> memoryCache;
    private volatile CacheDao backingStore;
    private volatile boolean storeFailed;
    private int putsSinceEviction = EVICT_EVERY_PUTS;

    public AutoSavingCache(String name, int maxSize) {
        this.cacheName = name;
        this.maxSize = maxSize;
        this.memoryCache = new LruCache<>(maxSize);
    }

    private CacheDao store() {
        if (storeFailed) {
            return null;
        }
        CacheDao store = backingStore;
        if (store != null) return store;
        synchronized (this) {
            if (storeFailed) return null;
            if (backingStore == null) {
                try {
                    backingStore = CacheDatabase.getInstance().cacheDao();
                } catch (RuntimeException | LinkageError failure) {
                    disableStore(failure);
                }
            }
            return backingStore;
        }
    }

    private void disableStore(Throwable failure) {
        storeFailed = true;
        LoggingUtils.logInfo(false, () -> "morphe_cache_memory_only: " + cacheName
                + " " + failure.getClass().getSimpleName());
    }

    public Optional<String> get(String key) {
        // Memory hits do not touch the database; recency there only matters for eviction,
        // and updating it on every comment of every thread meant a SQLite write per lookup.
        String memoryValue = memoryCache.get(key);
        if (memoryValue != null) {
            return Optional.of(memoryValue);
        }

        CacheDao store = store();
        if (store == null) {
            return Optional.empty();
        }
        try {
            String storedValue = store.get(cacheName, key);
            if (storedValue != null) {
                memoryCache.put(key, storedValue);
                store.updateLastAccessed(cacheName, key, System.currentTimeMillis());
                return Optional.of(storedValue);
            }
        } catch (RuntimeException | LinkageError failure) {
            disableStore(failure);
        }
        return Optional.empty();
    }

    public void put(String key, String value) {
        if (value.equals(memoryCache.get(key))) {
            return; // Unchanged; skip the database write.
        }
        memoryCache.put(key, value);

        CacheDao store = store();
        if (store == null) {
            return;
        }
        try {
            store.put(new CacheEntry(cacheName, key, value, System.currentTimeMillis()));
            synchronized (this) {
                if (++putsSinceEviction < EVICT_EVERY_PUTS) {
                    return;
                }
                putsSinceEviction = 0;
            }
            int excess = store.getCount(cacheName) - maxSize;
            if (excess > 0) store.evictOldest(cacheName, excess);
        } catch (RuntimeException | LinkageError failure) {
            disableStore(failure);
        }
    }
}
