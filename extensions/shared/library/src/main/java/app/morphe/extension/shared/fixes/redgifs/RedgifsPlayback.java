/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.fixes.redgifs;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.shared.Logger;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class RedgifsPlayback {
    private static final String WATCH = "https://www.redgifs.com/watch/";
    private static final Pattern PAGE = Pattern.compile("/(?:watch|ifr)/([a-z0-9]{1,128})/?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SILENT = Pattern.compile("/([a-z0-9]{1,128})-silent\\.mp4",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CDN = Pattern.compile("(?:thumbs\\d*|media\\d*|i)\\.redgifs\\.com");
    private static final Pattern METADATA = Pattern.compile("/v2/gifs/[a-zA-Z0-9]{1,128}");
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int MAX_METADATA_BYTES = 256 * 1024;

    private RedgifsPlayback() {}

    private static HttpUrl safeUrl(String value) {
        HttpUrl url = value == null ? null : HttpUrl.parse(value);
        return url != null && url.isHttps() && url.port() == 443
                && url.username().isEmpty() && url.password().isEmpty() ? url : null;
    }

    private static String silentId(HttpUrl url) {
        if (url == null || !CDN.matcher(url.host()).matches()) return null;
        Matcher match = SILENT.matcher(url.encodedPath());
        return match.matches() ? match.group(1).toLowerCase(Locale.ROOT) : null;
    }

    public static boolean isSilent(String value) {
        return silentId(safeUrl(value)) != null;
    }

    public static String clipId(String value) {
        HttpUrl url = safeUrl(value);
        if (url == null) return null;
        if (url.host().equals("redgifs.com") || url.host().equals("www.redgifs.com")) {
            Matcher match = PAGE.matcher(url.encodedPath());
            if (match.matches()) return match.group(1).toLowerCase(Locale.ROOT);
        }
        return silentId(url);
    }

    public static String normalizeSourceUrl(String value) {
        String id = clipId(value);
        return id == null ? value : WATCH + id;
    }

    public static boolean shouldBypassCachedSource(String original, String cached) {
        if (clipId(original) == null) return false;
        HttpUrl url = safeUrl(cached);
        return url == null || silentId(url) != null
                || url.host().equals("v.redd.it") || url.host().endsWith(".redd.it");
    }

    public static boolean shouldRefresh(String value) {
        return value == null || value.isEmpty() || isSilent(value);
    }

    public static void logLookupFailure(IOException failure) {
        Logger.printException(() -> "Could not refresh Redgifs playback metadata", failure);
    }

    public static String preferOriginal(String selected, String original) {
        String id = clipId(original);
        return id == null ? normalizeSourceUrl(selected) : WATCH + id;
    }

    static Response preferAudioMetadata(Response response) throws IOException {
        HttpUrl url = response.request().url();
        if (!response.isSuccessful() || !response.request().method().equals("GET") || !url.isHttps()
                || url.port() != 443 || !url.host().equals("api.redgifs.com")
                || !METADATA.matcher(url.encodedPath()).matches()) return response;
        String encoding = response.header("Content-Encoding");
        ResponseBody body = response.body();
        MediaType type = body == null ? null : body.contentType();
        if ((encoding != null && !encoding.equalsIgnoreCase("identity"))
                || type == null || !type.subtype().toLowerCase(Locale.ROOT).endsWith("json")
                || body.contentLength() > MAX_METADATA_BYTES) return response;

        try (ResponseBody peek = response.peekBody(MAX_METADATA_BYTES + 1L)) {
            if (peek.contentLength() > MAX_METADATA_BYTES) return response;
            JSONObject root = new JSONObject(peek.string());
            JSONObject gif = root.optJSONObject("gif");
            JSONObject urls = gif == null ? null : gif.optJSONObject("urls");
            if (urls == null) return response;
            String hd = urls.optString("hd", "");
            String sd = urls.optString("sd", "");
            // Both native viewers try HD first; expose a usable SD stream when HD is missing or silent.
            if (!isCdnVideo(sd) || (isCdnVideo(hd) && (!isSilent(hd) || isSilent(sd)))) return response;
            urls.put("hd", sd);
            Response rewritten = response.newBuilder().removeHeader("Content-Length").removeHeader("ETag")
                    .header("Content-Type", JSON.toString())
                    .body(ResponseBody.create(root.toString(), JSON)).build();
            response.close();
            return rewritten;
        } catch (JSONException malformed) {
            Logger.printException(() -> "Could not read Redgifs playback metadata", malformed);
            return response;
        }
    }

    private static boolean isCdnVideo(String value) {
        HttpUrl url = safeUrl(value);
        return url != null && CDN.matcher(url.host()).matches()
                && url.encodedPath().toLowerCase(Locale.ROOT).endsWith(".mp4");
    }
}
