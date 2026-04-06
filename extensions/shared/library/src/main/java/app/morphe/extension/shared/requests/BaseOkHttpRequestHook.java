/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.shared.requests;

import java.util.List;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;


/**
 * @noinspection unused
 */
public abstract class BaseOkHttpRequestHook {
    protected static BaseOkHttpRequestHook INSTANCE;

    protected BaseOkHttpRequestHook() {}

    protected abstract List<Interceptor> getInterceptors();
    protected abstract List<Interceptor> getNetworkInterceptors();

    public static OkHttpClient.Builder installInterceptor(OkHttpClient.Builder builder) {
        for (Interceptor interceptor : INSTANCE.getInterceptors()) {
            builder.addInterceptor(interceptor);
        }

        for (Interceptor interceptor : INSTANCE.getNetworkInterceptors()) {
            builder.addNetworkInterceptor(interceptor);
        }

        return builder;
    }
}
