/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.shared.fixes.feed;

import androidx.annotation.NonNull;

import java.io.IOException;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


public class RAllPatch implements Interceptor {
    protected static RAllPatch INSTANCE = new RAllPatch();

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        HttpUrl url = request.url();

        if (!url.host().endsWith("reddit.com") || !url.encodedPath().equals("/r/all")) {
            return chain.proceed(request);
        }

        Request newRequest = request.newBuilder()
                .url(url.newBuilder().encodedPath("/r/all.json").build())
                .build();
        return chain.proceed(newRequest);
    }

    public static OkHttpClient createClient() {
        return new OkHttpClient.Builder().addInterceptor(INSTANCE).build();
    }

    public static OkHttpClient install(OkHttpClient.Builder builder) {
        return builder.addInterceptor(INSTANCE).build();
    }
}
