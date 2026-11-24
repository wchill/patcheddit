package app.morphe.extension.shared.fixes.slink;

import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ShareLinkResolver {
    private static final String WWW = "www.reddit.com";
    private static final String NP = "np.reddit.com";
    private static final String OAUTH = "oauth.reddit.com";
    private static final Pattern SHARE_PATH = Pattern.compile(
            "/(?:r|u|user)/[A-Za-z0-9_-]+/s/[A-Za-z0-9_-]+/?", Pattern.CASE_INSENSITIVE);
    private static final Pattern BARE_SHARE = Pattern.compile("/s/[A-Za-z0-9_-]+/?", Pattern.CASE_INSENSITIVE);
    private static final String SCOPE = "(/(?:r|u|user)/[A-Za-z0-9_-]+)";
    private static final String POST_ID = "(?:t3_)?([A-Za-z0-9]+)";
    private static final String COMMENT_ID = "(?:t1_)?([A-Za-z0-9]+)";
    private static final Pattern POST = Pattern.compile(SCOPE + "?/comments/" + POST_ID
            + "(?:/([A-Za-z0-9_-]*)(?:/" + COMMENT_ID + ")?)?/?", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALTERNATE_COMMENT = Pattern.compile(SCOPE + "?/comments/" + POST_ID
            + "(?:/([A-Za-z0-9_-]+))?/comment/" + COMMENT_ID + "/?", Pattern.CASE_INSENSITIVE);
    private static final Pattern ID = Pattern.compile(COMMENT_ID, Pattern.CASE_INSENSITIVE);
    private static final Pattern PROFILE = Pattern.compile("/(?:u|user)/([A-Za-z0-9_-]+)/?", Pattern.CASE_INSENSITIVE);
    private static final Pattern GALLERY = Pattern.compile("/gallery/" + POST_ID + "/?", Pattern.CASE_INSENSITIVE);
    private static final Pattern WRAPPER_PATH = Pattern.compile("/(?:[A-Za-z0-9._~+%=-]+/)*[A-Za-z0-9._~+%=-]+/?");
    private static final Pattern MAIL_EMBEDDED = Pattern.compile("/CL0/([^/]+)(?:/.*)?", Pattern.CASE_INSENSITIVE);
    private static final String[] EMBEDDED_TARGETS = {
            "deep_link_value", "$canonical_url", "$deeplink_path", "af_dp", "url", "rdt_cid"
    };
    private static final Pattern BEARER = Pattern.compile("[A-Za-z0-9._~+/-]+=*");
    private static final int MAX_HOPS = 5;

    private ShareLinkResolver() {}

    static URI shareUri(String link) {
        return requestUri(SafeRedirects.httpsUri(link), false);
    }

    public static boolean isJoeyShare(String link) {
        return requestUri(SafeRedirects.httpsUri(link), true) != null;
    }

    /** A canonical comment and the fields expected by Joey's native CommentActivity. */
    public static final class CommentRoute {
        public final String scope;
        public final String submission;
        public final String comment;
        public final int context;
        public final boolean nonParticipation;
        private final String url;

        private CommentRoute(String scope, String submission, String comment, String url) {
            this.scope = scope;
            this.submission = submission;
            this.comment = comment;
            this.url = url;
            URI uri = URI.create(url);
            nonParticipation = SafeRedirects.hasHost(uri, NP);
            String count = functionalValue(uri, "context");
            context = count == null ? -1 : Integer.parseInt(count);
        }

        public String subreddit() {
            if (scope == null) return null;
            int slash = scope.indexOf('/', 1);
            return (scope.startsWith("/r/") ? "" : "u_") + scope.substring(slash + 1);
        }
    }

    public static CommentRoute joeyComment(String link) {
        URI uri = SafeRedirects.httpsUri(link);
        if (!isRedditWeb(uri)) return null;
        CommentRoute route = commentRoute(uri, uri, SafeRedirects.hasHost(uri, NP), true);
        return route != null && route.comment != null ? route : null;
    }

    /** Only the nonstandard direct URLs that Joey's regular dispatcher doesn't already handle. */
    public static String joeyNormalization(String link) {
        URI uri = SafeRedirects.httpsUri(link);
        if (!isRedditWeb(uri)) return null;
        String path = uri.getRawPath();
        if (GALLERY.matcher(path).matches() || path.regionMatches(true, 0, "/u/", 0, 3)
                || (path.regionMatches(true, 0, "/user/", 0, 6) && POST.matcher(path).matches())
                || ALTERNATE_COMMENT.matcher(path).matches() || SafeRedirects.query(uri, "comment") != null) {
            return destination(uri, uri, SafeRedirects.hasHost(uri, NP), true);
        }
        return null;
    }

    static String permalink(String location) {
        URI base = URI.create("https://" + WWW);
        URI uri = SafeRedirects.location(base, location);
        return destination(uri, base, false, false);
    }

    static String baconReaderPermalink(String share, String destination) {
        URI target = SafeRedirects.httpsUri(destination);
        Matcher comment = target == null ? null : POST.matcher(target.getRawPath());
        if (comment == null || !comment.matches()) return destination;
        String scope = comment.group(1);
        if (scope == null) {
            URI source = shareUri(share);
            if (source == null) return destination;
            String path = source.getRawPath();
            scope = path.substring(0, path.toLowerCase(Locale.ROOT).lastIndexOf("/s/"));
        }
        String subreddit = scope.regionMatches(true, 0, "/r/", 0, 3)
                ? scope.substring(3) : "u_" + scope.substring(scope.indexOf('/', 1) + 1);
        return "https://" + target.getHost() + "/r/" + subreddit
                + target.getRawPath().substring(comment.group(1) == null ? 0 : scope.length())
                + querySuffix(target);
    }

    static String resolve(String link, String token, SafeRedirects.ConnectionFactory factory) throws IOException {
        return resolve(link, token, false, factory);
    }

    static String resolve(String link, String token, boolean joey, SafeRedirects.ConnectionFactory factory)
            throws IOException {
        URI source = SafeRedirects.httpsUri(link);
        URI current = requestUri(source, joey);
        if (current == null) return null;
        String authorization = token != null && BEARER.matcher(token).matches() ? "Bearer " + token : null;
        boolean np = SafeRedirects.hasHost(source, NP);
        Set<URI> visited = new HashSet<>();
        SafeRedirects requests = factory == null ? new SafeRedirects() : new SafeRedirects(factory);
        for (int hop = 0; hop < MAX_HOPS && visited.add(current); hop++) {
            URI next;
            if (isRedditWeb(current)) {
                next = redirectShare(requests, current, authorization, joey);
            } else {
                next = embedded(current);
                if (next == null) next = requests.redirect(current, null);
            }
            if (next == null) return null;
            np |= SafeRedirects.hasHost(next, NP);
            String destination = destination(next, source, np, joey);
            if (destination != null) return destination;
            current = requestUri(next, joey);
            if (current == null) return null;
        }
        return null;
    }

    private static URI redirectShare(SafeRedirects requests, URI web, String authorization, boolean joey)
            throws IOException {
        if (authorization != null) {
            // Only requestUri's validated share paths reach here. Never forward a bearer to the web
            // host or follow a Location with the HTTP client's own redirect handling.
            URI oauth = URI.create("https://" + OAUTH + web.getRawPath());
            URI next = null;
            try {
                next = requests.redirect(oauth, authorization);
            } catch (IOException ignored) {
                // The public share endpoint can still work when the OAuth endpoint is unavailable.
            }
            if (SafeRedirects.hasHost(next, OAUTH)) {
                // An API-relative Location inherits the OAuth host, but native navigation is web-based.
                String host = SafeRedirects.hasHost(web, NP) ? NP : WWW;
                return URI.create("https://" + host + next.getRawPath() + querySuffix(next));
            }
            if (next != null && !web.equals(requestUri(next, joey))) return next;
        }
        return requests.redirect(web, null);
    }

    private static URI requestUri(URI uri, boolean joey) {
        if (uri == null) return null;
        String path = uri.getRawPath();
        if (isRedditWeb(uri) && (SHARE_PATH.matcher(path).matches() || joey && BARE_SHARE.matcher(path).matches())) {
            return URI.create("https://" + (SafeRedirects.hasHost(uri, NP) ? NP : WWW) + path);
        }
        if (!joey || !SafeRedirects.hasHost(uri, "s.reddit.com", "applink.reddit.com", "reddit.onelink.me",
                "c.redditmail.com", "click.redditmail.com")) return null;
        if (path.isEmpty() || "/".equals(path)) {
            if (uri.getRawQuery() == null) return null;
            path = "/";
        } else if (!WRAPPER_PATH.matcher(path).matches()) return null;
        boolean mail = SafeRedirects.hasHost(uri, "c.redditmail.com", "click.redditmail.com")
                && MAIL_EMBEDDED.matcher(path).matches();
        String[] segments = path.split("/");
        for (int i = 1; i < segments.length; i++) {
            String decoded = SafeRedirects.decode(segments[i]);
            if (decoded != null && decoded.contains("/") && !(mail && i == 2)) return null;
        }
        return URI.create("https://" + uri.getHost().toLowerCase(Locale.ROOT) + path + querySuffix(uri));
    }

    private static URI embedded(URI wrapper) {
        if (SafeRedirects.hasHost(wrapper, "c.redditmail.com", "click.redditmail.com")) {
            Matcher encoded = MAIL_EMBEDDED.matcher(wrapper.getRawPath());
            if (encoded.matches()) {
                URI candidate = embeddedCandidate(wrapper, SafeRedirects.decode(encoded.group(1)));
                if (candidate != null) return candidate;
            }
        }
        for (String key : EMBEDDED_TARGETS) {
            URI candidate = embeddedCandidate(wrapper, SafeRedirects.query(wrapper, key));
            if (candidate != null) return candidate;
        }
        return null;
    }

    private static URI embeddedCandidate(URI wrapper, String value) {
        if (value == null) return null;
        if (value.startsWith("reddit://reddit/")) value = "/" + value.substring("reddit://reddit/".length());
        else if (value.startsWith("reddit://")) value = "/" + value.substring("reddit://".length());
        URI base = URI.create("https://" + WWW);
        URI candidate = SafeRedirects.location(value.startsWith("/") ? base : wrapper, value);
        if (candidate == null || (!value.startsWith("/") && SafeRedirects.httpsUri(value) == null)) return null;
        return requestUri(candidate, true) != null || destination(candidate, base, false, true) != null
                ? candidate : null;
    }

    private static String destination(URI uri, URI source, boolean np, boolean joey) {
        if (!isRedditWeb(uri)) return null;
        CommentRoute comment = commentRoute(uri, source, np, joey);
        if (comment != null) return comment.url;
        String path = uri.getRawPath();
        String origin = "https://" + (np || SafeRedirects.hasHost(uri, NP) ? NP : WWW);
        Matcher profile = PROFILE.matcher(path);
        if (profile.matches()) {
            return origin + "/user/" + profile.group(1) + (path.endsWith("/") ? "/" : "");
        }
        Matcher gallery = GALLERY.matcher(path);
        if (gallery.matches()) return origin + (joey ? "/comments/" : "/gallery/") + gallery.group(1);
        return null;
    }

    private static CommentRoute commentRoute(URI uri, URI source, boolean np, boolean joey) {
        String path = uri.getRawPath();
        Matcher match = ALTERNATE_COMMENT.matcher(path);
        boolean alternate = match.matches();
        if (!alternate) {
            match = POST.matcher(path);
            if (!match.matches()) return null;
        }
        String scope = match.group(1);
        if (scope != null) {
            int slash = scope.indexOf('/', 1);
            String name = scope.substring(slash + 1);
            if (scope.regionMatches(true, 0, "/r/", 0, 3)) scope = "/r/" + name;
            else scope = joey ? "/r/u_" + name : "/user/" + name;
        }
        String post = match.group(2);
        String slug = match.group(3);
        String comment = match.group(4);
        if (comment == null) comment = id(SafeRedirects.query(uri, "comment"));
        if (comment == null) comment = id(SafeRedirects.query(source, "comment"));
        StringBuilder url = new StringBuilder("https://").append(np ? NP : WWW);
        if (scope != null) url.append(scope);
        url.append("/comments/").append(post);
        if (slug != null && !slug.isEmpty()) url.append('/').append(slug);
        else if (comment != null) url.append("/_");
        if (comment != null) url.append('/').append(comment);
        if (path.endsWith("/")) url.append('/');
        boolean first = true;
        for (String key : new String[]{"context", "depth", "sort"}) {
            String value = functionalValue(uri, key);
            if (value == null) value = functionalValue(source, key);
            if (value == null) continue;
            url.append(first ? '?' : '&').append(key).append('=').append(value);
            first = false;
        }
        return new CommentRoute(scope, post, comment, url.toString());
    }

    private static String id(String value) {
        if (value == null) return null;
        Matcher matcher = ID.matcher(value);
        return matcher.matches() ? matcher.group(1) : null;
    }

    private static String functionalValue(URI uri, String key) {
        String value = SafeRedirects.query(uri, key);
        if (value == null) return null;
        if ("sort".equals(key)) {
            return value.matches("confidence|top|new|controversial|old|random|qa|live|best") ? value : null;
        }
        return value.matches("[0-9]{1,9}") ? value : null;
    }

    private static boolean isRedditWeb(URI uri) {
        return SafeRedirects.hasHost(uri, WWW, NP, "reddit.com", "old.reddit.com",
                "new.reddit.com", "sh.reddit.com", "m.reddit.com");
    }

    private static String querySuffix(URI uri) {
        return uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
    }
}
