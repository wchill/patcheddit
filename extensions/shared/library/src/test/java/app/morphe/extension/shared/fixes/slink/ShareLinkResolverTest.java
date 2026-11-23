package app.morphe.extension.shared.fixes.slink;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
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
