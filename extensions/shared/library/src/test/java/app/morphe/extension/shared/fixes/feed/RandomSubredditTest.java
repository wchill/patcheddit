/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.fixes.feed;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.*;

public class RandomSubredditTest {
    private static final URI RANDOM = URI.create("https://oauth.reddit.com/r/random/new.json?limit=1&raw_json=1");
    private static final String TOKEN = "Bearer fixture";
    private final RandomSubreddit.Resolver resolver = new RandomSubreddit.Resolver(new Random(5));

    @Test
    public void searchPrefersSmallerSafeCommunitiesAndSkipsThePreviousPick() throws Exception {
        String results = listing(community("Large", 90_000, false, "public"),
                community("Small", 500, false, "public"),
                community("Private", 50, false, "private"),
                community("Huge", 100_001, false, "public"),
                community("TooTiny", 49, false, "public"),
                community("Mature", 500, true, "public"));
        RandomSubreddit.Transport transport = (uri, token, agent, timeout) -> {
            assertEquals("oauth.reddit.com", uri.getHost());
            assertEquals("/subreddits/search.json", uri.getPath());
            assertTrue(uri.getQuery().contains("include_over_18=false"));
            assertEquals(TOKEN, token);
            assertEquals("fixture-agent", agent);
            return results;
        };
        assertEquals("/r/Small/new.json", resolver.rewrite(RANDOM, TOKEN, "fixture-agent", false, false, transport).getPath());
        URI second = resolver.rewrite(RANDOM, TOKEN, "fixture-agent", false, false, transport);
        assertEquals("/r/Large/new.json", second.getPath());
        assertEquals(RANDOM.getRawQuery(), second.getRawQuery());
    }

    @Test
    public void searchRecoversFromEmptyResultsWithoutRepeatingAnUnproductiveQuery() throws Exception {
        List<String> queries = new ArrayList<>();
        URI selected = resolver.rewrite(RANDOM, TOKEN, null, false, false, (uri, token, agent, timeout) -> {
            assertEquals("oauth.reddit.com", uri.getHost());
            String query = uri.getRawQuery().replaceFirst("^.*&q=", "");
            // Model Reddit's empty results for short prefixes and some valid topic searches.
            if (query.length() < 3 || queries.contains(query)) return listing();
            queries.add(query);
            return queries.size() < 3 ? listing()
                    : listing(community("FixtureTopic", 750, false, "public"));
        });
        assertEquals(3, queries.size());
        assertEquals("/r/FixtureTopic/new.json", selected.getPath());
    }

    @Test
    public void redditRandDoesNotReceiveCredentialsAndRedditVerifiesTheCommunity() throws Exception {
        List<String> paths = new ArrayList<>();
        URI original = URI.create("https://oauth.reddit.com/r/random/comments.json?limit=1");
        URI resolved = resolver.rewrite(original, TOKEN, "fixture-agent", true, false, (uri, token, agent, timeout) -> {
            paths.add(uri.getHost() + uri.getPath());
            if (uri.getHost().equals("api.redditrand.com")) {
                assertNull(token);
                assertNull(agent);
                assertEquals("nsfw=false", uri.getQuery());
                return paths.size() == 1 ? "{\"url\":\"https://unrelated.example/r/Example/\"}"
                        : "{\"url\":\"/r/Example/\"}";
            }
            assertEquals(TOKEN, token);
            return new JSONObject(Map.of("data", community("Example", 2_000, false, "public"))).toString();
        });
        assertEquals("https://oauth.reddit.com/r/Example/comments.json?limit=1", resolved.toString());
        assertEquals(List.of("api.redditrand.com/reddit-runner/rand", "api.redditrand.com/reddit-runner/rand",
                "oauth.reddit.com/r/Example/about.json"), paths);
    }

