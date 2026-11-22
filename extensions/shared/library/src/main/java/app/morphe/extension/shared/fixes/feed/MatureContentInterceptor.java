/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.fixes.feed;

import java.io.IOException;

import app.morphe.extension.shared.settings.MatureContentLock;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class MatureContentInterceptor implements Interceptor {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        HttpUrl url = request.url();
        if (MatureContentLock.isLocked() && MatureContentFilter.isRedditHost(url.host())) {
            String path = MatureContentFilter.path(url.encodedPath());
            if (request.method().equals("GET") && MatureContentFilter.isSearch(path)) {
                request = request.newBuilder().url(url.newBuilder()
                        .setQueryParameter("include_over_18", "false").build()).build();
            } else if (MatureContentFilter.isCommunityNameSearch(path) && request.body() instanceof FormBody) {
                FormBody old = (FormBody) request.body();
                FormBody.Builder form = new FormBody.Builder();
                for (int i = 0; i < old.size(); i++) {
                    if (!old.name(i).equals("include_over_18")) form.addEncoded(old.encodedName(i), old.encodedValue(i));
                }
                request = request.newBuilder().url(url.newBuilder().removeAllQueryParameters("include_over_18").build())
                        .method(request.method(), form.add("include_over_18", "false").build()).build();
            }
        }
        Response response = chain.proceed(request);
        if (!MatureContentLock.isLocked() || !response.isSuccessful()
                || !MatureContentFilter.isRedditHost(response.request().url().host())) return response;
        ResponseBody body = response.body();
        if (body == null || body.contentType() == null || !MatureContentFilter.isJson(body.contentType().toString())) {
            return response;
        }
        String path = MatureContentFilter.path(response.request().url().encodedPath());
        String encoding = response.header("Content-Encoding");
        try (Response original = response) {
            if (encoding != null && !encoding.equalsIgnoreCase("identity")) {
                throw new IOException("Could not decode the Reddit response for content filtering.");
            }
            try (ResponseBody peek = original.peekBody(MatureContentFilter.MAX_BODY + 1L)) {
                if (peek.contentLength() > MatureContentFilter.MAX_BODY) {
                    throw new IOException("Reddit response exceeds the content filter's size limit.");
                }
                String filtered = MatureContentFilter.filter(peek.string(), path, requestedSafeNames(original.request()));
                return original.newBuilder().removeHeader("Content-Length").removeHeader("ETag")
                        .header("Content-Type", JSON.toString()).body(ResponseBody.create(filtered, JSON)).build();
            }
        }
    }

    private static boolean requestedSafeNames(Request request) {
        if (request.method().equals("GET")) return "false".equals(request.url().queryParameter("include_over_18"));
        if (request.body() instanceof FormBody) {
            FormBody form = (FormBody) request.body();
            for (int i = 0; i < form.size(); i++) {
                if (form.name(i).equals("include_over_18")) return form.value(i).equals("false");
            }
        }
        return false;
    }
}
