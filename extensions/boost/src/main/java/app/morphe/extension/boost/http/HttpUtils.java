/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Modifications Copyright 2026 brealorg.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 */

package app.morphe.extension.boost.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import app.morphe.extension.boost.http.arcticshift.ArcticShiftThrottlingInterceptor;
import app.morphe.extension.boost.http.wayback.WaybackThrottlingInterceptor;
import app.morphe.extension.boost.utils.CacheUtils;
import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class HttpUtils {
    private static final OkHttpClient httpClient;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Map<Long, OkHttpClient> timeoutClients = new ConcurrentHashMap<>();
    private static final Map<Long, OkHttpClient> streamingTimeoutClients = new ConcurrentHashMap<>();

    static {
        File cacheDir = CacheUtils.getHttpCacheDir();
        // Archive responses only; 2 GB filled phones with old Wayback media. OkHttp trims an
        // existing larger cache to this size.
        Cache cache = new Cache(cacheDir, 200L * 1024L * 1024L);
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30L, TimeUnit.SECONDS)
                .readTimeout(30L, TimeUnit.SECONDS)
                .cache(cache)
                .fastFallback(true)
                .addNetworkInterceptor(new WaybackThrottlingInterceptor())
                .addNetworkInterceptor(new ArcticShiftThrottlingInterceptor())
                .build();
    }

    public static JsonNode getJson(String url) throws IOException {
        try (Response response = get(url)) {
            String json = response.body().string();
            return objectMapper.readTree(json);
        }
    }

    public static JsonNode getJsonFromString(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Fetches JSON with an overall time limit. Unlike {@link #getJson(String)}, HTTP errors
     * throw instead of handing an error page to the JSON parser.
     */
    public static JsonNode getJson(String url, long callTimeoutMillis) throws IOException {
        return getJson(url, callTimeoutMillis, false);
    }

    public static JsonNode getJson(String url, long callTimeoutMillis, boolean forceNetwork) throws IOException {
        Request.Builder builder = new Request.Builder()
                .get()
                .url(url);
        if (forceNetwork) builder.cacheControl(CacheControl.FORCE_NETWORK);
        Request request = builder.build();
        try (Response response = clientWithCallTimeout(callTimeoutMillis).newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new HttpStatusException(response.code(),
                        response.body() == null ? "" : response.body().string());
            }
            return objectMapper.readTree(response.body().string());
        }
    }

    /** A non-2xx answer; callers can inspect services that report errors in the body. */
    public static final class HttpStatusException extends IOException {
        public final int code;
        public final String body;

        public HttpStatusException(int code, String body) {
            super("HTTP " + code);
            this.code = code;
            this.body = body == null ? "" : body;
        }
    }

    // Boost obfuscates okio, so Call.timeout() (returning okio.Timeout) cannot be linked at runtime.
    // Builder.callTimeout only uses okhttp and JDK types. Derived clients share the pool and cache.
    private static OkHttpClient clientWithCallTimeout(long callTimeoutMillis) {
        return timeoutClients.computeIfAbsent(callTimeoutMillis, millis -> httpClient.newBuilder()
                .callTimeout(millis, TimeUnit.MILLISECONDS)
                .build());
    }

    /**
     * GET whose connect and per-read waits are bounded, without a limit on the whole call,
     * so the body can still be streamed to Glide or Boost after this returns.
     */
    public static Response get(String url, long connectAndReadTimeoutMillis) throws IOException {
        Request request = new Request.Builder()
                .get()
                .url(url)
                .build();
        OkHttpClient client = streamingTimeoutClients.computeIfAbsent(connectAndReadTimeoutMillis,
                millis -> httpClient.newBuilder()
                        .connectTimeout(millis, TimeUnit.MILLISECONDS)
                        .readTimeout(millis, TimeUnit.MILLISECONDS)
                        .build());
        return client.newCall(request).execute();
    }

    public static String getStringFromJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static ResponseBody getResponseBodyFromJson(JsonNode node) throws JsonProcessingException {
        return getResponseBodyFromString(objectMapper.writeValueAsString(node));
    }

    public static ResponseBody getResponseBodyFromString(String s) {
        return ResponseBody.create(s, MediaType.get("application/json"));
    }

    public static Response makeJsonResponse(Request request, JsonNode node) throws JsonProcessingException {
        return makeJsonResponse(request, objectMapper.writeValueAsString(node));
    }

    public static Response makeJsonResponse(Request request, String s) {
        return new Response.Builder()
                .message("OK")
                .code(HttpURLConnection.HTTP_OK)
                .protocol(Protocol.HTTP_1_1)
                .request(request)
                .header("Content-Type", "application/json")
                .body(HttpUtils.getResponseBodyFromString(s))
                .build();
    }

    public static Response get(String url) throws IOException {
        Request request = new Request.Builder()
                .get()
                .url(url)
                .build();
        return httpClient.newCall(request).execute();
    }

    public static boolean isPresent(String url) throws IOException {
        Request request = new Request.Builder()
                .head()
                .url(url)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            return response.isSuccessful();
        }
    }

    public static Response create404(Request request) {
        return new Response.Builder()
                .code(HttpURLConnection.HTTP_NOT_FOUND)
                .protocol(Protocol.HTTP_1_1)
                .message("Not found")
                .request(request)
                .build();
    }

    public static Response create503(Request request) {
        return new Response.Builder()
                .code(HttpURLConnection.HTTP_UNAVAILABLE)
                .protocol(Protocol.HTTP_1_1)
                .message("Service unavailable")
                .request(request)
                .build();
    }

    public static URL createUrl(String href) {
        try {
            URI uri = new URI(href);
            String path = uri.getPath();
            if (uri.getQuery() != null) {
                path += "?" + uri.getQuery();
            }
            if (uri.getFragment() != null) {
                path += "#" + uri.getFragment();
            }
            return new URL(uri.getScheme(), uri.getRawAuthority(), uri.getPort(), path, new NoOpUrlStreamHandler());
        } catch (URISyntaxException | MalformedURLException e) {
            throw new IllegalArgumentException("Malformed URL", e);
        }
    }
}
