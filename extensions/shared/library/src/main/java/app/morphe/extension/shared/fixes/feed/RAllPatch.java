/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.shared.fixes.feed;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.net.URI;
import java.util.regex.Pattern;

import app.morphe.extension.shared.requests.RedditRouteResponseShim;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;


public class RAllPatch extends RedditRouteResponseShim {
    private static final Pattern LISTING_PATH = Pattern.compile(
            "/r/all(?:-[a-z0-9_]+)*(?:/(?:best|hot|new|rising|top|controversial))?/?",
            Pattern.CASE_INSENSITIVE);

    public RAllPatch() {
        super(request -> shouldRewrite(request.method(), request.url()));
    }

    private static boolean shouldRewrite(String method, HttpUrl url) {
        return "GET".equals(method) && isRedditHost(url.host()) && LISTING_PATH.matcher(url.encodedPath()).matches();
    }

    private static HttpUrl listingJsonUrl(HttpUrl url) {
        return url.newBuilder().addEncodedPathSegment(".json").build();
    }

    /** For BaconReader's Apache request path, which bypasses the OkHttp shim. */
    public static URI rewriteLegacyListing(String method, URI original) {
        if (!"GET".equals(method) || original == null) return null;
        HttpUrl url = HttpUrl.parse(original.toString());
        return url != null && shouldRewrite(method, url) ? listingJsonUrl(url).uri() : null;
    }

    @Override
    public boolean isPatchIncluded() {
        // Overridden by patch.
        return false;
    }

    @NonNull
    @Override
    protected Response shimResponse(@NonNull Chain chain, @NonNull Request request) throws IOException {
        Request newRequest = request.newBuilder()
                .url(listingJsonUrl(request.url()))
                .build();
        return chain.proceed(newRequest);
    }
}
