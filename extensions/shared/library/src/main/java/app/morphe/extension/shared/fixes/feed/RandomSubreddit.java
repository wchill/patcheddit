/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.fixes.feed;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.debug.ExceptionHook;
import app.morphe.extension.shared.fixes.slink.SafeRedirects;
import app.morphe.extension.shared.settings.MatureContentLock;
import app.morphe.extension.shared.settings.PatchSettings;

public final class RandomSubreddit {
    public static final String PROVIDER_KEY = "morphe_random_subreddit_provider";
    static final String REDDIT_SEARCH = "reddit_search";
    static final String REDDIT_RAND = "redditrand";
    static final String DEFAULT_PROVIDER = REDDIT_RAND;
    private static final String OAUTH = "https://oauth.reddit.com";
    private static final String PUBLIC = "https://www.reddit.com";
    private static final String REDDIT_RAND_API = "https://api.redditrand.com/reddit-runner/rand?nsfw=";
    private static final Pattern NAME = Pattern.compile("[a-zA-Z0-9_]{2,21}");
    private static final Pattern ALIAS = Pattern.compile(
            "/r/(random|randnsfw)((?:/(?:best|hot|new|rising|top|controversial|comments))?/?(?:\\.json)?)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMUNITY = Pattern.compile("/r/([a-zA-Z0-9_]{2,21})/?");
    private static final List<String> SEEDS = List.of("art", "books", "camping", "cooking", "crafts",
            "cycling", "design", "drawing", "games", "garden", "history", "hobby", "local", "music",
            "nature", "pets", "photo", "science", "sports", "travel", "vintage");
    private static final int TIMEOUT_MS = 5500;
    private static final int ATTEMPTS = 4;
    private static final int HISTORY_SIZE = 24;
    private static final int MAX_BYTES = 768 * 1024;
    private static final Resolver RESOLVER = new Resolver(new Random());

    private RandomSubreddit() {}

    @FunctionalInterface
    interface Transport {
        String get(URI uri, String authorization, String userAgent, int timeoutMs) throws IOException;
    }

    static boolean matches(String method, URI url) {
        return "GET".equals(method) && alias(url) != null;
    }

    public static boolean matchesNativeRequest(String method, String url) {
        if (!"GET".equals(method) || url == null || !url.startsWith(OAUTH + "/r/")) return false;
        URI uri = SafeRedirects.httpsUri(url);
        return SafeRedirects.hasHost(uri, "oauth.reddit.com") && alias(uri) != null;
    }

    /** Infinity keeps its own OkHttp. Run after its normal token interceptor supplies the bearer. */
    public static String rewriteAuthenticated(String url, String authorization, String userAgent) throws IOException {
        return rewrite(SafeRedirects.httpsUri(url), authorization, userAgent, RandomSubreddit::fetchNative)
                .toASCIIString();
    }

    /** Infinity normally resolves the alias once its token interceptor has run. */
    public static void rejectUnresolvedRequest(String method, String url) throws IOException {
        if (matches(method, SafeRedirects.httpsUri(url))) {
            throw reportFailure(new IOException("No Reddit session was available for Random."), providerUsesRedditRand());
        }
    }

    public static boolean hasBearer(String authorization) {
        return authorization != null && authorization.length() > 7
                && authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                && !"null".equalsIgnoreCase(authorization.substring(7));
    }

    private static String fetchNative(URI uri, String authorization, String agent, int timeoutMs) throws IOException {
        HttpURLConnection connection = SafeRedirects.openConnection(uri);
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(timeoutMs / 2 + 1);
            connection.setReadTimeout(timeoutMs / 2 + 1);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            if (authorization != null) connection.setRequestProperty("Authorization", authorization);
            if (agent != null) connection.setRequestProperty("User-Agent", agent);
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) throw new HttpFailure(code);
            try (InputStream input = connection.getInputStream()) {
                return read(input);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static Matcher alias(URI uri) {
        if (!SafeRedirects.hasHost(uri, "www.reddit.com", "reddit.com", "oauth.reddit.com",
                "api.reddit.com", "old.reddit.com", "np.reddit.com") || !isPlainHttps(uri)) return null;
        Matcher match = ALIAS.matcher(uri.getRawPath());
        return match.matches() ? match : null;
    }

    private static boolean isPlainHttps(URI uri) {
        return uri != null && "https".equalsIgnoreCase(uri.getScheme()) && uri.getPort() == -1
                && uri.getRawUserInfo() == null && uri.getRawPath() != null;
    }

    static URI rewrite(URI original, String authorization, String userAgent, Transport transport) throws IOException {
        boolean redditRand = providerUsesRedditRand();
        try {
            return RESOLVER.rewrite(original, authorization, userAgent, redditRand, MatureContentLock.isLocked(),
                    transport);
        } catch (UnavailableRandomRoute unavailable) {
            throw unavailable;
        } catch (IOException | JSONException failure) {
            throw reportFailure(failureReport(failure), redditRand);
        }
    }

    private static boolean providerUsesRedditRand() {
        String provider = Utils.isContextSet() ? PatchSettings.preferences(Utils.getContext())
                .getString(PROVIDER_KEY, DEFAULT_PROVIDER) : DEFAULT_PROVIDER;
        return REDDIT_RAND.equals(provider);
    }

    private static IOException reportFailure(IOException failure, boolean redditRand) {
        ExceptionHook.e("RandomSubreddit", "Random subreddit lookup (" + (redditRand ? "RedditRand"
                : "Reddit search") + ")", failure);
        return failure;
    }

    static IOException failureReport(Exception failure) {
        Throwable detail = failure.getCause() == null ? failure : failure.getCause();
        String message;
        if (detail instanceof HttpFailure) {
            message = detail.getMessage();
        } else if (detail instanceof JSONException) {
            message = "Lookup returned invalid JSON.";
        } else if (failure instanceof NoEligibleSubreddit && detail == failure) {
            message = failure.getMessage();
        } else {
            message = "Lookup failed: " + detail.getClass().getSimpleName();
        }
        // Transport and parser exceptions can contain request details or snippets of the response.
        IOException report = new IOException(message);
        report.setStackTrace(detail.getStackTrace());
        return report;
    }

    static String read(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int count; (count = input.read(buffer)) != -1; ) {
            if (count > MAX_BYTES - output.size()) throw new IOException("Random subreddit response is too large");
            output.write(buffer, 0, count);
        }
        return output.toString("UTF-8");
    }

    static final class Resolver {
        private final Random random;
        private final ArrayDeque<String> recent = new ArrayDeque<>();

        Resolver(Random random) {
            this.random = random;
        }

        URI rewrite(URI original, String authorization, String userAgent, boolean redditRand,
                    boolean locked, Transport transport) throws IOException, JSONException {
            Matcher match = alias(original);
            if (match == null) return original;
            boolean adult = "randnsfw".equalsIgnoreCase(match.group(1));
            if (locked && adult) throw new UnavailableRandomRoute("Mature Content Lock blocks this Random request.");
            if (SafeRedirects.query(original, "after") != null || SafeRedirects.query(original, "before") != null) {
                throw new UnavailableRandomRoute("Open Random again before loading more posts.");
            }
            String bearer = SafeRedirects.hasHost(original, "oauth.reddit.com") && hasBearer(authorization)
                    ? authorization : null;
            String reddit = bearer == null ? PUBLIC : OAUTH;
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MS);
            Map<String, Candidate> candidates = new LinkedHashMap<>();
            List<String> seeds = redditRand ? List.of() : new ArrayList<>(SEEDS);
            IOException lastFailure = null;
            for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
                try {
                    if (redditRand) {
                        JSONObject reply = fetch(transport, REDDIT_RAND_API + adult, null, null, deadline);
                        URI path = SafeRedirects.location(URI.create(PUBLIC), reply.optString("url"));
                        if (!SafeRedirects.hasHost(path, "www.reddit.com", "reddit.com")) continue;
                        Matcher community = COMMUNITY.matcher(path.getRawPath());
                        if (!community.matches() || !validName(community.group(1))) continue;
                        String name = community.group(1);
                        JSONObject about = fetch(transport, reddit + "/r/" + name + "/about.json?raw_json=1",
                                bearer, userAgent, deadline).optJSONObject("data");
                        Candidate candidate = candidate(about, adult, false);
                        if (candidate == null || !name.equalsIgnoreCase(candidate.name)) continue;
                        candidates.putIfAbsent(name.toLowerCase(Locale.ROOT), candidate);
                    } else {
                        // Reddit search can return empty listings for arbitrary two-letter prefixes.
                        String seed = seeds.remove(random.nextInt(seeds.size()));
                        String path = "/subreddits/search.json?raw_json=1&limit=50&sort=relevance&include_over_18="
                                + adult + "&q=" + seed;
                        JSONObject listing = fetch(transport, reddit + path, bearer, userAgent, deadline)
                                .optJSONObject("data");
                        JSONArray children = listing == null ? null : listing.optJSONArray("children");
                        for (int i = 0; children != null && i < children.length(); i++) {
                            JSONObject child = children.optJSONObject(i);
                            if (child == null || !"t5".equals(child.optString("kind"))) continue;
                            Candidate candidate = candidate(child.optJSONObject("data"), adult, true);
                            if (candidate != null) candidates.putIfAbsent(candidate.name.toLowerCase(Locale.ROOT),
                                    candidate);
                        }
                    }
                    String name = select(candidates, false);
                    if (name != null) return replace(original, match, name);
                } catch (IOException failure) {
                    lastFailure = failure;
                    if (failure instanceof HttpFailure && ((HttpFailure) failure).terminal()) break;
                    if (System.nanoTime() >= deadline) break;
                }
            }
            String fallback = select(candidates, true);
            if (fallback != null) return replace(original, match, fallback);
            throw new NoEligibleSubreddit(lastFailure);
        }

