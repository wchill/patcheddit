package app.morphe.extension.boostforreddit.http;

import android.util.LruCache;

import java.util.Optional;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import app.morphe.extension.boostforreddit.utils.CacheUtils;
import app.morphe.extension.shared.Utils;

public class AutoSavingCache {
    private final String cacheName;
    // TODO: Improve the caching mechanism so that it doesn't involve serializing large JSON blobs.
    private final LruCache<String, String> cache;
    private final Object mutex;
    private AtomicBoolean dirty;

    private static final ScheduledThreadPoolExecutor cacheWritebackThreadPool = new ScheduledThreadPoolExecutor(
            1,
            r -> {
                Thread t = new Thread(r);
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            });

    public AutoSavingCache(String name, int maxSize) {
        cacheName = name;
        cache = CacheUtils.readCacheFromDisk(name, maxSize);
        mutex = new Object();
        dirty = new AtomicBoolean(false);

        cacheWritebackThreadPool.scheduleWithFixedDelay(this::writePeriodicallyToDisk, 5, 30, TimeUnit.SECONDS);
    }

    public Optional<String> get(String key) {
        synchronized (mutex) {
            return Optional.ofNullable(cache.get(key));
        }
    }

    public void put(String key, String value) {
        synchronized (mutex) {
            dirty.set(true);
            cache.put(key, value);
        }
    }

    private void writePeriodicallyToDisk() {
        synchronized (mutex) {
            if (dirty.get()) {
                CacheUtils.writeCacheToDisk(cacheName, cache);
                dirty.set(false);
            }
        }
    }
}
