/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.fixes.api;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RedditCommentContextTest {
    @Test
    public void learnsPostsFromCommentsInboxLinksAndNestedReplies() throws Exception {
        RedditCommentContext context = new RedditCommentContext(20);
        JSONObject child = comment("child", new JSONObject().put("parent_id", "t1_parent"));
        JSONObject parent = comment("parent", new JSONObject().put("link_id", "t3_post")
                .put("replies", new JSONObject().put("data",
                        new JSONObject().put("children", new JSONArray().put(child)))));
        JSONObject inbox = comment("inbox", new JSONObject()
                .put("context", "/r/java/comments/other/title/inbox/?context=3"));
        JSONObject history = comment("history", new JSONObject()
                .put("permalink", "https://www.reddit.com/r/java/comments/historypost/title/history/"));
        context.index(new JSONArray().put(parent).put(inbox).put(history), null);
        assertEquals("post", context.postFor("t1_parent"));
        assertEquals("post", context.postFor("t1_child"));
        assertEquals("other", context.postFor("t1_inbox"));
        assertEquals("historypost", context.postFor("t1_history"));
    }

    @Test
    public void usesTheRequestPostOnlyWhenTheCommentDoesNotProvideItsOwn() throws Exception {
        RedditCommentContext context = new RedditCommentContext(20);
        context.index(new JSONArray().put(comment("first", new JSONObject()))
                .put(comment("second", new JSONObject().put("link_id", "t3_actual"))), "fallback");
        assertEquals("fallback", context.postFor("t1_first"));
        assertEquals("actual", context.postFor("t1_second"));
    }

    @Test
    public void rejectsExternalContextLinksAndInvalidIds() throws Exception {
        RedditCommentContext context = new RedditCommentContext(20);
        context.index(comment("external", new JSONObject()
                .put("context", "https://reddit.com.evil.example/comments/post/title/external/")), null);
        context.index(comment("relative", new JSONObject()
                .put("context", "//evil.example/comments/post/title/relative/")), null);
        context.index(comment("../invalid", new JSONObject().put("link_id", "t3_post")), null);
        assertNull(context.postFor("t1_external"));
        assertNull(context.postFor("t1_relative"));
        assertNull(context.postFor("t1_../invalid"));
    }

    @Test
    public void evictsTheLeastRecentlyUsedRelationshipAtTheExactCapacity() throws Exception {
        RedditCommentContext context = new RedditCommentContext(2);
        context.index(comment("one", new JSONObject().put("link_id", "t3_post")), null);
        context.index(comment("two", new JSONObject().put("link_id", "t3_post")), null);
        assertEquals("post", context.postFor("t1_one"));
        context.index(comment("three", new JSONObject().put("link_id", "t3_other")), null);
        assertEquals("post", context.postFor("t1_one"));
        assertNull(context.postFor("t1_two"));
        assertEquals("other", context.postFor("t1_three"));
    }

    private static JSONObject comment(String id, JSONObject data) throws Exception {
        return new JSONObject().put("kind", "t1").put("data",
                data.put("id", id).put("name", "t1_" + id));
    }
}
