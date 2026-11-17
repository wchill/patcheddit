/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.settings;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PatchedditReleaseTest {
    private URL url;
    private HttpURLConnection connection;
    private String response = "{\"version\":\"2.4.0\"}";
    private int status = 200;
    private boolean disconnected;
    private boolean timeout;

    @Before
    public void connection() throws IOException {
        url = new URL(null, "https://example.test/patches-bundle.json", new URLStreamHandler() {
            @Override
            protected HttpURLConnection openConnection(URL url) {
                disconnected = false;
                connection = new HttpURLConnection(url) {
                    @Override public int getResponseCode() throws IOException {
                        if (timeout) throw new java.net.SocketTimeoutException("Read timed out");
                        return status;
                    }
                    @Override public InputStream getInputStream() throws IOException {
                        return new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8));
                    }
                    @Override public void connect() {}
                    @Override public void disconnect() { disconnected = true; }
                    @Override public boolean usingProxy() { return false; }
                };
                return connection;
            }
        });
    }

    @Test
    public void readsPublishedVersionFromManifest() throws IOException {
        assertEquals("2.4.0", PatchedditRelease.fetch(url));
        assertTrue(disconnected);
        assertTrue(connection.getConnectTimeout() > 0);
        assertTrue(connection.getReadTimeout() > 0);
        assertFalse(connection.getUseCaches());
        response = "{\"version\":\"2.4.0-dev.10\",\"description\":\"Release notes\"}";
        assertEquals("2.4.0-dev.10", PatchedditRelease.fetch(url));
    }

    @Test
    public void rejectsHttpErrorsInsteadOfReportingCurrent() {
        status = 503;
        IOException error = assertThrows(IOException.class, () -> PatchedditRelease.fetch(url));
        assertTrue(error.getMessage().contains("503"));
        assertTrue(disconnected);
    }

    @Test
    public void rejectsMalformedOrMissingReleaseData() {
        for (String invalid : new String[]{"not json", "{}", "{\"version\":\"latest\"}",
                "{\"version\":\"2.4.0-dev.01\"}"}) {
            response = invalid;
            assertThrows(IOException.class, () -> PatchedditRelease.fetch(url));
            assertTrue(disconnected);
        }
    }

    @Test
    public void propagatesNetworkFailuresAndDisconnects() {
        timeout = true;
        assertThrows(java.net.SocketTimeoutException.class, () -> PatchedditRelease.fetch(url));
        assertTrue(disconnected);
    }

    @Test
    public void comparesNumericVersionsWithoutLexicalOrderingOrDowngrades() {
        assertTrue(PatchedditRelease.compare("2.10.0", "2.9.9") > 0);
        assertTrue(PatchedditRelease.compare("2.4.0", "2.4.1") < 0);
        assertEquals(0, PatchedditRelease.compare("v2.3.0", "2.3.0+build.1"));
    }

    @Test
    public void ordersPrereleasesAndStableVersionsSemantically() {
        String[] versions = {"2.4.0-alpha", "2.4.0-alpha.1", "2.4.0-alpha.beta",
                "2.4.0-beta", "2.4.0-dev.2", "2.4.0-dev.10", "2.4.0-rc.1", "2.4.0"};
        for (int index = 1; index < versions.length; index++) {
            assertTrue(PatchedditRelease.compare(versions[index], versions[index - 1]) > 0);
            assertTrue(PatchedditRelease.compare(versions[index - 1], versions[index]) < 0);
        }
        assertTrue(PatchedditRelease.compare("2.5.0-dev.1", "2.4.0") > 0);
        assertEquals(0, PatchedditRelease.compare("2.4.0-dev.10", "2.4.0-dev.10+commit"));
    }

    @Test
    public void distinguishesPrereleasesFromBuildMetadata() {
        assertTrue(PatchedditRelease.isPreRelease("2.4.0-dev.1"));
        assertFalse(PatchedditRelease.isPreRelease("2.4.0+local-build"));
    }
}
