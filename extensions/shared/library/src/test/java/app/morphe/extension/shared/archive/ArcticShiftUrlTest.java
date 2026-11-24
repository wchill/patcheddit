/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.archive;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ArcticShiftUrlTest {
    @Test
    public void normalizesCompatibleApiUrlsWithoutLosingTheirPathOrPort() {
        assertEquals(ArcticShiftUrl.DEFAULT_BASE_URL, ArcticShiftUrl.normalize(" \t "));
        assertEquals("https://archive.example/custom/api/",
                ArcticShiftUrl.normalize(" https://archive.example/custom/api "));
        assertEquals("http://192.168.1.2:8080/api/", ArcticShiftUrl.normalize("http://192.168.1.2:8080/api/"));
        assertEquals("http://[::1]:8080/", ArcticShiftUrl.normalize("http://[::1]:8080"));
        assertEquals("https://archive.example/api%20v1/", ArcticShiftUrl.normalize("https://archive.example/api%20v1"));
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
            assertThrows(value, IllegalArgumentException.class, () -> ArcticShiftUrl.normalize(value));
        }
    }
}
