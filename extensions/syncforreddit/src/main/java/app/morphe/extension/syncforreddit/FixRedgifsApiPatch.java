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
     * Replaces Sync's LinkHandler.getGfycatId, which supplies the id for /v2/gifs/{id}. The original strips a
     * trailing slash before the query, so "redgifs.com/watch/{id}/?92/" gives an empty id and a 404. Here the
     * query and fragment go first; the suffix cleanup is the original's.
     */
    public static String extractGifId(String url) {
        if (url == null) return null;
        int query = url.indexOf('?');
        if (query >= 0) url = url.substring(0, query);
        int fragment = url.indexOf('#');
        if (fragment >= 0) url = url.substring(0, fragment);
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        String id = url.substring(url.lastIndexOf('/') + 1)
                .replace(".gif", "").replace(".mp4", "").replace(".webm", "")
                .replace("-mobile", "").replace("-size_restricted", "").replace("-max-1mb-poster", "");
        return id.contains("-") ? id.split("-")[0] : id;
    }
}
