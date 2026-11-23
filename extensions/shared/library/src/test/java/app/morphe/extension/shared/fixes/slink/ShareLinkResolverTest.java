package app.morphe.extension.shared.fixes.slink;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ShareLinkResolverTest {
    private static final String SHARE = "https://www.reddit.com/r/Android/s/AbCd123";
    private static final String POST = "https://www.reddit.com/r/Android/comments/abc123/example/";
    private static final String COMMENT = POST + "def456/";

    @Test
    public void invalidAndOrdinaryInputsNeverOpenAConnection() throws IOException {
        for (String link : new String[]{
                null, POST, "https://example.com/r/Android/s/AbCd123",
                "http://www.reddit.com/r/Android/s/AbCd123",
                "https://notreddit.com/r/Android/s/AbCd123",
                "https://www.reddit.com.evil.example/r/Android/s/AbCd123",
                "https://evil.example/?link=" + SHARE,
                "https://www.reddit.com@evil.example/r/Android/s/AbCd123",
                "https://user@www.reddit.com/r/Android/s/AbCd123",
                "https://www.reddit.com:444/r/Android/s/AbCd123",
                "https://www.reddit.com./r/Android/s/AbCd123",
                "https://oauth.reddit.com/r/Android/s/AbCd123",
                "https://www.reddit.com/r/Android/s/",
                "https://www.reddit.com/r/Android/s/a/extra",
                "https://www.reddit.com/r/Android/s/%2e%2e",
                "https://www.reddit.com/r/Android/s/a%2fb",
                "/r/Android/s/AbCd123"
        }) {
            assertNull(link, ShareLinkResolver.resolve(link, "fixture-token", uri -> {
                throw new AssertionError("Unexpected connection to " + uri);
            }));
        }
    }

    @Test
    public void canonicalizesShareAndOpensOnlyOneAuthenticatedHead() throws IOException {
        FixtureConnection response = new FixtureConnection(302,
                "/r/Android/comments/abc123/example/def456/?share_id=tracking#fragment");
        AtomicInteger requests = new AtomicInteger();
        assertEquals(COMMENT, ShareLinkResolver.resolve(
                "HTTPS://Reddit.com/r/Android/s/AbCd123?tracking=123#app", "fixture-token", uri -> {
                    assertEquals(SHARE, uri.toString());
                    requests.incrementAndGet();
                    return response;
                }));
        assertEquals(1, requests.get());
        assertEquals("HEAD", response.getRequestMethod());
        assertEquals("Bearer fixture-token", response.getRequestProperty("Authorization"));
        assertFalse(response.getInstanceFollowRedirects());
        assertEquals(2000, response.getConnectTimeout());
        assertEquals(2000, response.getReadTimeout());
        assertTrue(response.disconnected);
    }

    @Test
    public void onlyRecognizedRedditPermalinksAreHandedBackToNavigation() throws IOException {
        assertEquals(POST, ShareLinkResolver.permalink(POST));
        assertEquals("https://www.reddit.com/comments/abc123",
                ShareLinkResolver.permalink("https://old.reddit.com/comments/abc123"));
        for (String location : new String[]{
                null, "https://example.com/r/Android/comments/abc123/",
                "//www.reddit.com/r/Android/comments/abc123/",
                "http://www.reddit.com/comments/abc123/",
                "https://www.reddit.com@evil.example/comments/abc123/",
                "https://www.reddit.com:443/comments/abc123/",
                "intent://www.reddit.com/comments/abc123/",
                "/r/Android/s/AnotherShare", "/login/", "/api/v1/authorize",
                "/r/Android/../Android/comments/abc123/", "/comments/abc123/%2f/def456/"
        }) {
            FixtureConnection response = new FixtureConnection(307, location);
            assertNull(location, ShareLinkResolver.resolve(SHARE, "fixture-token", uri -> response));
            assertFalse(response.getInstanceFollowRedirects());
            assertTrue(response.disconnected);
        }
    }

    @Test
    public void suppliesSubredditForBaconReaderWhenRedirectOmitsIt() {
        assertEquals(COMMENT, ShareLinkResolver.baconReaderPermalink(SHARE,
                "https://www.reddit.com/comments/abc123/example/def456/"));
        assertEquals(COMMENT, ShareLinkResolver.baconReaderPermalink(SHARE, COMMENT));
        assertEquals("https://np.reddit.com/r/u_Test-User/comments/abc123/example/def456/?context=4",
                ShareLinkResolver.baconReaderPermalink("https://np.reddit.com/u/Test-User/s/ABC",
                        "https://np.reddit.com/comments/abc123/example/def456/?context=4"));
    }

    @Test
    public void preservesNonparticipationAndOnlyFunctionalParametersAcrossHops() throws IOException {
        FixtureConnection first = new FixtureConnection(302, "https://www.reddit.com/user/Alice/s/Second");
        FixtureConnection second = new FixtureConnection(308,
                "/user/Alice/comments/t3_abc123/example/def456/?context=4&depth=1&sort=top&share_id=x#ignored");
        var responses = new ArrayDeque<>(List.of(first, second));
        assertEquals("https://np.reddit.com/user/Alice/comments/abc123/example/def456/?context=4&depth=1&sort=top",
                ShareLinkResolver.resolve("https://np.reddit.com/u/Alice/s/First?context=1", "fixture-token", uri -> {
                    if (responses.size() == 2) {
                        assertEquals("https://np.reddit.com/u/Alice/s/First", uri.toString());
                    } else {
                        assertEquals("https://www.reddit.com/user/Alice/s/Second", uri.toString());
                    }
                    return responses.remove();
                }));
        assertNull(first.getRequestProperty("Authorization"));
        assertNull(second.getRequestProperty("Authorization"));
        assertTrue(responses.isEmpty());
    }

    @Test
    public void fallsBackToGetWithoutForwardingCredentialsToRedirects() throws IOException {
        FixtureConnection first = new FixtureConnection(405, null);
        FixtureConnection second = new FixtureConnection(307, "/r/Android/s/Next");
        FixtureConnection third = new FixtureConnection(302, POST);
        var responses = new ArrayDeque<>(List.of(first, second, third));
        assertEquals(POST, ShareLinkResolver.resolve(SHARE, "fixture-token", uri -> responses.remove()));
        assertEquals("HEAD", first.getRequestMethod());
        assertEquals("GET", second.getRequestMethod());
        assertEquals("HEAD", third.getRequestMethod());
        assertEquals("Bearer fixture-token", first.getRequestProperty("Authorization"));
        assertEquals("Bearer fixture-token", second.getRequestProperty("Authorization"));
        assertNull(third.getRequestProperty("Authorization"));
        for (FixtureConnection connection : List.of(first, second, third)) {
            assertNull(connection.getRequestProperty("Cookie"));
            assertFalse(connection.getInstanceFollowRedirects());
            assertTrue(connection.disconnected);
        }
    }

    @Test
    public void rejectsAmbientJavaCookiesWithoutChangingGlobalState() {
        CookieHandler original = CookieHandler.getDefault();
        CookieManager cookies = new CookieManager();
        CookieHandler.setDefault(cookies);
        try {
            assertThrows(IOException.class, () -> SafeRedirects.openConnection(URI.create(SHARE)));
            assertEquals(cookies, CookieHandler.getDefault());
        } finally {
            CookieHandler.setDefault(original);
        }
    }

    @Test
    public void limitsLoopsAndNeverRequestsUnsafeRedirectHops() throws IOException {
        for (String location : List.of(SHARE, "//www.reddit.com/r/Android/s/Next", "http://www.reddit.com/r/A/s/B",
                "https://elsewhere.test/r/Android/s/Next", "/login/", "/r/A/s/../s/B", "/r/A/s/%2e%2e")) {
            AtomicInteger attempts = new AtomicInteger();
            assertNull(location, ShareLinkResolver.resolve(SHARE, null, uri -> {
                assertEquals(1, attempts.incrementAndGet());
                return new FixtureConnection(302, location);
            }));
        }
        AtomicInteger attempts = new AtomicInteger();
        assertNull(ShareLinkResolver.resolve(SHARE, null, uri -> {
            assertTrue(attempts.get() < 5);
            return new FixtureConnection(308, "/r/Android/s/Hop" + attempts.incrementAndGet());
        }));
        assertEquals(5, attempts.get());
    }

    @Test
    public void recognizesJoeyWrappersAndRestoresExactNativeCommentFields() throws IOException {
        String mail = "https://click.redditmail.com/CL0/https%3A%2F%2Fnp.reddit.com%2Fuser%2FAlice%2Fcomments"
                + "%2Ft3_abc123%2Fcomment%2Ft1_def456%3Fcontext%3D7/123";
        String result = ShareLinkResolver.resolve(mail, "fixture-token", true, uri -> {
            throw new AssertionError("Embedded destination should not open a connection");
        });
        assertEquals("https://np.reddit.com/r/u_Alice/comments/abc123/_/def456?context=7", result);
        ShareLinkResolver.CommentRoute route = ShareLinkResolver.joeyComment(result);
        assertEquals("u_Alice", route.subreddit());
        assertEquals("abc123", route.submission);
        assertEquals("def456", route.comment);
        assertEquals(7, route.context);
        assertTrue(route.nonParticipation);

        assertEquals("https://www.reddit.com/comments/abc123",
                ShareLinkResolver.resolve("https://reddit.onelink.me/Fixture?af_dp=reddit%3A%2F%2Fgallery%2Ft3_abc123",
                        null, true, uri -> { throw new AssertionError(uri); }));
        assertEquals("https://www.reddit.com/user/Alice/", ShareLinkResolver.joeyNormalization("https://reddit.com/u/Alice/"));
        assertNull(ShareLinkResolver.joeyNormalization(POST));
        assertNull(ShareLinkResolver.resolve("https://www.reddit.com/s/Bare", null, uri -> {
            throw new AssertionError("Bare share support is Joey-only");
        }));
    }

    @Test
    public void neverSendsRedditCredentialsOnWrapperChains() throws IOException {
        FixtureConnection wrapper = new FixtureConnection(302, "https://www.reddit.com/s/Bare");
        FixtureConnection share = new FixtureConnection(302, POST);
        var responses = new ArrayDeque<>(List.of(wrapper, share));
        assertEquals(POST, ShareLinkResolver.resolve("https://s.reddit.com/Fixture", "fixture-token", true, uri ->
                responses.remove()));
        assertNull(wrapper.getRequestProperty("Authorization"));
        assertNull(share.getRequestProperty("Authorization"));
        for (String bad : List.of("https://notreddit.onelink.me/Fixture", "https://applink.reddit.com:443/Fixture",
                "https://reddit.onelink.me/r%2Fa%2Fsecret", "http://click.redditmail.com/Fixture")) {
            assertNull(ShareLinkResolver.resolve(bad, null, true, uri -> { throw new AssertionError(uri); }));
        }
    }

    @Test
    public void doesNotReuseAnAccountDependentDestinationAfterAnAccountChange() throws IOException {
        FixtureConnection first = new FixtureConnection(302, POST);
        FixtureConnection second = new FixtureConnection(302, "/r/Android/comments/other123/");
        assertEquals(POST, ShareLinkResolver.resolve(SHARE, "first-account", uri -> first));
        assertEquals("https://www.reddit.com/r/Android/comments/other123/",
                ShareLinkResolver.resolve(SHARE, "second-account", uri -> second));
        assertEquals("Bearer first-account", first.getRequestProperty("Authorization"));
        assertEquals("Bearer second-account", second.getRequestProperty("Authorization"));
    }

    @Test
    public void omitsMissingOrMalformedTokenAndIgnoresLocationOnNonRedirect() throws IOException {
        for (String token : new String[]{null, "", "malformed\r\nheader"}) {
            FixtureConnection response = new FixtureConnection(200, POST);
            assertNull(ShareLinkResolver.resolve(SHARE, token, uri -> response));
            assertNull(response.getRequestProperty("Authorization"));
            assertTrue(response.disconnected);
        }
    }

    @Test
    public void disconnectsOnNetworkFailure() throws IOException {
        FixtureConnection connection = new FixtureConnection(302, POST) {
            @Override
            public int getResponseCode() throws IOException {
                throw new IOException("fixture network failure");
            }
        };
        assertThrows(IOException.class, () -> ShareLinkResolver.resolve(SHARE, null, uri -> connection));
        assertTrue(connection.disconnected);
    }

    private static class FixtureConnection extends HttpURLConnection {
        private final int code;
        private final String location;
        private boolean disconnected;

        FixtureConnection(int code, String location) throws IOException {
            super(URI.create(SHARE).toURL());
            this.code = code;
            this.location = location;
        }

        @Override
        public void connect() {}

        @Override
        public int getResponseCode() throws IOException { return code; }

        @Override
        public String getHeaderField(String name) { return "Location".equals(name) ? location : null; }

        @Override
        public void disconnect() { disconnected = true; }

        @Override
        public boolean usingProxy() { return false; }
    }
}
