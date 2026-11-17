/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.fixes.media;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CommentVideoLinks {
    private static final Set<String> HOSTS = new HashSet<>(Arrays.asList(
            "reddit.com", "www.reddit.com", "old.reddit.com", "np.reddit.com",
            "m.reddit.com", "new.reddit.com", "sh.reddit.com"));
    private static final Pattern PLAYER = Pattern.compile(
            "/link/([A-Za-z0-9]{1,64})/video/([A-Za-z0-9]{1,64})(?:/player)?/?");

    private CommentVideoLinks() {}

    public static String baseUrl(String link) {
        if (link == null || link.length() > 4096) return null;
        try {
            URI uri = new URI(link);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null || uri.getRawUserInfo() != null
                    || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))
                    || !HOSTS.contains(host.toLowerCase(Locale.ROOT))) return null;
            int port = uri.getPort();
            if (port != -1 && port != (scheme.equalsIgnoreCase("https") ? 443 : 80)) return null;
            String path = uri.getRawPath();
            if (path == null) return null;
            Matcher match = PLAYER.matcher(path);
            if (!match.matches()) return null;
            return "https://v.redd.it/link/" + match.group(1) + "/asset/" + match.group(2);
        } catch (URISyntaxException invalidUrl) {
            return null;
        }
    }

    public static String dashUrl(String link) {
        String base = baseUrl(link);
        return base == null ? null : base + "/DASHPlaylist.mpd";
    }

    public static String hlsUrl(String link) {
        String base = baseUrl(link);
        return base == null ? null : base + "/HLSPlaylist.m3u8";
    }
}
