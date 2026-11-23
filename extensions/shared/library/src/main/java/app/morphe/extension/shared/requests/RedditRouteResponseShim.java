/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.requests;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

import okhttp3.Request;
import okhttp3.Response;

public abstract class RedditRouteResponseShim extends PatchedditInterceptor {
    private final Predicate<Request> route;

    @Override
    public boolean isEnabled() {
        // Always enable for Reddit API fixes.
        return true;
    }

    protected RedditRouteResponseShim(String path, String... methods) {
        this(exactRoute(path, methods));
    }

    /** Matches a Reddit request whose route cannot be expressed as one exact normalized path. */
    protected RedditRouteResponseShim(Predicate<Request> route) {
        this.route = route;
    }

    private static Predicate<Request> exactRoute(String path, String[] methods) {
        String normalizedPath = normalizePath(path);
        Set<String> allowed = new HashSet<>();
        for (String method : methods) allowed.add(method.toUpperCase(Locale.ROOT));
        return request -> allowed.contains(request.method().toUpperCase(Locale.ROOT))
                && normalizedPath.equals(normalizePath(request.url().encodedPath()));
    }

    @NonNull
    @Override
    protected final Response doIntercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        if (!isRedditHost(request.url().host()) || !route.test(request)) {
            return chain.proceed(request);
        }
        return shimResponse(chain, request);
    }

    @NonNull
    protected abstract Response shimResponse(@NonNull Chain chain, @NonNull Request request)
            throws IOException;

    public static boolean isRedditHost(String host) {
        if (host == null) return false;
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.equals("reddit.com") || normalized.endsWith(".reddit.com");
    }

    public static String normalizePath(String path) {
        String normalized = path.toLowerCase(Locale.ROOT);
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/.json")) {
            normalized = normalized.substring(0, normalized.length() - "/.json".length());
        } else if (normalized.endsWith(".json")) {
            normalized = normalized.substring(0, normalized.length() - ".json".length());
        }
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.startsWith("/r/")) {
            int end = normalized.indexOf('/', 3);
            if (end > 3) {
                String route = normalized.substring(end);
                if (route.startsWith("/api/") || route.startsWith("/by_id/")) {
                    normalized = route;
                }
            }
        }
        return normalized;
    }
}
