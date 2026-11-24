package app.morphe.extension.redditisfun.media;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class RifMediaLinksTest {
    private static final String MATRIX = "https://matrix.redditspace.com/_matrix/media/v3/download/reddit.com/Fixture";
    private static final String IMAGE = "https://preview.redd.it/fixture.png?width=1280&s=signed_value";

    @Test
    public void unwrapsAllowedImagesAndMatrixDownloadsWithoutLosingSignedImageQuery() throws IOException {
        assertEquals(URI.create(IMAGE), RifMediaLinks.target(wrap(IMAGE + "#discard")));
        assertEquals(URI.create(MATRIX), RifMediaLinks.target(wrap(MATRIX)));
        assertEquals(URI.create(MATRIX), RifMediaLinks.target(MATRIX + "#discard"));
        for (String invalid : List.of("https://i.redd.it.evil.example/fixture.png",
                "https://i.redd.it:443/fixture.png", "https://i.redd.it/fixture.svg",
                "https://i.redd.it/images/fixture.png", "https://i.redd.it/fixture.png%2F..",
                "https://i.redd.it/fixture.php", "https://matrix.redditspace.com/_matrix/media/v3/download/evil/Fixture",
                "http://i.redd.it/fixture.png", "https://user@i.redd.it/fixture.png")) {
            assertNull(invalid, RifMediaLinks.target(wrap(invalid)));
        }
        assertNull(RifMediaLinks.target(wrap(IMAGE) + "&url=" + IMAGE));
        assertNull(RifMediaLinks.target(IMAGE)); // Leave direct, already supported images to stock.
        assertNull(RifMediaLinks.target("https://unrelated.example/media?url=" + IMAGE));
    }

    @Test
    public void resolvesExtensionlessMatrixWithHeadThenGetWithoutFetchingTheImageItself() throws IOException {
        List<String> methods = new ArrayList<>();
        URI matrix = URI.create(MATRIX);
        assertEquals(URI.create(IMAGE), RifMediaLinks.resolve(matrix, uri -> {
            assertEquals(matrix, uri);
            return new HttpURLConnection(uri.toURL()) {
                @Override public int getResponseCode() {
                    methods.add(getRequestMethod());
                    assertNull(getRequestProperty("Authorization"));
                    assertNull(getRequestProperty("Cookie"));
                    assertFalse(getInstanceFollowRedirects());
                    return "HEAD".equals(getRequestMethod()) ? 405 : 302;
                }
                @Override public String getHeaderField(String key) { return "Location".equals(key) ? IMAGE : null; }
                @Override public void disconnect() {}
                @Override public boolean usingProxy() { return false; }
                @Override public void connect() {}
            };
        }));
        assertEquals(List.of("HEAD", "GET"), methods);
    }

    @Test
    public void neverFollowsUntrustedMatrixLocationsAndCapsSafeHops() throws IOException {
        URI matrix = URI.create(MATRIX);
        for (String invalid : List.of(MATRIX, "//i.redd.it/fixture.png", "http://i.redd.it/fixture.png",
                "https://off-origin.example/fixture.png", "https://i.redd.it/fixture.png%2Fsomething")) {
            var requests = new ArrayList<URI>();
            assertNull(RifMediaLinks.resolve(matrix, uri -> {
                requests.add(uri);
                return redirect(uri, invalid);
            }));
            assertEquals(List.of(matrix), requests);
        }
        var requests = new ArrayList<URI>();
        assertNull(RifMediaLinks.resolve(matrix, uri -> {
            requests.add(uri);
            return redirect(uri, MATRIX + requests.size());
        }));
        assertEquals(List.of(matrix, URI.create(MATRIX + "1"), URI.create(MATRIX + "2")), requests);
    }

    private static String wrap(String target) throws IOException {
        return "https://np.reddit.com/media?url=" + URLEncoder.encode(target, "UTF-8");
    }

    private static HttpURLConnection redirect(URI uri, String target) throws IOException {
        return new HttpURLConnection(uri.toURL()) {
            @Override public int getResponseCode() { return 302; }
            @Override public String getHeaderField(String key) { return "Location".equals(key) ? target : null; }
            @Override public void disconnect() {}
            @Override public boolean usingProxy() { return false; }
            @Override public void connect() {}
        };
    }
}
