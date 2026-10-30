/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.joey.http;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import app.morphe.extension.shared.fixes.api.RedditInfoResponsePatch;
import app.morphe.extension.shared.fixes.comments.MoreChildrenResponsePatch;
import app.morphe.extension.shared.requests.BaseOkHttpRequestHook;
import okhttp3.Interceptor;

public final class OkHttpRequestHook extends BaseOkHttpRequestHook {
    private OkHttpRequestHook() {}

    public static synchronized void init() {
        if (INSTANCE == null) INSTANCE = new OkHttpRequestHook();
    }

    @Override
    protected List<Interceptor> getInterceptors() {
        return Arrays.asList(new RedditInfoResponsePatch(), new MoreChildrenResponsePatch());
    }

    @Override
    protected List<Interceptor> getNetworkInterceptors() {
        return Collections.emptyList();
    }
}