        private synchronized String select(Map<String, Candidate> candidates, boolean allowRepeat) {
            List<Candidate> preferred = new ArrayList<>();
            List<Candidate> others = new ArrayList<>();
            for (Map.Entry<String, Candidate> entry : candidates.entrySet()) {
                if (!allowRepeat && recent.contains(entry.getKey())) continue;
                Candidate candidate = entry.getValue();
                (candidate.subscribers < 50_000 ? preferred : others).add(candidate);
            }
            List<Candidate> pool = preferred.isEmpty() ? others : preferred;
            if (pool.isEmpty()) return null;
            String name = pool.get(random.nextInt(pool.size())).name;
            String key = name.toLowerCase(Locale.ROOT);
            recent.remove(key);
            recent.addLast(key);
            if (recent.size() > HISTORY_SIZE) recent.removeFirst();
            return name;
        }
    }

    private static JSONObject fetch(Transport transport, String url, String bearer, String userAgent, long deadline)
            throws IOException, JSONException {
        long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
        if (remaining <= 0) throw new IOException("Random subreddit lookup timed out");
        return new JSONObject(transport.get(URI.create(url), bearer, userAgent, (int) Math.min(remaining, 2000)));
    }

    private static URI replace(URI original, Matcher alias, String name) {
        String suffix = alias.group(2);
        if (!suffix.toLowerCase(Locale.ROOT).endsWith(".json")) {
            suffix += suffix.endsWith("/") || !suffix.isEmpty() ? ".json" : "/.json";
        }
        String query = original.getRawQuery();
        return URI.create(original.getScheme() + "://" + original.getRawAuthority() + "/r/" + name + suffix
                + (query == null ? "" : "?" + query));
    }

    private static boolean validName(String name) {
        return NAME.matcher(name).matches() && !name.matches("(?i)random|randnsfw|all|popular|friends|mod");
    }

    private static Candidate candidate(JSONObject data, boolean adult, boolean restrictSize) {
        if (data == null || !(data.opt("over18") instanceof Boolean)
                || data.optBoolean("over18") != adult
                || !"public".equals(data.optString("subreddit_type"))) return null;
        String name = data.optString("display_name");
        int size = data.optInt("subscribers", -1);
        if (!validName(name) || restrictSize && (size < 50 || size > 100_000)) return null;
        return new Candidate(name, size);
    }

    private static final class Candidate {
        final String name;
        final int subscribers;

        Candidate(String name, int subscribers) {
            this.name = name;
            this.subscribers = subscribers;
        }
    }

    static final class HttpFailure extends IOException {
        private final int code;

        HttpFailure(int code) {
            super("Random subreddit lookup returned HTTP " + code);
            this.code = code;
        }

        boolean terminal() {
            return code == 401 || code == 403 || code == 429;
        }
    }

    private static final class NoEligibleSubreddit extends IOException {
        NoEligibleSubreddit(IOException cause) {
            super("No eligible random subreddit was found", cause);
        }
    }

    private static final class UnavailableRandomRoute extends IOException {
        UnavailableRandomRoute(String message) {
            super(message);
        }
    }
}
