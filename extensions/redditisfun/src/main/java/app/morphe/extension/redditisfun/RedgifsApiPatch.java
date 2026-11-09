/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.redditisfun;

import android.app.Activity;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class RedgifsApiPatch implements Interceptor {
    private static final String HOST = "api.redgifs.com";
    private static final long REFRESH_MARGIN_SECONDS = 60;
    private static final long MAX_TOKEN_LIFETIME_SECONDS = 24 * 60 * 60;
    private static final int MAX_TOKEN_BYTES = 8 * 1024;
    private static final long MAX_AUTH_BYTES = 16 * 1024;
    private static final long MAX_METADATA_BYTES = 2L * 1024 * 1024;
    private static final Semaphore LOADS = new Semaphore(4);
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9]{1,128}");
    private static final Pattern JWT = Pattern.compile("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final Map<View, String> REPORTED = new WeakHashMap<>();
    private static final Handler UI = new Handler(Looper.getMainLooper());

    public static boolean isIncluded() {
        return false; // Bound to the included patch's Morphe switch.
    }

    public static boolean useSystemTrust(String host) {
        return isIncluded() && HOST.equals(host);
    }

    public static String validCachedToken(String token) {
        if (!isIncluded() || token == null || token.isEmpty()) return token;
        try {
            return expiresAt(token) > System.currentTimeMillis() / 1000 + REFRESH_MARGIN_SECONDS ? token : null;
        } catch (IOException failure) {
            Log.w("MorpheRedgifs", "Discarding invalid cached anonymous authorization");
            return null;
        }
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();
        if (!isIncluded()) return chain.proceed(original);
        HttpUrl url = original.url();
        if (!apiUrl(url)) return chain.proceed(original);
        boolean authorization = original.method().equals("POST") && url.encodedPath().equals("/v2/oauth/client");
        boolean metadata = original.method().equals("GET") && videoId(url.pathSegments()) != null;
        if (!authorization && !metadata) return chain.proceed(original);
        String agent = original.header("User-Agent");
        if (agent == null || agent.isEmpty()) throw new IOException("Redgifs request has no user agent");
        Headers.Builder headers = new Headers.Builder().add("User-Agent", agent)
                .add("Accept", "application/json").add("Accept-Encoding", "identity");
        if (metadata) {
            String bearer = original.header("Authorization");
            if (bearer == null || !bearer.startsWith("Bearer ")
                    || expiresAt(bearer.substring(7)) <= System.currentTimeMillis() / 1000) {
                throw new IOException("Redgifs anonymous authorization is unavailable; reopen the video to retry");
            }
            headers.add("Authorization", bearer);
        } else {
            url = url.newBuilder().encodedPath("/v2/auth/temporary").query(null).fragment(null).build();
            headers.add("Cache-Control", "no-cache, no-store");
        }
        Request request = original.newBuilder().url(url).headers(headers.build()).get().build();
        long limit = authorization ? MAX_AUTH_BYTES : MAX_METADATA_BYTES;
        MediaMetadata.Load load = new MediaMetadata.Load(chain.call(), LOADS, 15_000, limit, "Redgifs");
        boolean handedOff = false;
        try {
            Response response = chain.withConnectTimeout(timeout(chain.connectTimeoutMillis()), TimeUnit.MILLISECONDS)
                    .withReadTimeout(timeout(chain.readTimeoutMillis()), TimeUnit.MILLISECONDS)
                    .withWriteTimeout(timeout(chain.writeTimeoutMillis()), TimeUnit.MILLISECONDS)
                    .proceed(request);
            ResponseBody body = response.body();
            if (body == null) throw new IOException("Redgifs response has no body");
            if (!apiUrl(response.request().url())
                    || !response.request().url().encodedPath().equals(url.encodedPath())) {
                response.close();
                throw new IOException("Redgifs redirected to an unsupported destination");
            }
            if (body.contentLength() > limit) {
                response.close();
                throw new IOException("Redgifs metadata exceeds the size limit");
            }
            response = response.newBuilder().body(load.bound(body)).build();
            if (authorization) return nativeAuthorization(response);
            handedOff = true;
            return response;
        } catch (IOException failure) {
            throw load.failure(failure);
        } finally {
            if (!handedOff) load.finish();
        }
    }

    private static Response nativeAuthorization(Response response) throws IOException {
        try (Response closeable = response) {
            if (!closeable.isSuccessful()) {
                throw new IOException("Redgifs authorization failed (HTTP " + closeable.code() + ")");
            }
            ResponseBody body = closeable.body();
            MediaType type = body.contentType();
            if (type == null || !type.type().equals("application")
                    || !(type.subtype().equals("json") || type.subtype().endsWith("+json"))) {
                throw new IOException("Redgifs authorization response is not JSON");
            }
            JSONObject data = object(body.string());
            Object value = data.opt("token");
            if (!(value instanceof String)) throw new IOException("Redgifs authorization response has no token");
            String token = (String) value;
            long lifetime = Math.min(MAX_TOKEN_LIFETIME_SECONDS,
                    expiresAt(token) - System.currentTimeMillis() / 1000) - REFRESH_MARGIN_SECONDS;
            if (lifetime <= 0) throw new IOException("Redgifs temporary authorization has expired");
            try {
                String json = new JSONObject().put("access_token", token).put("expires_in", lifetime)
                        .put("scope", "read").put("token_type", "Bearer").toString();
                return closeable.newBuilder().removeHeader("Content-Length").removeHeader("Content-Encoding")
                        .removeHeader("Transfer-Encoding").removeHeader("ETag").removeHeader("Last-Modified")
                        .header("Content-Type", JSON.toString()).header("Cache-Control", "no-store")
                        .body(ResponseBody.create(json, JSON)).build();
            } catch (JSONException failure) {
                throw new IOException("Could not adapt Redgifs authorization for the native loader");
            }
        }
    }

    private static long expiresAt(String token) throws IOException {
        if (token.length() > MAX_TOKEN_BYTES || !JWT.matcher(token).matches()) {
            throw new IOException("Redgifs returned invalid temporary authorization");
        }
        int first = token.indexOf('.');
        int second = token.indexOf('.', first + 1);
        try {
            byte[] payload = Base64.decode(token.substring(first + 1, second),
                    Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
            if (payload.length > 4096) throw new IOException("Redgifs authorization payload exceeds the size limit");
            Object expiration = object(new String(payload, StandardCharsets.UTF_8)).opt("exp");
            if (!(expiration instanceof Number)) throw new IOException("Redgifs authorization has no expiration");
            Number value = (Number) expiration;
            long seconds = value.longValue();
            if (seconds <= 0 || seconds > Long.MAX_VALUE / 1000 || value.doubleValue() != (double) seconds) {
                throw new IOException("Redgifs authorization has an invalid expiration");
            }
            return seconds;
        } catch (IllegalArgumentException failure) {
            throw new IOException("Redgifs returned malformed temporary authorization");
        }
    }

    private static JSONObject object(String text) throws IOException {
        MediaMetadata.checkJsonDepth(text, 8, "Redgifs authorization");
        try {
            JSONTokener tokener = new JSONTokener(text);
            Object value = tokener.nextValue();
            if (!(value instanceof JSONObject) || tokener.nextClean() != 0) {
                throw new IOException("Redgifs authorization is not a JSON object");
            }
            return (JSONObject) value;
        } catch (JSONException failure) {
            // Token bodies must not become exception messages or diagnostic causes.
            throw new IOException("Could not parse Redgifs temporary authorization");
        }
    }

    private static boolean apiUrl(HttpUrl url) {
        return url.isHttps() && url.host().equals(HOST) && url.port() == 443
                && url.username().isEmpty() && url.password().isEmpty();
    }

    private static String videoId(List<String> segments) {
        return segments.size() == 3 && segments.get(0).equals("v2") && segments.get(1).equals("gifs")
                && ID.matcher(segments.get(2)).matches() ? segments.get(2) : null;
    }

    private static int timeout(int nativeTimeout) {
        return nativeTimeout == 0 ? 10_000 : Math.min(nativeTimeout, 10_000);
    }

    public static boolean handleMissing(Object callback, Uri request, int status) {
        if (!isIncluded() || status != 404 || request == null
                || !HOST.equals(request.getHost()) || !"https".equals(request.getScheme())
                || videoId(request.getPathSegments()) == null) return false;
        Log.d("MorpheRedgifs", "Handling a missing native video");
        WeakReference<Object> viewer = new WeakReference<>(callback);
        // A fast response can beat the fragment's layout and activity transition.
        long deadline = SystemClock.uptimeMillis() + 1000;
        UI.post(() -> dispatchMissing(viewer, request, deadline));
        return true;
    }

    private static void dispatchMissing(WeakReference<Object> viewer, Uri request, long deadline) {
        Object current = viewer.get();
        if (current == null || !isIncluded() || missingViewer(current, request)) return;
        if (SystemClock.uptimeMillis() < deadline) {
            UI.postDelayed(() -> dispatchMissing(viewer, request, deadline), 50);
        } else {
            Log.d("MorpheRedgifs", "Unavailable-video viewer did not become active");
        }
    }

    public static boolean missingViewer(Object callback, Uri request) {
        throw new IllegalStateException("Native Redgifs viewer was not bound");
    }

    @SuppressWarnings("deprecation")
    public static boolean showUnavailable(Activity activity, View view, Uri active, Uri request) {
        // The native player hides its loading pane until media is ready.
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || view == null) return true;
        if (!view.isAttachedToWindow() || !view.hasWindowFocus()) return false;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Log.w("MorpheRedgifs", "Skipped unavailable-video navigation outside the UI thread");
            return true;
        }
        if (active == null || !("redgifs.com".equalsIgnoreCase(active.getHost())
                || "www.redgifs.com".equalsIgnoreCase(active.getHost()))) {
            Log.d("MorpheRedgifs", "Unavailable-video source changed");
            return true;
        }
        List<String> path = active.getPathSegments();
        String id = videoId(request.getPathSegments());
        if (path.size() != 2 || !(path.get(0).equals("watch") || path.get(0).equals("ifr"))
                || id == null || !id.equalsIgnoreCase(path.get(1))) {
            Log.d("MorpheRedgifs", "Unavailable-video media identity changed");
            return true;
        }
        if (id.equalsIgnoreCase(REPORTED.get(view))) return true;
        if (REPORTED.size() >= 8) {
            Iterator<View> iterator = REPORTED.keySet().iterator();
            if (iterator.hasNext()) REPORTED.remove(iterator.next());
        }
        REPORTED.put(view, id);
        Toast.makeText(activity, "This Redgifs video is no longer available.", Toast.LENGTH_LONG).show();
        activity.onBackPressed();
        return true;
    }
}
