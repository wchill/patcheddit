/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.fixes.links;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class EscapedUrlsPatchTest {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String BROKEN = "https://example.com/a\\_b?q=c\\_d#e\\_f";
    private static final String FIXED = "https://example.com/a_b?q=c_d#e_f";

    @Test
    public void repairsListingsRepliesCrosspostsAndMorechildrenWithoutChangingOtherFields() throws Exception {
        JSONObject comment = new JSONObject().put("body", "See " + BROKEN)
                .put("author", "user\\_name").put("other", BROKEN);
        JSONObject post = new JSONObject().put("selftext", "[link](" + BROKEN + ")")
                .put("url", BROKEN).put("url_overridden_by_dest", BROKEN)
                .put("title", BROKEN).put("null", JSONObject.NULL)
                .put("crosspost_parent_list", new JSONArray().put(new JSONObject().put("selftext", BROKEN)));
        JSONObject response = new JSONObject().put("data", new JSONObject()
                .put("children", new JSONArray().put(post))
                .put("json", new JSONObject().put("data", new JSONObject()
                        .put("things", new JSONArray().put(comment)))));
        JSONObject repaired = new JSONObject(EscapedUrls.repairJson(response.toString()))
                .getJSONObject("data");
        JSONObject fixedPost = repaired.getJSONArray("children").getJSONObject(0);
        assertEquals("[link](" + FIXED + ")", fixedPost.getString("selftext"));
        assertEquals(FIXED, fixedPost.getString("url"));
        assertEquals(FIXED, fixedPost.getString("url_overridden_by_dest"));
        assertEquals(FIXED, fixedPost.getJSONArray("crosspost_parent_list").getJSONObject(0)
                .getString("selftext"));
        assertEquals(BROKEN, fixedPost.getString("title"));
        JSONObject fixedComment = repaired.getJSONObject("json").getJSONObject("data")
                .getJSONArray("things").getJSONObject(0);
        assertEquals("See " + FIXED, fixedComment.getString("body"));
        assertEquals("user\\_name", fixedComment.getString("author"));
        assertEquals(BROKEN, fixedComment.getString("other"));
        JSONArray thread = new JSONArray().put(response);
        assertEquals(fixedPost.toString(), new JSONArray(EscapedUrls.repairJson(thread.toString()))
                .getJSONObject(0).getJSONObject("data").getJSONArray("children").getJSONObject(0).toString());
    }

    @Test
    public void preservesMarkdownEscapesCodeAndLinkSyntax() throws Exception {
        String input = "\\_prose\\_ [a\\_label](" + BROKEN + ")\\_outside\\_ <" + BROKEN + ">\n"
                + "`" + BROKEN + "` ``a `" + BROKEN + "` b``\n"
                + "```markdown\n" + BROKEN + "\n````\n"
                + "~~~\n" + BROKEN + "\n~~~\n"
                + "    " + BROKEN + "\n\t" + BROKEN + "\n"
                + "https://example.com/a\\_b\\(c\\) https://example.com/C:\\folder\\\\_name\n"
                + "www.example.com/a\\_b http://example.com/a\\*b\\~c";
        String expected = "\\_prose\\_ [a\\_label](" + FIXED + ")\\_outside\\_ <" + FIXED + ">\n"
                + "`" + BROKEN + "` ``a `" + BROKEN + "` b``\n"
                + "```markdown\n" + BROKEN + "\n````\n"
                + "~~~\n" + BROKEN + "\n~~~\n"
                + "    " + BROKEN + "\n\t" + BROKEN + "\n"
                + "https://example.com/a_b\\(c\\) https://example.com/C:\\folder\\\\_name\n"
                + "www.example.com/a_b http://example.com/a*b~c";
        assertField("body", input, expected);
        assertField("body", "https://example.com/a_(b\\_c)", "https://example.com/a_(b_c)");
        assertField("body", "https://example.com/" + "a".repeat(20000) + "\\_b",
                "https://example.com/" + "a".repeat(20000) + "_b");
    }

    @Test
    public void repairsRenderedHtmlAndEncodedBackslashesButNotCodeOrOtherEscapes() throws Exception {
        for (String field : List.of("body_html", "selftext_html")) {
            for (boolean encoded : List.of(false, true)) {
                String input = "<div><a href=\"" + BROKEN.replace("\\", "%5C") + "\">" + BROKEN
                        + "</a><code>" + BROKEN + "</code><pre>" + BROKEN + "</pre>\\_prose\\_</div>";
                String expected = "<div><a href=\"" + FIXED + "\">" + FIXED
                        + "</a><code>" + BROKEN + "</code><pre>" + BROKEN + "</pre>\\_prose\\_</div>";
                if (encoded) {
                    input = input.replace("<", "&lt;").replace(">", "&gt;");
                    expected = expected.replace("<", "&lt;").replace(">", "&gt;");
                }
                assertField(field, input, expected);
            }
        }
        assertField("selftext", "https://example.com/a%5c_b/%5Cother/%255C_",
                "https://example.com/a_b/%5Cother/%255C_");
    }

    @Test
    public void leavesUnchangedAndMalformedDocumentsByteForByteIntact() {
        for (String input : List.of(
                "{ \"body\": \"https://example.com/a_b\" }\n",
                "{ \"author\": \"a\\\\_b\", \"body\": null }",
                "{\"body\":\"https://example.com/a\\\\_b\"",
                "{\"body\":\"https://example.com/a\\\\_b\"} trailing")) {
            assertEquals(input, EscapedUrls.repairJson(input));
        }
    }

    @Test
    public void rewritesSuccessfulRedditJsonAndInvalidatesItsOldEntityHeaders() throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        String body = new JSONObject().put("body", BROKEN).toString();
        try (Response response = fetch("https://oauth.reddit.com/r/test/comments/post.json",
                body, JSON, 200, null, true, false, closed)) {
            assertEquals(FIXED, new JSONObject(response.body().string()).getString("body"));
            assertNull(response.header("Content-Length"));
            assertNull(response.header("ETag"));
            assertEquals("application/json; charset=utf-8", response.header("Content-Type"));
            assertTrue(closed.get());
        }
    }

    @Test
    public void passesDisabledUnrelatedErrorCompressedAndOversizedResponsesThroughUnconsumed() throws Exception {
        String body = new JSONObject().put("body", BROKEN).toString();
        for (String url : List.of("https://example.com/comments/post",
                "https://reddit.com.evil.example/api/info", "https://notreddit.com/best")) {
            assertUnchanged(url, body, JSON, 200, null, true, false);
        }
        assertUnchanged("https://reddit.com/best", body, JSON, 403, null, true, false);
        assertUnchanged("https://reddit.com/best", body, MediaType.get("text/html"), 200, null, true, false);
        assertUnchanged("https://reddit.com/best", body, JSON, 200, "gzip", true, false);
        assertUnchanged("https://reddit.com/best", body, JSON, 200, null, false, false);
        assertUnchanged("https://reddit.com/best", " ".repeat(8 * 1024 * 1024 + 1) + body,
                JSON, 200, null, true, true);
        assertUnchanged("https://reddit.com/best", "{ \"body\": \"unchanged\" }\n",
                JSON, 200, null, true, false);
    }

    @Test
    public void legacyTransportUsesTheSameRepairAndResponseGuards() throws Exception {
        String body = new JSONObject().put("selftext", BROKEN).toString();
        String url = "https://oauth.reddit.com/best";
        assertEquals(FIXED, new JSONObject(EscapedUrlsPatch.repairLegacyResponse(
                body, url, 200, "application/json")).getString("selftext"));
        assertEquals(body, EscapedUrlsPatch.repairLegacyResponse(body, url, 403, "application/json"));
        assertEquals(body, EscapedUrlsPatch.repairLegacyResponse(body, url, 200, "text/html"));
        assertEquals(body, EscapedUrlsPatch.repairLegacyResponse(
                body, "https://example.com/best", 200, "application/json"));
    }

    @Test
    public void nativeDecoderRepairsOnlyRedditThingsAndPreservesUnrelatedJson() throws Exception {
        JSONObject thing = new JSONObject().put("kind", "t1")
                .put("data", new JSONObject().put("body", BROKEN));
        JSONArray listing = new JSONArray().put(new JSONObject().put("kind", "Listing")
                .put("data", new JSONObject().put("children", new JSONArray().put(thing))));
        assertEquals(FIXED, new JSONArray(EscapedUrls.repairRedditResponse(listing.toString()))
                .getJSONObject(0).getJSONObject("data").getJSONArray("children")
                .getJSONObject(0).getJSONObject("data").getString("body"));
        for (String kind : List.of("t2", "Listing", "other")) {
            thing.put("kind", kind);
            assertEquals(thing.toString(), EscapedUrls.repairRedditResponse(thing.toString()));
        }
    }

    private static void assertField(String key, String input, String expected) throws Exception {
        assertEquals(expected, new JSONObject(EscapedUrls.repairJson(
                new JSONObject().put(key, input).toString())).getString(key));
    }

    private static void assertUnchanged(String url, String body, MediaType type, int code,
                                        String encoding, boolean enabled, boolean unknownLength) throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        try (Response response = fetch(url, body, type, code, encoding, enabled, unknownLength, closed)) {
            assertFalse(closed.get());
            assertEquals("original", response.header("ETag"));
            assertEquals(body, response.body().string());
        }
    }

    private static Response fetch(String url, String text, MediaType type, int code, String encoding,
                                  boolean enabled, boolean unknownLength, AtomicBoolean closed) throws Exception {
        EscapedUrlsPatch patch = new EscapedUrlsPatch();
        ResponseBody delegate = ResponseBody.create(text, type);
        ResponseBody tracked = new ResponseBody() {
            @Override
            public MediaType contentType() {
                return type;
            }

            @Override
            public long contentLength() {
                return unknownLength ? -1 : delegate.contentLength();
            }

            @Override
            public BufferedSource source() {
                return delegate.source();
            }

            @Override
            public void close() {
                closed.set(true);
                delegate.close();
            }
        };
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(enabled ? patch::doIntercept : patch)
                .addInterceptor(chain -> {
                    Response.Builder response = new Response.Builder().request(chain.request())
                            .protocol(Protocol.HTTP_1_1).code(code).message("fixture").body(tracked)
                            .header("ETag", "original").header("Content-Length", "" + delegate.contentLength());
                    if (encoding != null) response.header("Content-Encoding", encoding);
                    return response.build();
                }).build();
        return client.newCall(new Request.Builder().url(url).build()).execute();
    }
}