    @Test
    public void matureRandomRequiresExplicitRouteAndRespectsTheLock() throws Exception {
        URI nsfw = URI.create("https://oauth.reddit.com/r/randNSFW/.json");
        assertThrows(IOException.class, () -> resolver.rewrite(nsfw, TOKEN, null, false, true,
                (uri, token, agent, timeout) -> {
                    throw new AssertionError("Mature Content Lock must not trigger mature discovery");
                }));
        URI selected = resolver.rewrite(nsfw, TOKEN, null, false, false, (uri, token, agent, timeout) -> {
            assertTrue(uri.getQuery().contains("include_over_18=true"));
            return listing(community("Safe", 100, false, "public"), community("Mature", 500, true, "public"));
        });
        assertEquals("/r/Mature/.json", selected.getPath());
    }

    @Test
    public void leavesUnrelatedRequestsAloneButDoesNotForwardUnresolvedPagination() throws Exception {
        assertFalse(RandomSubreddit.matches("POST", RANDOM));
        for (String url : List.of("https://example.org/r/random", "http://www.reddit.com/r/random",
                "https://oauth.reddit.com/r/randomsub", "https://oauth.reddit.com/r/random/comments/abc",
                "https://api.reddit.com:444/r/random")) {
            URI path = URI.create(url);
            assertEquals(path, resolver.rewrite(path, TOKEN, null, false, false, (uri, token, agent, timeout) -> {
                throw new AssertionError("Unrelated request attempted a lookup");
            }));
        }
        URI paged = URI.create("https://oauth.reddit.com/r/random?after=t3_fixture");
        assertThrows(IOException.class, () -> resolver.rewrite(paged, TOKEN, null, false, false,
                (uri, token, agent, timeout) -> {
                    throw new AssertionError("An unresolved cursor must not trigger a new lookup");
                }));
    }

    @Test
    public void boundsEmptySearchesAndDoesNotRetryRateLimits() {
        List<URI> requests = new ArrayList<>();
        IOException empty = assertThrows(IOException.class, () -> resolver.rewrite(RANDOM, TOKEN, null, false, false,
                (uri, token, agent, timeout) -> {
                    requests.add(uri);
                    return listing();
                }));
        assertEquals("No eligible random subreddit was found", RandomSubreddit.failureReport(empty).getMessage());
        assertEquals(4, requests.size());
        requests.clear();
        IOException limited = assertThrows(IOException.class, () -> resolver.rewrite(RANDOM, TOKEN, null, false, false,
                (uri, token, agent, timeout) -> {
                    requests.add(uri);
                    throw new RandomSubreddit.HttpFailure(429);
                }));
        assertTrue(RandomSubreddit.failureReport(limited).getMessage().contains("HTTP 429"));
        assertEquals(1, requests.size());
    }

    @Test
    public void doesNotPersistRawTransportOrJsonExceptionMessages() throws Exception {
        IOException transport = assertThrows(IOException.class, () -> resolver.rewrite(RANDOM, TOKEN, null,
                false, false, (uri, token, agent, timeout) -> {
                    throw new java.net.SocketTimeoutException("fixture-secret-url");
                }));
        IOException report = RandomSubreddit.failureReport(transport);
        assertEquals("Lookup failed: SocketTimeoutException", report.getMessage());
        assertNull(report.getCause());

        report = RandomSubreddit.failureReport(new JSONException("fixture-private-response"));
        assertEquals("Lookup returned invalid JSON.", report.getMessage());
        assertNull(report.getCause());
    }

    private static JSONObject community(String name, int subscribers, boolean adult, String type) {
        return new JSONObject(Map.of("display_name", name, "subscribers", subscribers,
                "over18", adult, "subreddit_type", type));
    }

    private static String listing(JSONObject... entries) {
        JSONArray children = new JSONArray();
        for (JSONObject entry : entries) children.put(new JSONObject(Map.of("kind", "t5", "data", entry)));
        return new JSONObject(Map.of("data", new JSONObject(Map.of("children", children)))).toString();
    }
}
