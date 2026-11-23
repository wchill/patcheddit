/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.archive.arcticshift;

import org.junit.Test;

import app.morphe.extension.shared.archive.ArcticShiftUrl;
import okhttp3.HttpUrl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ArcticShiftSettingsTest {
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
                ArcticShiftUrl.DEFAULT_BASE_URL + "comments/ids"
        }) {
            assertFalse(url, ArcticShiftSettings.isApiUrl(HttpUrl.get(url), base));
        }
    }
}
