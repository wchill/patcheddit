package app.morphe.extension.relay;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.fixes.redgifs.RedgifsTokenManager;

import reddit.news.oauth.reddit.model.links.MediaPreview;

/**
 * Points Redgifs posts at their real media url so they play with audio.
 * <br>
 * Relay substitutes Reddit's own preview video for any Redgifs watch url, because the
 * `redgifs_api_enabled` remote config flag is always false - it defaults to false and the Spoof
 * client patch stops remote config being loaded. Reddit's preview carries no audio track at all,
 * so there is nothing for the player to unmute.
 * <br>
 * Relay's own Redgifs client cannot be used instead: it authenticates against
 * `POST /v2/oauth/client`, which Redgifs has removed. The shared interceptor is unusable too,
 * because okhttp is minified in this app down to `Interceptor.intercept` itself. So the url is
 * resolved here over HttpURLConnection, reusing the shared temporary-token flow.
 *
 * @noinspection unused
 */
public final class RedgifsUrlResolver {
    private static final String USER_AGENT = "Relay";
    private static final String GIF_ENDPOINT = RedgifsTokenManager.REDGIFS_API_HOST + "/v2/gifs/";
    private static final int TIMEOUT_MS = 10_000;

    private static final ExecutorService executor = Executors.newFixedThreadPool(3);
    private static final Map<String, String> resolved = new ConcurrentHashMap<>();
    private static final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    private RedgifsUrlResolver() {
    }

    /**
     * Rewrites the media url of a Redgifs post to its direct media url.
     * <br>
     * Called from the map stage of Relay's media-preview pipeline, which runs on a background
     * scheduler, so resolving synchronously here is safe.
     *
     * @param url      the post's url, as an `okhttp3.HttpUrl`; typed as Object because okhttp's
     *                 own methods are minified and must not be called from here.
     * @param previews the previews Relay built, whose first entry is the one it plays.
     */
    public static void rewriteRedgifsMedia(Object url, List<MediaPreview> previews) {
        try {
            if (url == null || previews == null || previews.isEmpty()) {
                return;
            }

            String watchUrl = url.toString();
            if (!isRedgifsWatchUrl(watchUrl)) {
                return;
            }

            MediaPreview preview = previews.get(0);

            String media = resolved.get(watchUrl);
            if (media != null) {
                apply(preview, media);
                return;
            }

            // Relay's preview pipeline applies this on the main thread, because its `observeOn`
            // runs before the map stage. So nothing is waited on here: the preview is updated in
            // place once the url resolves, which is well before the post is ever played.
            if (inFlight.add(watchUrl)) {
                executor.submit(() -> {
                    try {
                        String resolvedUrl = fetchMediaUrl(watchUrl);
                        if (resolvedUrl != null) {
                            resolved.put(watchUrl, resolvedUrl);
                            apply(preview, resolvedUrl);
                        }
                    } finally {
                        inFlight.remove(watchUrl);
                    }
                });
            }
        } catch (Exception ex) {
            Logger.printException(() -> "Could not rewrite the Redgifs media url", ex);
        }
    }

    /**
     * Relay keeps this preview in the link's media list and reads the url when the post is played,
     * so updating it after the fact is enough - no listing refresh is needed.
     */
    private static void apply(MediaPreview preview, String media) {
        preview.mediaUrl = media;
        // Relay prefers mobileMediaUrl when set, and there is no separate mobile encode here.
        preview.mobileMediaUrl = "";
        preview.type = 2;

        Logger.printDebug(() -> "Playing as " + media);
    }

    private static boolean isRedgifsWatchUrl(String url) {
        return url.contains("redgifs.com/watch/");
    }

    private static String fetchMediaUrl(String watchUrl) {
        HttpURLConnection connection = null;
        try {
            String id = idOf(watchUrl);
            if (id.isEmpty()) {
                return null;
            }

            String token = RedgifsTokenManager.refreshToken(USER_AGENT).getAccessToken();

            connection = (HttpURLConnection) new URL(GIF_ENDPOINT + id).openConnection();
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                Logger.printDebug(() -> "Redgifs returned " + responseCode + " for " + id);
                return null;
            }

            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
            }

            JSONObject urls = new JSONObject(body.toString())
                    .getJSONObject("gif")
                    .getJSONObject("urls");

            // hd and sd both carry audio; sd is the smaller "-mobile" encode.
            String media = urls.optString("hd", urls.optString("sd", ""));
            if (media.isEmpty()) {
                Logger.printDebug(() -> "Redgifs response had no usable url for " + id);
                return null;
            }

            return media;
        } catch (Exception ex) {
            Logger.printDebug(() -> "Could not resolve " + watchUrl + ": " + ex);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Redgifs ids are the last path segment, before any `-` suffix.
     */
    private static String idOf(String watchUrl) {
        String path = watchUrl;
        int query = path.indexOf('?');
        if (query != -1) {
            path = path.substring(0, query);
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        String last = path.substring(path.lastIndexOf('/') + 1);
        int dash = last.indexOf('-');
        return dash == -1 ? last : last.substring(0, dash);
    }
}
