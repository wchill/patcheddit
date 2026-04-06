/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.shared.fixes.feed;

import androidx.annotation.NonNull;

import java.io.IOException;

import app.morphe.extension.shared.requests.PatchedditInterceptor;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;


public class RAllPatch extends PatchedditInterceptor {
    @Override
    public boolean isPatchIncluded() {
        // Overridden by patch.
        return false;
    }

    @NonNull
    @Override
    public Response doIntercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        HttpUrl url = request.url();
        if (!url.host().endsWith("reddit.com")) {
            return chain.proceed(request);
        }

        if (!url.encodedPath().contains("/r/all")) {
            return chain.proceed(request);
        }

        if (url.encodedPath().endsWith("/.json")) {
            return chain.proceed(request);
        }

        Request newRequest = request.newBuilder()
                .url(url.newBuilder().addEncodedPathSegment(".json").build())
                .build();
        return chain.proceed(newRequest);
    }
}
