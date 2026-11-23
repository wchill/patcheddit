/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.redditisfun.feed;

import android.net.Uri;

import java.util.regex.Pattern;

import okhttp3.Request;

public final class GuestListings {
    private static final String COMMUNITY = "(?:/r/[A-Za-z0-9_+]+)?";
    private static final Pattern FEED = Pattern.compile(
            COMMUNITY + "(?:/(?:best|hot|new|rising|controversial|top))?/?\\.json");
    private static final Pattern THREAD = Pattern.compile(
            COMMUNITY + "/comments/[a-z0-9]+(?:/[A-Za-z0-9_%\\-]+){0,2}/?\\.json");

    private GuestListings() {}

    public static boolean isListing(Uri uri) {
        if (uri == null || !"https".equals(uri.getScheme())) return false;
        String authority = uri.getEncodedAuthority();
        if (!"www.reddit.com".equals(authority) && !"api.reddit.com".equals(authority)
                && !"oauth.reddit.com".equals(authority)) return false;
        String path = uri.getEncodedPath();
        return path != null && (FEED.matcher(path).matches() || THREAD.matcher(path).matches());
    }

    public static void authorize(Request.Builder request, String token, Uri uri) {
        if (token != null && !token.isEmpty() && uri != null
                && "oauth.reddit.com".equals(uri.getEncodedAuthority()) && isListing(uri)) {
            request.header("Authorization", "Bearer " + token);
        }
    }
}
