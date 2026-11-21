/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.fixes.feed;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertFalse;

public class MatureContentFilterTest {
    @Test
    public void removesRestrictedPostsAndCommunitiesButKeepsSafeResultsAndPagination() throws Exception {
        String body = "{\"kind\":\"Listing\",\"data\":{\"after\":\"next\",\"children\":["
                + "{\"kind\":\"t3\",\"data\":{\"id\":\"safe\",\"over_18\":false}},"
                + "{\"kind\":\"t3\",\"data\":{\"id\":\"mature\",\"over_18\":true}},"
                + "{\"kind\":\"t5\",\"data\":{\"display_name\":\"hidden\",\"over18\":true}},"
                + "{\"kind\":\"t5\",\"data\":{\"display_name\":\"visible\",\"over18\":false}},"
                + "{\"kind\":\"t3\",\"data\":{\"id\":\"crosspost\",\"crosspost_parent_list\":[{\"over_18\":true}]}},"
                + "{\"kind\":\"more\",\"data\":{\"id\":\"more\"}}]}}";
        JSONObject listing = new JSONObject(MatureContentFilter.filter(body, "/subreddits/mine/subscriber"));
        JSONObject data = listing.getJSONObject("data");
        assertEquals("next", data.getString("after"));
        JSONArray children = data.getJSONArray("children");
        assertEquals(3, children.length());
        assertEquals("safe", children.getJSONObject(0).getJSONObject("data").getString("id"));
        assertEquals("visible", children.getJSONObject(1).getJSONObject("data").getString("display_name"));
        assertEquals("more", children.getJSONObject(2).getString("kind"));
    }

    @Test
    public void handlesAutocompleteAndMasksRedditAccountSettings() throws Exception {
        String body = "{\"subreddits\":[{\"name\":\"safe\",\"over18\":false},"
                + "{\"name\":\"mature\",\"over18\":true}],\"names\":[\"unknown\"]}";
        assertEquals("unknown", new JSONObject(MatureContentFilter.filter(body, "/api/search_reddit_names", true))
                .getJSONArray("names").getString(0));
        assertEquals(0, new JSONObject(MatureContentFilter.filter(body, "/api/search_reddit_names"))
                .getJSONArray("names").length());
        assertEquals(1, new JSONObject(MatureContentFilter.filter(body, "/api/search_reddit_names"))
                .getJSONArray("subreddits").length());
        String settings = "{\"over_18\":true,\"search_include_over_18\":true}";
        JSONObject filtered = new JSONObject(MatureContentFilter.filter(settings, "/api/v1/me/prefs"));
        assertFalse(filtered.getBoolean("over_18"));
        assertFalse(filtered.getBoolean("search_include_over_18"));
    }

    @Test
    public void blocksDirectMatureObjectsAndRejectsMalformedRedditJson() {
        assertThrows(IOException.class, () -> MatureContentFilter.filter(
                "{\"kind\":\"t5\",\"data\":{\"over18\":true}}", "/r/example/about"));
        assertThrows(IOException.class, () -> MatureContentFilter.filter("{not-json", "/hot"));
    }
}
