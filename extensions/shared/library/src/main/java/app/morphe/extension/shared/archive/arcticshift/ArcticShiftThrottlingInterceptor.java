/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.shared.archive.arcticshift;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

import app.morphe.extension.vendored.RateLimiter;
import okhttp3.Interceptor;
import okhttp3.Response;

public class ArcticShiftThrottlingInterceptor implements Interceptor {
    private static final RateLimiter limiter = RateLimiter.create(10);

    @NotNull
    @Override
    public Response intercept(@NotNull Chain chain) throws IOException {
        if (ArcticShiftSettings.isApiUrl(chain.request().url(), ArcticShiftSettings.getBaseUrl())) {
            limiter.acquire();
        }
        return chain.proceed(chain.request());
    }
}
