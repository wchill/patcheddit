package app.morphe.extension.boostforreddit.http.wayback;

import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;

import app.morphe.extension.boostforreddit.http.AutoSavingCache;
import app.morphe.extension.boostforreddit.http.HttpUtils;
import app.morphe.extension.boostforreddit.utils.LoggingUtils;
import okhttp3.Request;

public class WaybackMachine {
    private static final String WAYBACK_MACHINE_CDX_URL = "https://web.archive.org/web/timemap/json/%s";
    private static final String WAYBACK_MACHINE_CONTENT_URL = "https://web.archive.org/web/%sif_/%s";
    private static final AutoSavingCache waybackCache = new AutoSavingCache("WaybackMachine", 100000);

    public static Optional<WaybackResponse> fetchUrlFromCache(Request request, String contentUrl) throws IOException {
        Optional<String> cached = waybackCache.get(contentUrl);
        if (cached.isPresent()) {
            String cachedUrl = cached.get();
            if (cachedUrl.isEmpty()) {
                LoggingUtils.logInfo(false, () -> String.format(Locale.ROOT, "URL %s not in Wayback Machine", contentUrl));
                return Optional.of(new WaybackResponse(request));
            }
            LoggingUtils.logInfo(true, () -> String.format(Locale.ROOT, "URL %s cached", cachedUrl));
            return Optional.of(new WaybackResponse(HttpUtils.get(cachedUrl), getTimestamp(cachedUrl)));
        }
        return Optional.empty();
    }

    public static WaybackResponse getFromWayback(Request request, String contentUrl) throws IOException {
        Optional<WaybackResponse> cached = fetchUrlFromCache(request, contentUrl);
        if (cached.isPresent()) {
            return cached.get();
        }

        String waybackCdxUrl = String.format(WAYBACK_MACHINE_CDX_URL, contentUrl);

        ArrayNode waybackResponse = (ArrayNode) HttpUtils.getJson(waybackCdxUrl);
        if (waybackResponse.size() == 0) {
            LoggingUtils.logInfo(false, () -> String.format(Locale.ROOT, "URL %s not available in Wayback Machine", contentUrl));
            waybackCache.put(contentUrl, "");
            return new WaybackResponse(request);
        }

        String timestamp = waybackResponse.get(1).get(1).asText();
        String waybackContentUrl = getWaybackContentUrl(contentUrl, timestamp);
        LoggingUtils.logInfo(true, () -> String.format(Locale.ROOT, "URL %s -> %s", contentUrl, waybackContentUrl));
        waybackCache.put(contentUrl, waybackContentUrl);
        return new WaybackResponse(HttpUtils.get(waybackContentUrl), timestamp);
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
