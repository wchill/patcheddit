/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.fixes.links;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import app.morphe.extension.shared.requests.PatchedditInterceptor;
import app.morphe.extension.shared.requests.RedditRouteResponseShim;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class EscapedUrlsPatch extends PatchedditInterceptor {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean isPatchIncluded() {
        // Overridden by patch.
        return false;
    }

    @NonNull
    @Override
    protected Response doIntercept(@NonNull Chain chain) throws IOException {
        Response response = chain.proceed(chain.request());
        if (!RedditRouteResponseShim.isRedditHost(response.request().url().host())
                || !response.isSuccessful()) return response;
        String encoding = response.header("Content-Encoding");
        if (encoding != null && !encoding.equalsIgnoreCase("identity")) return response;
        ResponseBody body = response.body();
        MediaType type = body == null ? null : body.contentType();
        if (type == null || !isJson(type.toString()) || body.contentLength() > EscapedUrls.MAX_BODY_BYTES) {
            return response;
        }
        byte[] bytes;
        try (ResponseBody peek = response.peekBody(EscapedUrls.MAX_BODY_BYTES + 1)) {
            if (peek.contentLength() > EscapedUrls.MAX_BODY_BYTES) return response;
            bytes = peek.bytes();
        }
        String original;
        try (ResponseBody copy = ResponseBody.create(bytes, type)) {
            original = copy.string();
        }
        String repaired = EscapedUrls.repairJson(original);
        if (original.equals(repaired)) return response;
        response.close();
        return response.newBuilder()
                .removeHeader("Content-Length")
                .removeHeader("ETag")
                .header("Content-Type", JSON.toString())
                .body(ResponseBody.create(repaired, JSON))
                .build();
    }

    /** BaconReader's Reddit API uses Apache HTTP rather than OkHttp. */
    public static String repairApacheResponse(String body, String url, int code, String contentType) {
        if (body == null || url == null || code < 200 || code >= 300
                || !isJson(contentType) || body.getBytes(StandardCharsets.UTF_8).length > EscapedUrls.MAX_BODY_BYTES) {
            return body;
        }
        HttpUrl parsed = HttpUrl.parse(url);
        if (parsed == null || !RedditRouteResponseShim.isRedditHost(parsed.host())) return body;
        return EscapedUrls.repairJson(body);
    }

    private static boolean isJson(String type) {
        if (type == null) return false;
        String mime = type.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return mime.equals("application/json") || mime.endsWith("+json");
    }
}
