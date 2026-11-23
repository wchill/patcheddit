/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Modifications Copyright 2026 brealorg.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 */

package app.morphe.extension.shared.archive.wayback;

import app.morphe.extension.vendored.com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import app.morphe.extension.shared.archive.HttpUtils;
import app.morphe.extension.shared.cache.AutoSavingCache;
import app.morphe.extension.shared.debug.LoggingUtils;
import okhttp3.Request;
import okhttp3.Response;

public class WaybackMachine {
    private static final String WAYBACK_MACHINE_CDX_URL = "https://web.archive.org/web/timemap/json/%s";
    private static final String WAYBACK_MACHINE_CONTENT_URL = "https://web.archive.org/web/%sif_/%s";
    private static final String COOLDOWN_MARKER = "morphe_boost_wayback_cooldown";
    private static final AutoSavingCache waybackCache = new AutoSavingCache("WaybackMachine", 100);

    // Lookups run inside client requests, so a slow archive must not hold them for long.
    private static final long CDX_CALL_TIMEOUT_MS = 10_000L;
    // Content is streamed to the caller, so only connecting and each read are bounded.
    public static final long CONTENT_TIMEOUT_MS = 15_000L;

    // After a failure, skip lookups briefly instead of making every request wait for the same outage.
    private static final long FAILURE_COOLDOWN_NANOS = TimeUnit.SECONDS.toNanos(60);
    private static volatile Long lastFailureNanos;

    public static Optional<WaybackResponse> fetchUrlFromCache(Request request, String contentUrl) throws IOException {
        Optional<String> cached = waybackCache.get(contentUrl);
        if (cached.isPresent()) {
            String cachedUrl = cached.get();
            if (cachedUrl.isEmpty()) {
                LoggingUtils.logInfo(false, () -> String.format(Locale.ROOT, "URL %s not in Wayback Machine", contentUrl));
                return Optional.of(new WaybackResponse(request));
            }
            LoggingUtils.logInfo(true, () -> String.format(Locale.ROOT, "URL %s cached", cachedUrl));
            return Optional.of(fetchContent(request, contentUrl, cachedUrl, getTimestamp(cachedUrl)));
        }
        return Optional.empty();
    }

    public static WaybackResponse getFromWayback(Request request, String contentUrl) throws IOException {
        Optional<WaybackResponse> cached = fetchUrlFromCache(request, contentUrl);
        if (cached.isPresent()) {
            return cached.get();
        }

        checkCooldown();
        String waybackCdxUrl = String.format(WAYBACK_MACHINE_CDX_URL, contentUrl);
        JsonNode snapshots;
        try {
            snapshots = HttpUtils.getJson(waybackCdxUrl, CDX_CALL_TIMEOUT_MS);
        } catch (IOException failure) {
            throw recordFailure(failure);
        }

        String timestamp = pickTimestamp(snapshots);
        if (timestamp == null) {
            LoggingUtils.logInfo(false, () -> String.format(Locale.ROOT, "URL %s not available in Wayback Machine", contentUrl));
            waybackCache.put(contentUrl, "");
            return new WaybackResponse(request);
        }

        String waybackContentUrl = getWaybackContentUrl(contentUrl, timestamp);
        LoggingUtils.logInfo(true, () -> String.format(Locale.ROOT, "URL %s -> %s", contentUrl, waybackContentUrl));
        return fetchContent(request, contentUrl, waybackContentUrl, timestamp);
    }

    /**
     * Returns the timestamp of the oldest successful capture in a timemap, preferring HTTP 200
     * captures over redirects and errors. Null when the timemap has no usable rows.
     */
    static String pickTimestamp(JsonNode snapshots) {
        if (snapshots == null || !snapshots.isArray() || snapshots.size() < 2) {
            return null;
        }
        JsonNode header = snapshots.get(0);
        int timestampColumn = columnIndex(header, "timestamp", 1);
        int statusColumn = columnIndex(header, "statuscode", -1);
        String fallback = null;
        for (int i = 1; i < snapshots.size(); i++) {
            JsonNode row = snapshots.get(i);
            if (row == null || !row.isArray() || row.size() <= timestampColumn) {
                continue;
            }
            String timestamp = row.get(timestampColumn).asText();
            if (!timestamp.matches("\\d{4,14}")) {
                continue;
            }
            if (statusColumn < 0 || (row.size() > statusColumn && "200".equals(row.get(statusColumn).asText()))) {
                return timestamp;
            }
            if (fallback == null) {
                fallback = timestamp;
            }
        }
        return fallback;
    }

    private static int columnIndex(JsonNode header, String name, int defaultIndex) {
        if (header != null && header.isArray()) {
            for (int i = 0; i < header.size(); i++) {
                if (name.equals(header.get(i).asText())) {
                    return i;
                }
            }
        }
        return defaultIndex;
    }

    private static WaybackResponse fetchContent(Request request, String contentUrl, String waybackContentUrl,
                                                String timestamp) throws IOException {
        checkCooldown();
        Response response;
        try {
            response = HttpUtils.get(waybackContentUrl, CONTENT_TIMEOUT_MS);
        } catch (IOException failure) {
            throw recordFailure(failure);
        }
        if (response.isSuccessful()) {
            lastFailureNanos = null;
            waybackCache.put(contentUrl, waybackContentUrl);
            return new WaybackResponse(response, timestamp);
        }

        int code = response.code();
        response.close();
        if (code == 404 || code == 410) {
            // The capture is gone; remember that rather than asking again.
            waybackCache.put(contentUrl, "");
            return new WaybackResponse(request);
        }
        throw recordFailure(new IOException("Wayback HTTP " + code));
    }

    private static void checkCooldown() throws IOException {
        Long failedAt = lastFailureNanos;
        if (failedAt != null && System.nanoTime() - failedAt < FAILURE_COOLDOWN_NANOS) {
            throw new IOException(COOLDOWN_MARKER + ": skipping lookup after recent failure");
        }
    }

    private static IOException recordFailure(IOException failure) {
        lastFailureNanos = System.nanoTime();
        LoggingUtils.logInfo(false, () -> COOLDOWN_MARKER + ": " + failure.getClass().getSimpleName());
        return failure;
    }
    public static String getTimestamp(String url) {
        String[] parts = url.split("/");
        for (String s : parts) {
            String part = s.replace("if_", "");
            try {
                Long.parseLong(part);
                return part;
            } catch (NumberFormatException ignored) {}
        }
        throw new IllegalArgumentException("Timestamp not found");
    }

    public static String getWaybackContentUrl(String contentUrl, String timestamp) {
        return String.format(WAYBACK_MACHINE_CONTENT_URL, timestamp, contentUrl);
    }
}
