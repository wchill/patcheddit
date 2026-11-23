/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Modifications Copyright 2026 brealorg.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 */

package app.morphe.extension.shared.archive.wayback;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import app.morphe.extension.vendored.RateLimiter;
import okhttp3.Interceptor;
import okhttp3.Response;

public class WaybackThrottlingInterceptor implements Interceptor {
    private static final RateLimiter searchLimiter = RateLimiter.create(1);
    private static final RateLimiter retrieveLimiter = RateLimiter.create(30);
    // A long queue means the archive is already being hammered; fail instead of blocking the app.
    private static final long MAX_WAIT_SECONDS = 5L;

    @NotNull
    @Override
    public Response intercept(@NotNull Chain chain) throws IOException {
        String url = chain.request().url().toString();
        RateLimiter limiter = null;
        if (url.contains("web.archive.org/web/timemap")) {
            limiter = searchLimiter;
        } else if (url.contains("web.archive.org/web/")) {
            limiter = retrieveLimiter;
        }
        if (limiter != null && !limiter.tryAcquire(1, MAX_WAIT_SECONDS, TimeUnit.SECONDS)) {
            throw new IOException("Wayback Machine request queue is full");
        }
        return chain.proceed(chain.request());
    }
}
