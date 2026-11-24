/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Modifications Copyright 2026 brealorg.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 */

package app.morphe.extension.boost.http.reddit;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Locale;

import app.morphe.extension.boost.utils.BoostUndeleteSettings;
import app.morphe.extension.shared.archive.wayback.WaybackMachine;
import app.morphe.extension.shared.archive.wayback.WaybackResponse;
import app.morphe.extension.shared.debug.LoggingUtils;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Glide's HttpUrlFetcher uses HttpURLConnection directly and does not go through
 * Boost's OkHttp/JRAW clients. This helper is invoked only from Glide's HTTP
 * failure path after the original host has returned 404.
 */
public final class GlideRedditMediaWayback {
    private static final String MARKER = "morphe_boost_glide_reddit_media_wayback";

    private GlideRedditMediaWayback() {
    }

    public static InputStream open(HttpURLConnection connection, int responseCode) {
        try {
            if (responseCode != HttpURLConnection.HTTP_NOT_FOUND) {
                return null;
            }

            if (!BoostUndeleteSettings.isRedditUndeleteEnabled()) {
                return null;
            }

            if (connection == null || connection.getURL() == null) {
                return null;
            }

            String contentUrl = connection.getURL().toString();
            if (!isRedditMediaUrl(contentUrl)) {
                return null;
            }

            Request request = new Request.Builder()
                    .get()
                    .url(contentUrl)
                    .build();

            WaybackResponse waybackResponse = WaybackMachine.getFromWayback(request, contentUrl);
            if (!waybackResponse.found()) {
                LoggingUtils.logInfo(true, () -> MARKER + ": no Wayback media for " + contentUrl);
                return null;
            }

            Response response = waybackResponse.getResponse();
            if (response == null || !response.isSuccessful()) {
                waybackResponse.close();
                LoggingUtils.logInfo(true, () -> MARKER + ": unusable Wayback response for " + contentUrl);
                return null;
            }

            LoggingUtils.logInfo(true, () -> MARKER + ": restored " + contentUrl);
            return response.body().byteStream();
        } catch (Throwable ignored) {
            LoggingUtils.logInfo(false, () -> MARKER + ": failed open");
            return null;
        }
    }

    private static boolean isRedditMediaUrl(String url) {
        if (url == null) {
            return false;
        }

        String lower = url.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://i.redd.it/")
                || lower.startsWith("http://i.redd.it/")
                || lower.startsWith("https://preview.redd.it/")
                || lower.startsWith("http://preview.redd.it/");
    }
}
