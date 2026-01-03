package app.morphe.extension.boostforreddit.http;

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
import java.util.concurrent.TimeUnit;

import app.morphe.extension.boostforreddit.http.arcticshift.ArcticShiftThrottlingInterceptor;
import app.morphe.extension.boostforreddit.http.wayback.WaybackThrottlingInterceptor;
import app.morphe.extension.boostforreddit.utils.CacheUtils;
import okhttp3.Cache;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class HttpUtils {
    private static final OkHttpClient httpClient;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        File cacheDir = CacheUtils.getHttpCacheDir();
        Cache cache = new Cache(cacheDir, 2048L * 1024L * 1024L);
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
        return httpClient.newCall(request).execute().isSuccessful();
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
