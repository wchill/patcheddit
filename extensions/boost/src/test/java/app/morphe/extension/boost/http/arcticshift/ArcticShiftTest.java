/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.boost.http.arcticshift;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.IOException;

import okhttp3.HttpUrl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ArcticShiftTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void emptySuccessfulLookupIsAnArchiveMiss() throws Exception {
        assertEquals(0, ArcticShift.idsData(mapper.readTree("{\"data\":[]}")).size());
        assertEquals(1, ArcticShift.idsData(mapper.readTree("{\"data\":[{\"id\":\"abc\"}]}")).size());
    }

    @Test
    public void errorsAndMalformedResponsesAreNotArchiveMisses() throws Exception {
        for (String json : new String[] {
                "null", "{}", "{\"data\":null}", "{\"data\":{}}",
                "{\"error\":\"Timeout\"}", "{\"data\":[],\"error\":\"Timeout\"}"
        }) {
            assertThrows(json, IOException.class, () -> ArcticShift.idsData(mapper.readTree(json)));
        }
        assertThrows(IOException.class, () -> ArcticShift.idsData(null));
    }

    @Test
    public void normalizesCompatibleApiUrlsWithoutLosingTheirPathOrPort() {
        assertEquals(ArcticShiftSettings.DEFAULT_BASE_URL, ArcticShiftSettings.normalizeBaseUrl(" \t "));
        assertEquals("https://archive.example/custom/api/",
                ArcticShiftSettings.normalizeBaseUrl(" https://archive.example/custom/api "));
        assertEquals("http://192.168.1.2:8080/api/",
                ArcticShiftSettings.normalizeBaseUrl("http://192.168.1.2:8080/api/"));
        assertEquals("http://[::1]:8080/",
                ArcticShiftSettings.normalizeBaseUrl("http://[::1]:8080"));
        assertEquals("https://archive.example/api%20v1/",
                ArcticShiftSettings.normalizeBaseUrl("https://archive.example/api%20v1"));
    }

    @Test
    public void rejectsUrlsThatWouldMisrouteRequestsOrExposeCredentials() {
        for (String value : new String[] {
                "archive.example/api", "file:///api/", "ftp://archive.example/api",
                "https://user:password@archive.example/api", "https://archive.example/api?key=value",
                "https://archive.example/api#fragment", "https://archive.example:65536/api",
                "https://archive.example:0/api", "https://archive.example/invalid path",
                "https://archive.example/\napi", "https://"
        }) {
            assertThrows(value, IllegalArgumentException.class,
                    () -> ArcticShiftSettings.normalizeBaseUrl(value));
        }
    }

    @Test
    public void throttlesOnlyRequestsUnderTheConfiguredApiOriginAndPath() {
        String base = "http://archive.example:8080/custom/api/";
        assertTrue(ArcticShiftSettings.isApiUrl(
                HttpUrl.get(base + "comments/ids?ids=abc"), base));
        for (String url : new String[] {
                "https://archive.example:8080/custom/api/comments/ids",
                "http://archive.example/custom/api/comments/ids",
                "http://archive.example:8080/custom/api-other/comments/ids",
                "http://archive.example.attacker.test:8080/custom/api/comments/ids",
                ArcticShiftSettings.DEFAULT_BASE_URL + "comments/ids"
        }) {
            assertFalse(url, ArcticShiftSettings.isApiUrl(HttpUrl.get(url), base));
        }
    }
}
