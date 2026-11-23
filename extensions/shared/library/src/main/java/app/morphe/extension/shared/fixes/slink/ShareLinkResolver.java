package app.morphe.extension.shared.fixes.slink;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

final class ShareLinkResolver {
    private static final String ORIGIN = "https://www.reddit.com";
    private static final Pattern SHARE_PATH =
            Pattern.compile("/r/[A-Za-z0-9_]+/s/[A-Za-z0-9_-]+/?");
    private static final Pattern PERMALINK_PATH = Pattern.compile(
            "(?:/r/[A-Za-z0-9_]+)?/comments/[A-Za-z0-9]+"
                    + "(?:/[A-Za-z0-9_-]+(?:/[A-Za-z0-9]+)?)?/?");
    private static final Pattern BEARER = Pattern.compile("[A-Za-z0-9._~+/-]+=*");

    private ShareLinkResolver() {}

    static URI shareUri(String link) {
        URI uri = parse(link);
        return isRedditHttps(uri) && SHARE_PATH.matcher(uri.getRawPath()).matches()
                ? URI.create(ORIGIN + uri.getRawPath()) : null;
    }

    static String permalink(String location) {
        URI uri = parse(location);
        if (uri == null) return null;
        if (location.startsWith("/") && !location.startsWith("//")) {
            uri = URI.create(ORIGIN + location);
        }
        if (!isRedditHttps(uri) || !PERMALINK_PATH.matcher(uri.getRawPath()).matches()) {
            return null;
        }
        // Keep only the post/comment route. Do not copy tracking parameters or fragments.
        return ORIGIN + uri.getRawPath();
    }

    static String baconReaderPermalink(String share, String destination) {
        if (!destination.startsWith(ORIGIN + "/comments/")) return destination;
        URI source = shareUri(share);
        if (source == null) return destination;
        // BaconReader's native comment parser requires the /r/<subreddit> prefix.
        String path = source.getRawPath();
        return ORIGIN + path.substring(0, path.indexOf('/', 3)) + destination.substring(ORIGIN.length());
    }

    private static URI parse(String link) {
        if (link == null) return null;
        try {
            return new URI(link);
        } catch (URISyntaxException ignored) {
            return null;
        }
    }

    private static boolean isRedditHttps(URI uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) return false;
        // Match the full raw authority: no user info, ports, encoded hosts, or subdomain wildcards.
        String authority = uri.getRawAuthority();
        return "www.reddit.com".equalsIgnoreCase(authority)
                || "reddit.com".equalsIgnoreCase(authority)
                || "old.reddit.com".equalsIgnoreCase(authority);
    }

    @FunctionalInterface
    interface ConnectionFactory {
        HttpURLConnection open(URI uri) throws IOException;
    }

    static String resolve(String link, String token) throws IOException {
        return resolve(link, token, uri -> (HttpURLConnection) uri.toURL().openConnection());
    }

    static String resolve(String link, String token, ConnectionFactory factory) throws IOException {
        URI uri = shareUri(link);
        if (uri == null) return null;

        HttpURLConnection connection = factory.open(uri);
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2000);
            if (token != null && BEARER.matcher(token).matches()) {
                connection.setRequestProperty("Authorization", "Bearer " + token);
            }
            switch (connection.getResponseCode()) {
                case 301:
                case 302:
                case 303:
                case 307:
                case 308:
                    return permalink(connection.getHeaderField("Location"));
                default:
                    return null;
            }
        } finally {
            connection.disconnect();
        }
    }
}
