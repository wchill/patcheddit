/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.shared.fixes.feed;

import androidx.annotation.NonNull;

import java.io.IOException;

import app.morphe.extension.shared.requests.RedditRouteResponseShim;
import okhttp3.Request;
import okhttp3.Response;


public class RAllPatch extends RedditRouteResponseShim {
    public RAllPatch() {
        super(request -> {
            String path = request.url().encodedPath();
            return path.contains("/r/all") && !path.endsWith("/.json");
        });
    }

    @Override
    public boolean isPatchIncluded() {
        // Overridden by patch.
        return false;
    }

    @NonNull
    @Override
    protected Response shimResponse(@NonNull Chain chain, @NonNull Request request) throws IOException {
        Request newRequest = request.newBuilder()
                .url(request.url().newBuilder().addEncodedPathSegment(".json").build())
                .build();
        return chain.proceed(newRequest);
    }
}
