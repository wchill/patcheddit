package app.morphe.extension.shared.requests;

import okhttp3.Interceptor;

public final class RedditRouteFixture {
    private RedditRouteFixture() {}

    /** Exercise route dispatch without the inclusion marker that Morphe rewrites in the APK. */
    public static Interceptor included(RedditRouteResponseShim shim) {
        return shim::doIntercept;
    }
}
