package app.morphe.extension.boostforreddit.http.arcticshift;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

import app.morphe.extension.boostforreddit.utils.CompatibleRateLimiter;
import okhttp3.Interceptor;
import okhttp3.Response;

public class ArcticShiftThrottlingInterceptor implements Interceptor {
    private static final CompatibleRateLimiter limiter = CompatibleRateLimiter.create(10);

    @NotNull
    @Override
    public Response intercept(@NotNull Chain chain) throws IOException {
        if (chain.request().url().host().contains("arctic-shift.photon-reddit.com")) {
            limiter.acquire();
        }
        return chain.proceed(chain.request());
    }
}
