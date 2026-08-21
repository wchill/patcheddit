/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.syncforreddit;

import app.morphe.extension.shared.fixes.redgifs.BaseFixRedgifsApiPatch;
import okhttp3.OkHttpClient;

/**
 * @noinspection unused
 */
public class FixRedgifsApiPatch extends BaseFixRedgifsApiPatch {
    static {
        INSTANCE = new FixRedgifsApiPatch();
    }

    public String getDefaultUserAgent() {
        // To be filled in by patch
        return "";
    }

    public static OkHttpClient install(OkHttpClient.Builder builder) {
        return builder.addInterceptor(INSTANCE).build();
    }

    /**
     * Replacement for Sync's LinkHandler.getGfycatId (y7.a.d), which feeds the
     * Redgifs API URL builder. The original strips the trailing slash BEFORE the
     * query/fragment, so a URL like "https://www.redgifs.com/watch/<id>/?92/"
     * leaves "?92" as the last path segment and the query-strip then reduces the
     * "ID" to an empty string -- the API call goes out as /v2/gifs/ and 404s.
     * Strip query/fragment from the full URL first, then take the last segment.
     * Suffix cleanup kept identical to the original for parity (same replaces,
     * same order, same dash-split). Deliberately NO ".jpg" strip: type-2 image
     * posts resolve to .jpg URLs that Sync feeds to its VIDEO player (error 14
     * "Could not load video"); keeping the suffix lets the API 404 so the
     * WebView fallback displays the image instead.
     */
    public static String extractGifId(String url) {
        if (url == null) return null;
        try {
            int queryStart = url.indexOf('?');
            if (queryStart >= 0) url = url.substring(0, queryStart);
            int fragmentStart = url.indexOf('#');
            if (fragmentStart >= 0) url = url.substring(0, fragmentStart);
            if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
            String id = url.substring(url.lastIndexOf('/') + 1);
            id = id.replace(".gif", "").replace(".mp4", "").replace(".webm", "")
                    .replace("-mobile", "").replace("-size_restricted", "")
                    .replace("-max-1mb-poster", "");
            if (id.contains("-")) id = id.split("-")[0];
            return id;
        } catch (Exception ignored) {
            return null;
        }
    }
}
