/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.boost.utils;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SettingsUtilsTest {
    @Test
    public void sourcesCanBeDisabledIndependentlyWithoutChangingOtherMenuIds() {
        Map<Integer, SettingsUtils.ArchiveService> all = SettingsUtils.enabledArchiveServices(key -> true);
        all.forEach((disabledId, disabled) -> {
            Map<Integer, SettingsUtils.ArchiveService> remaining =
                    SettingsUtils.enabledArchiveServices(key -> !key.equals(disabled.preferenceKey));
            List<Integer> expected = new ArrayList<>(all.keySet());
            expected.remove(disabledId);
            assertEquals(expected, new ArrayList<>(remaining.keySet()));
            assertFalse(remaining.containsKey(disabledId));
        });
    }

    @Test
    public void selectedSourcesKeepTheirOriginalUrlsAndOrder() {
        Set<String> selected = Set.of("morphe_boost_archive_archive_is", "morphe_boost_archive_unwall");
        Map<Integer, SettingsUtils.ArchiveService> enabled =
                SettingsUtils.enabledArchiveServices(selected::contains);
        assertEquals(List.of(102, 107), new ArrayList<>(enabled.keySet()));
        assertEquals("https://archive.is/", enabled.get(102).baseUrl);
        assertEquals("https://unwall.app/", enabled.get(107).baseUrl);
    }

    @Test
    public void allSourcesCanBeDisabled() {
        assertTrue(SettingsUtils.enabledArchiveServices(key -> false).isEmpty());
    }

    @Test
    public void removesTheWholeQueryWithoutChangingEncodedPathsOrFragments() {
        assertEquals("https://example.com/a%3Fb#section?details",
                SettingsUtils.stripQueryParameters("https://example.com/a%3Fb?a=1&utm_source=test#section?details"));
        assertEquals("https://example.com/path#section",
                SettingsUtils.stripQueryParameters("https://example.com/path?#section"));
        assertEquals("https://example.com/path",
                SettingsUtils.stripQueryParameters("https://example.com/path?token=a%26b&key=1&key=2"));
    }

    @Test
    public void leavesUrlsWithoutQueriesAndFragmentQuestionsUnchanged() {
        for (String url : List.of("https://example.com/a%3Fb", "https://example.com/#fragment?query=1")) {
            assertEquals(url, SettingsUtils.stripQueryParameters(url));
        }
    }

    @Test
    public void offersQueryStrippingOnlyForQueriesBeforeTheFragment() {
        assertTrue(LinkQueryParameters.hasQueryParameters("https://example.com/?a=1#fragment"));
        assertTrue(LinkQueryParameters.hasQueryParameters("https://example.com/?a=%3F"));
        for (String url : List.of("https://example.com/", "https://example.com/?",
                "https://example.com/?#fragment", "https://example.com/#fragment?query=1",
                "https://example.com/a%3Fb")) {
            assertFalse(LinkQueryParameters.hasQueryParameters(url));
        }
    }
}
