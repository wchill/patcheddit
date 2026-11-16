/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * Copyright 2026 vonwerderc.
 * Copyright 2026 Apollo-Reborn contributors.
 *
 * Adapted from https://github.com/wchill/patcheddit/pull/142.
 * Gallery repair approach: https://github.com/Apollo-Reborn/Apollo-Reborn/pull/1589.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.fixes.api;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.shared.requests.PatchedditInterceptor;
import app.morphe.extension.shared.requests.RedditRouteResponseShim;
import app.morphe.extension.shared.settings.OAuthSettings;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Since late October 2026 Reddit returns {@code media_metadata} entries of galleries that were
 * created through its public API as {@code {"status":"failed"}} (or briefly
 * {@code "unprocessed"}) with no URLs. The images themselves are fine and are served at
 * {@code https://i.redd.it/<media_id>.<ext>}. Clients that only read valid entries end up with
 * empty image URLs ("Glide failed to grab this image" in Sync).
 *
 * <p>This interceptor rebuilds such entries as valid ones. i.redd.it only serves the exact
 * extension of the upload, so each candidate URL is checked with a HEAD request first.
 * Only single-post lookups are touched ({@code /api/info}, {@code /by_id/}, {@code /comments/}),
 * never subreddit or front page listings, to keep the extra requests bounded.
 * Probing is also bounded by a probe count and a time budget, and stops at the first
 * transport error, so a slow i.redd.it can't hold up the original response for long.
 *
 * <p>Same approach as Apollo Reborn PR #1589.
 */
public final class FailedGalleryMetadataPatch extends PatchedditInterceptor {
    private static final Logger LOGGER = Logger.getLogger(FailedGalleryMetadataPatch.class.getName());
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final long MAX_BODY_BYTES = 8L * 1024 * 1024;
    private static final int MAX_PROBES_PER_RESPONSE = 80;
    /** Repair budget shared by all images in a response. */
    private static final long REPAIR_BUDGET_NANOS = TimeUnit.SECONDS.toNanos(6);
    private static final int PROBE_TIMEOUT_SECONDS = 3;
    private static final String[] DEFAULT_EXTENSIONS = {"jpg", "png", "jpeg", "webp", "gif"};
    private static final Pattern MEDIA_URL = Pattern.compile(
            "redd\\.it/(?:[^/?#]*-)?([a-z0-9]+)\\.([a-z0-9]{3,4})(?:[?#]|$)",
            Pattern.CASE_INSENSITIVE);

    enum Probe {FOUND, MISSING, FAILED}

    /**
     * Probes one candidate URL. {@link Probe#FAILED} stops the response's remaining probes.
     * Throws on cancellation or interruption.
     */
    interface Prober {
        Probe probe(String url) throws IOException;
    }

    private final Prober prober;
    private final boolean repairGifs;

    public FailedGalleryMetadataPatch() {
        this(true);
    }

    public FailedGalleryMetadataPatch(boolean repairGifs) {
        prober = null;
        this.repairGifs = repairGifs;
    }

    FailedGalleryMetadataPatch(Prober prober) {
        this(prober, true);
    }

    FailedGalleryMetadataPatch(Prober prober, boolean repairGifs) {
        this.prober = java.util.Objects.requireNonNull(prober);
        this.repairGifs = repairGifs;
    }

    @Override
    public boolean isPatchIncluded() {
        // Overridden by patch.
        return false;
    }

    @NonNull
    @Override
    protected Response doIntercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        Response response = chain.proceed(request);
        if (!isPostLookup(request) || !response.isSuccessful()) return response;
        String encoding = response.header("Content-Encoding");
        if (encoding != null && !encoding.equalsIgnoreCase("identity")) return response;
        ResponseBody body = response.body();
        MediaType type = body == null ? null : body.contentType();
        if (type == null || !type.toString().toLowerCase(Locale.ROOT).contains("json")) {
            return response;
        }
        if (body.contentLength() > MAX_BODY_BYTES) return response;

        // Bound buffering when Content-Length is unknown.
        byte[] originalBytes;
        try (ResponseBody peek = response.peekBody(MAX_BODY_BYTES + 1)) {
            if (peek.contentLength() > MAX_BODY_BYTES) return response;
            originalBytes = peek.bytes();
        }
        String text;
        try (ResponseBody copy = ResponseBody.create(originalBytes, type)) {
            text = copy.string();
        }
        Object json = galleryJson(text);
        if (json == null) return response;

        // Close the network body for HEAD probes; keep its bytes for unchanged responses.
        response.close();
        Response buffered = response.newBuilder()
                .body(ResponseBody.create(originalBytes, type))
                .build();
        final String repaired;
        try {
            Prober boundProber = prober == null ? url -> head(chain, request, url) : prober;
            repaired = repair(json, boundProber, System.nanoTime() + REPAIR_BUDGET_NANOS, false, repairGifs);
            if (repaired == null) return buffered;
        } catch (IOException canceled) {
            buffered.close();
            throw canceled;
        }
        buffered.close();
        return buffered.newBuilder()
                // Rewritten JSON invalidates the original length and entity tag.
                .removeHeader("Content-Length")
                .removeHeader("ETag")
                .header("Content-Type", "application/json; charset=utf-8")
                .body(ResponseBody.create(repaired, JSON))
                .build();
    }

    /** Repairs BaconReader's Apache HTTP response after its stream has been consumed. */
    public static String repairLegacyResponse(String body, String url, int code, String contentType)
            throws IOException {
        long deadline = System.nanoTime() + REPAIR_BUDGET_NANOS;
        return repairLegacyBody(body, url, code, contentType,
                candidate -> legacyHead(candidate, deadline), deadline);
    }

    static String repairLegacyBody(String body, String url, int code, String contentType,
                                   Prober prober, long deadline) throws IOException {
        if (body == null || url == null || code < 200 || code >= 300 || contentType == null
                || !contentType.toLowerCase(Locale.ROOT).contains("json")
                || body.getBytes(StandardCharsets.UTF_8).length > MAX_BODY_BYTES) return body;
        final Request request;
        try {
            request = new Request.Builder().url(url).build();
        } catch (IllegalArgumentException exception) {
            LOGGER.log(Level.WARNING, "Invalid legacy Reddit gallery URL", exception);
            return body;
        }
        if (!isPostLookup(request)) return body;
        Object json = galleryJson(body);
        if (json == null) return body;
        String repaired = repair(json, prober, deadline, true, true);
        return repaired == null ? body : repaired;
    }

    private static boolean isPostLookup(Request request) {
        if (!RedditRouteResponseShim.isRedditHost(request.url().host())) return false;
        String path = RedditRouteResponseShim.normalizePath(request.url().encodedPath());
        return path.equals("/api/info") || path.startsWith("/by_id/")
                || path.startsWith("/comments/") || path.contains("/comments/");
    }

    private static Object galleryJson(String text) {
        if (!text.contains("media_metadata") || !text.contains("gallery_data")
                || !(text.contains("\"failed\"") || text.contains("\"unprocessed\""))) return null;
        try {
            return new JSONTokener(text).nextValue();
        } catch (JSONException exception) {
            LOGGER.log(Level.WARNING, "Could not parse Reddit gallery metadata", exception);
            return null;
        }
    }

    private static String repair(Object json, Prober prober, long deadline, boolean previewUrls,
                                 boolean repairGifs)
            throws IOException {
        try {
            Repairer repairer = new Repairer(prober, deadline, previewUrls, repairGifs);
            repairer.walk(json);
            return repairer.changed == 0 ? null : json.toString();
        } catch (JSONException exception) {
            LOGGER.log(Level.WARNING, "Could not repair Reddit gallery metadata", exception);
            return null;
        }
    }

    private static final class Repairer {
        final Prober prober;
        final long deadline;
        final boolean previewUrls;
        final boolean repairGifs;
        int probes;
        int changed;
        boolean stopped;
        String lastExtension;

        Repairer(Prober prober, long deadline, boolean previewUrls, boolean repairGifs) {
            this.prober = prober;
            this.deadline = deadline;
            this.previewUrls = previewUrls;
            this.repairGifs = repairGifs;
        }

        // Handles info listings, comment-thread arrays, and crossposts.
        void walk(Object node) throws JSONException, IOException {
            if (node instanceof JSONArray) {
                JSONArray array = (JSONArray) node;
                for (int i = 0; i < array.length(); i++) walk(array.get(i));
            } else if (node instanceof JSONObject) {
                JSONObject object = (JSONObject) node;
                if (object.optJSONObject("media_metadata") != null
                        && object.optJSONObject("gallery_data") != null) {
                    repairPost(object);
                }
                JSONArray names = object.names();
                if (names == null) return;
                for (int i = 0; i < names.length(); i++) {
                    Object child = object.opt(names.getString(i));
                    if (child instanceof JSONObject || child instanceof JSONArray) walk(child);
                }
            }
        }

        void repairPost(JSONObject post) throws JSONException, IOException {
            JSONObject metadata = post.getJSONObject("media_metadata");
            JSONArray items = post.getJSONObject("gallery_data").optJSONArray("items");
            if (items == null) return;
            String hintedId = null;
            String hintedExtension = null;
            // Thumbnail and destination filenames provide extension hints.
            for (String key : new String[]{"thumbnail", "url_overridden_by_dest", "url"}) {
                Matcher matcher = MEDIA_URL.matcher(post.optString(key, ""));
                if (matcher.find()) {
                    hintedId = matcher.group(1);
                    hintedExtension = matcher.group(2).toLowerCase(Locale.ROOT);
                    break;
                }
            }
            // Follow gallery order and preserve existing sources.
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                String id = item.optString("media_id", "");
                if (!id.matches("[A-Za-z0-9]+")) continue;
                JSONObject entry = metadata.optJSONObject(id);
                if (entry != null && entry.optJSONObject("s") != null) continue;
                String status = entry == null ? "" : entry.optString("status", "");
                if (entry != null && !status.equals("failed") && !status.equals("unprocessed")) {
                    continue;
                }
                String mimeExtension = entry == null ? null : extensionFromMime(entry.optString("m", ""));
                String extension = findExtension(id, mimeExtension,
                        id.equalsIgnoreCase(hintedId) ? hintedExtension : null, hintedExtension);
                if (extension == null) continue;
                String url = "https://i.redd.it/" + id + "." + extension;
                JSONObject source = new JSONObject().put("u", url);
                if (extension.equals("gif")) source.put("gif", url);
                // BaconReader's legacy gallery reads thumbnail URLs only from p.
                JSONArray previews = new JSONArray();
                if (previewUrls) previews.put(new JSONObject().put("u", url));
                JSONObject fixed = entry == null ? new JSONObject() : entry;
                fixed.put("status", "valid")
                        .put("e", extension.equals("gif") ? "AnimatedImage" : "Image")
                        // BaconReader derives the file extension from this field.
                        .put("m", "image/" + extension)
                        .put("id", id)
                        .put("s", source)
                        .put("p", previews);
                metadata.put(id, fixed);
                changed++;
            }
        }

        String findExtension(String id, String... hints) throws IOException {
            // Try metadata/URL hints, the previous match, then remaining formats.
            LinkedHashSet<String> candidates = new LinkedHashSet<>();
            for (String hint : hints) if (hint != null) candidates.add(hint);
            if (lastExtension != null) candidates.add(lastExtension);
            for (String extension : DEFAULT_EXTENSIONS) candidates.add(extension);
            List<String> ordered = new ArrayList<>(candidates);
            for (String extension : ordered) {
                if (extensionFromMime("image/" + extension) == null
                        || (!repairGifs && extension.equals("gif"))) continue;
                if (stopped || probes >= MAX_PROBES_PER_RESPONSE || System.nanoTime() >= deadline) {
                    stopped = true;
                    return null;
                }
                probes++;
                Probe result = prober.probe("https://i.redd.it/" + id + "." + extension);
                if (result == Probe.FOUND) {
                    lastExtension = extension;
                    return extension;
                }
                if (result == Probe.FAILED) {
                    // Stop the response's probes after a transport or server failure.
                    stopped = true;
                    return null;
                }
            }
            return null;
        }
    }

    static String extensionFromMime(String mime) {
        if (mime == null) return null;
        String lower = mime.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("image/")) return null;
        String subtype = lower.substring("image/".length());
        switch (subtype) {
            case "jpg":
            case "jpeg":
            case "png":
            case "webp":
            case "gif":
                return subtype;
            default:
                return null;
        }
    }

    private static Probe head(Chain chain, Request original, String url) throws IOException {
        if (chain.call().isCanceled()) throw new IOException("Canceled");
        String userAgent = original.header("User-Agent");
        // Do not forward Reddit credentials to the image host.
        Request probe = new Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", userAgent == null ? "Mozilla/5.0" : userAgent)
                .build();
        Chain bounded = chain
                .withConnectTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .withReadTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        try (Response response = bounded.proceed(probe)) {
            if (response.code() == 200) return Probe.FOUND;
            return response.code() == 404 ? Probe.MISSING : Probe.FAILED;
        } catch (IOException exception) {
            if (chain.call().isCanceled()) throw exception;
            LOGGER.log(Level.WARNING, "Could not probe Reddit gallery media", exception);
            return Probe.FAILED;
        }
    }

    // Apache requests have no OkHttp chain to reuse.
    private static final class LegacyClient {
        static final OkHttpClient INSTANCE = new OkHttpClient.Builder()
                .connectTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
    }

    private static Probe legacyHead(String url, long deadline) throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new IOException("Gallery repair interrupted");
        Request request = new Request.Builder().url(url).head()
                .header("User-Agent", OAuthSettings.getUserAgent("Mozilla/5.0")).build();
        Call call = LegacyClient.INSTANCE.newCall(request);
        // Cap the whole call at the remaining response budget.
        call.timeout().timeout(Math.max(1, Math.min(TimeUnit.SECONDS.toNanos(PROBE_TIMEOUT_SECONDS),
                deadline - System.nanoTime())), TimeUnit.NANOSECONDS);
        try (Response response = call.execute()) {
            if (response.code() == 200) return Probe.FOUND;
            return response.code() == 404 ? Probe.MISSING : Probe.FAILED;
        } catch (IOException exception) {
            if (Thread.currentThread().isInterrupted()) throw exception;
            LOGGER.log(Level.WARNING, "Could not probe legacy Reddit gallery media", exception);
            return Probe.FAILED;
        }
    }
}
