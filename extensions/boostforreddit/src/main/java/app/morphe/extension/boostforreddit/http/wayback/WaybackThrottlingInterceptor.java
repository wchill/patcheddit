/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.boostforreddit.http.wayback;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

import app.morphe.extension.boostforreddit.utils.CompatibleRateLimiter;
import okhttp3.Interceptor;
import okhttp3.Response;

public class WaybackThrottlingInterceptor implements Interceptor {
    private static final CompatibleRateLimiter searchLimiter = CompatibleRateLimiter.create(1);
    private static final CompatibleRateLimiter retrieveLimiter = CompatibleRateLimiter.create(30);

    @NotNull
    @Override
    public Response intercept(@NotNull Chain chain) throws IOException {
        String url = chain.request().url().toString();
        if (url.contains("web.archive.org/web/timemap")) {
            searchLimiter.acquire();
        } else if (url.contains("web.archive.org/web/")) {
            retrieveLimiter.acquire();
        }
        return chain.proceed(chain.request());
    }
}
