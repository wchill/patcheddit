/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.youtube;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

record YouTubeLink(String id, int startSeconds) {
    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Pattern DURATION = Pattern.compile("(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?");

    static YouTubeLink parse(String link) {
        URI uri = parseUri(link);
        return uri == null ? null : parseVideo(uri);
    }

    static boolean isShortsLink(String link) {
        URI uri = parseUri(link);
        return uri != null && uri.getPath().startsWith("/shorts/") && parseVideo(uri) != null;
    }

    private static URI parseUri(String link) {
        if (link == null) return null;
        final URI uri;
        try {
            uri = URI.create(link);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getRawUserInfo() != null) {
            return null;
        }
        return uri;
    }

    private static YouTubeLink parseVideo(URI uri) {
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String[] path = uri.getPath().split("/");
        String query = uri.getQuery();
        String id;
        if (host.equals("youtu.be") && path.length == 2) {
            id = path[1];
        } else if (host.equals("youtube.com") || host.endsWith(".youtube.com")
                || host.equals("youtube-nocookie.com") || host.endsWith(".youtube-nocookie.com")) {
            if ("/watch".equals(uri.getPath())) {
                id = parameter(query, "v");
            } else if (path.length == 3
                    && (path[1].equals("shorts") || path[1].equals("embed")
                    || path[1].equals("live") || path[1].equals("v"))) {
                id = path[2];
            } else {
                return null;
            }
        } else {
            return null;
        }
        if (id == null || !VIDEO_ID.matcher(id).matches()) return null;
        String start = parameter(query, "t");
        if (start == null) start = parameter(query, "start");
        if (start == null) start = parameter(uri.getFragment(), "t");
        return new YouTubeLink(id, seconds(start));
    }

    String watchUrl() {
        return "https://www.youtube.com/watch?v=" + id
                + (startSeconds == 0 ? "" : "&t=" + startSeconds + "s");
    }

    private static String parameter(String query, String name) {
        if (query != null) {
            for (String pair : query.split("&")) {
                if (pair.startsWith(name + "=")) return pair.substring(name.length() + 1);
            }
        }
        return null;
    }

    private static int seconds(String value) {
        if (value == null) return 0;
        try {
            if (value.matches("\\d+")) return Integer.parseInt(value);
            var match = DURATION.matcher(value);
            if (!match.matches()) return 0;
            long seconds = 0;
            for (int group = 1; group <= 3; group++) {
                if (match.group(group) != null) {
                    long amount = Integer.parseInt(match.group(group));
                    seconds += amount * (group == 1 ? 3600 : group == 2 ? 60 : 1);
                }
            }
            return seconds <= Integer.MAX_VALUE ? (int) seconds : 0;
        } catch (NumberFormatException invalid) {
            return 0;
        }
    }
}
