/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.fixes.api;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class RedditInfoResponsePatchTest {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final MediaType HTML = MediaType.get("text/html");
    private final List<Request> requests = new ArrayList<>();
    private final RedditCommentContext context = new RedditCommentContext(100);

    @Test
    public void leavesWorkingNativeLookupsAndTheirBodiesUntouched() throws Exception {
        String nativeJson = listing(post("post")).toString();
        OkHttpClient client = client((request) -> response(request, 200, nativeJson));
        Request request = request("/api/info?id=t3_post");
        try (Response response = client.newCall(request).execute()) {
            assertSame(request, response.request());
            assertEquals(nativeJson, response.body().string());
        }
        assertEquals(1, requests.size());
    }

    @Test
    public void replacesBlockedPostBatchesInOrderWithoutDuplicatingIds() throws Exception {
        OkHttpClient client = client(request -> {
            if (request.url().encodedPath().contains("/api/info")) return blocked(request);
            String id = request.url().encodedPath().contains("second") ? "second" : "post";
            return response(request, 200, thread(post(id)).toString()).newBuilder()
                    .header("ETag", "focused-only").header("Last-Modified", "yesterday")
                    .header("X-Ratelimit-Remaining", id.equals("second") ? "49" : "48").build();
        });
        Request original = request("/api/info.json?id=t3_second,t3_post,t3_second&raw_json=1")
                .newBuilder().header("If-None-Match", "original-info-only")
                .header("If-Modified-Since", "yesterday").header("Range", "bytes=0-100").build();
        try (Response response = client.newCall(original).execute()) {
            assertSame(original, response.request());
            JSONObject listing = new JSONObject(response.body().string());
            JSONArray children = listing.getJSONObject("data").getJSONArray("children");
            assertEquals("Listing", listing.getString("kind"));
            assertEquals(2, children.length());
            assertEquals("t3_second", children.getJSONObject(0).getJSONObject("data").getString("name"));
            assertEquals("t3_post", children.getJSONObject(1).getJSONObject("data").getString("name"));
            assertNull(response.header("Content-Length"));
            assertNull(response.header("Content-Encoding"));
            assertNull(response.header("ETag"));
            assertNull(response.header("Last-Modified"));
            assertEquals("48", response.header("X-Ratelimit-Remaining"));
        }
        assertEquals(3, requests.size());
        for (Request focused : requests.subList(1, requests.size())) {
            assertEquals("fixture-authorization", focused.header("Authorization"));
            assertEquals("fixture-cookie", focused.header("Cookie"));
            assertEquals("fixture-user-agent", focused.header("User-Agent"));
            assertEquals("oauth.reddit.com", focused.url().host());
            assertEquals("1", focused.url().queryParameter("limit"));
            assertEquals("1", focused.url().queryParameter("depth"));
            assertEquals("1", focused.url().queryParameter("raw_json"));
            assertEquals("identity", focused.header("Accept-Encoding"));
            assertNull(focused.header("If-None-Match"));
            assertNull(focused.header("If-Modified-Since"));
            assertNull(focused.header("Range"));
            assertNull(focused.url().queryParameter("id"));
        }
    }

    @Test
    public void handlesScopedInfoAndEncodedByIdPaths() throws Exception {
        OkHttpClient client = client(request -> {
            String path = request.url().encodedPath();
            if (path.contains("/by_id/") || path.contains("/api/info")) return blocked(request);
            return response(request, 200, thread(post("post")).toString());
        });
        for (String path : List.of("/r/java/api/info/.json?id=t3_post",
                "/r/java/by_id/t3_post%2Ct3_post.json")) {
            try (Response response = client.newCall(request(path)).execute()) {
                assertEquals(1, children(response).length());
            }
        }
        assertEquals(4, requests.size());
        assertEquals("/comments/post.json", requests.get(1).url().encodedPath());
        assertEquals("/comments/post.json", requests.get(3).url().encodedPath());
    }

    @Test
    public void convertsFormInfoRequestsToBodylessFocusedGets() throws Exception {
        OkHttpClient client = client(request -> request.url().encodedPath().contains("/api/info")
                ? blocked(request) : response(request, 200, thread(post("post")).toString()));
        Request original = request("/api/info?id=t3_old").newBuilder()
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Content-Length", "999")
                .post(new FormBody.Builder().add("id", "t3_post").add("raw_json", "0").build())
                .build();
        try (Response response = client.newCall(original).execute()) {
            assertEquals(1, children(response).length());
        }
        Request focused = requests.get(1);
        assertEquals("GET", focused.method());
        assertNull(focused.body());
        assertNull(focused.header("Content-Type"));
        assertNull(focused.header("Content-Length"));
        assertEquals("0", focused.url().queryParameter("raw_json"));
    }

    @Test
    public void reconstructsCommunityBatchesFromAboutResponses() throws Exception {
        OkHttpClient client = client(request -> {
            if (request.url().encodedPath().contains("/api/info")) return blocked(request);
            String name = request.url().pathSegments().get(1);
            return response(request, 200, community(name).toString());
        });
        try (Response response = client.newCall(request(
                "/api/info?sr_name=Java,learnjava,java&raw_json=1")).execute()) {
            JSONArray children = children(response);
            assertEquals(2, children.length());
            assertEquals("t5", children.getJSONObject(0).getString("kind"));
            assertEquals("java", children.getJSONObject(0).getJSONObject("data").getString("display_name"));
            assertEquals("learnjava", children.getJSONObject(1).getJSONObject("data").getString("display_name"));
            assertEquals("https://example.invalid/icon.png",
                    children.getJSONObject(0).getJSONObject("data").getString("icon_img"));
        }
        assertEquals(3, requests.size());
        assertEquals("/r/java/about.json", requests.get(1).url().encodedPath());
        assertEquals("/r/learnjava/about.json", requests.get(2).url().encodedPath());
    }

    @Test
    public void learnsCommentContextWithoutConsumingTheOriginalListing() throws Exception {
        JSONObject root = comment("child", "post").put("replies",
                new JSONObject().put("data", new JSONObject().put("children",
                        new JSONArray().put(thing("t1", comment("grandchild", "post"))))));
        String originalJson = thread(post("post"), thing("t1", root)).toString();
        OkHttpClient client = client(request -> {
            String path = request.url().encodedPath();
            if (path.contains("/api/info")) return blocked(request);
            if (path.contains("/comment/")) {
                return response(request, 200, thread(post("post"), thing("t1", root)).toString());
            }
            return response(request, 200, originalJson);
        });
        try (Response response = client.newCall(request("/r/java/comments/post/title/")).execute()) {
            assertEquals(originalJson, response.body().string());
        }
        assertEquals("post", context.postFor("t1_child"));
        assertEquals("post", context.postFor("t1_grandchild"));
        try (Response response = client.newCall(request("/api/info?id=t1_child")).execute()) {
            JSONObject data = children(response).getJSONObject(0).getJSONObject("data");
            assertEquals("t1_child", data.getString("name"));
            assertEquals("", data.getString("replies"));
            assertEquals("t3_post", data.getString("parent_id"));
        }
        assertEquals("/comments/post/comment/child.json", requests.get(2).url().encodedPath());
        assertEquals("0", requests.get(2).url().queryParameter("context"));
    }

    @Test
    public void learnsInboxContextAndAcceptsExplicitPostOrRefererContext() throws Exception {
        JSONObject inbox = thing("t1", new JSONObject().put("id", "child").put("name", "t1_child")
                .put("context", "/r/java/comments/post/title/child/?context=3"));
        OkHttpClient client = client(request -> {
            String path = request.url().encodedPath();
            if (path.startsWith("/message/")) return response(request, 200, listing(inbox).toString());
            if (path.contains("/api/info")) return blocked(request);
            return response(request, 200, thread(post("post"),
                    thing("t1", comment("child", "post"))).toString());
        });
        try (Response response = client.newCall(request("/message/inbox.json")).execute()) {
            response.body().string();
        }
        assertEquals("post", context.postFor("t1_child"));
        for (Request request : List.of(request("/api/info?id=t1_child"),
                request("/api/info?id=t1_child&link_id=t3_post"),
                request("/api/info?id=t1_child").newBuilder()
                        .header("Referer", "https://www.reddit.com/comments/post/title/child/").build())) {
            try (Response response = client.newCall(request).execute()) {
                assertEquals(1, children(response).length());
            }
        }
    }

    @Test
    public void keepsUnknownStandaloneCommentLookupsAsRealApiErrors() throws Exception {
        OkHttpClient client = client(RedditInfoResponsePatchTest::blocked);
        try (Response response = client.newCall(request("/api/info?id=t1_unknown")).execute()) {
            assertEquals(403, response.code());
            assertEquals("blocked", response.body().string());
        }
        assertEquals(1, requests.size());
    }

    @Test
    public void sharesOnlyPostRelationshipsAcrossDifferentClientInstances() throws Exception {
        OkHttpClient inbox = client(new RedditInfoResponsePatch(), request -> response(request, 200,
                listing(thing("t1", comment("sharedchild", "sharedpost"))).toString()));
        try (Response response = inbox.newCall(request("/message/inbox.json")).execute()) {
            response.body().string();
        }
        OkHttpClient lookup = client(new RedditInfoResponsePatch(), request -> {
            if (request.url().encodedPath().contains("/api/info")) return blocked(request);
            assertEquals("/comments/sharedpost/comment/sharedchild.json", request.url().encodedPath());
            return response(request, 200, thread(post("sharedpost"),
                    thing("t1", comment("sharedchild", "sharedpost"))).toString());
        });
        try (Response response = lookup.newCall(request("/api/info?id=t1_sharedchild")).execute()) {
            assertEquals("t1_sharedchild",
                    children(response).getJSONObject(0).getJSONObject("data").getString("name"));
        }
    }

    @Test
    public void omitsAbsentCommentsAndJsonNotFoundResources() throws Exception {
        OkHttpClient client = client(request -> {
            String path = request.url().encodedPath();
            if (path.contains("/api/info")) return blocked(request);
            if (path.contains("/comment/")) return response(request, 200, thread(post("post")).toString());
            return response(request, 404, "{\"error\":404,\"message\":\"Not Found\"}");
        });
        for (String path : List.of("/api/info?id=t3_missing", "/api/info?sr_name=missing",
                "/api/info?id=t1_child&link_id=t3_post")) {
            try (Response response = client.newCall(request(path)).execute()) {
                assertEquals(200, response.code());
                assertEquals(0, children(response).length());
            }
        }
    }

    @Test
    public void doesNotMaskAuthenticationPrivateOrRateLimitErrors() throws Exception {
        for (int code : List.of(401, 403, 429, 500)) {
            OkHttpClient client = client(request -> response(request, code,
                    "{\"error\":" + code + ",\"reason\":\"private\"}"));
            try (Response response = client.newCall(request("/api/info?id=t3_post")).execute()) {
                assertEquals(code, response.code());
            }
        }
        assertEquals(4, requests.size());
    }

    @Test
    public void propagatesAnAlternateFailureInsteadOfReturningAPartialSuccessfulBatch() throws Exception {
        OkHttpClient client = client(request -> {
            String path = request.url().encodedPath();
            if (path.contains("/api/info")) return blocked(request);
            if (path.contains("second")) return response(request, 429, "{\"error\":429}")
                    .newBuilder().header("Retry-After", "60").build();
            return response(request, 200, thread(post("post")).toString());
        });
        try (Response response = client.newCall(request("/api/info?id=t3_post,t3_second")).execute()) {
            assertEquals(429, response.code());
            assertEquals("60", response.header("Retry-After"));
            assertEquals("{\"error\":429}", response.body().string());
        }
        assertEquals(3, requests.size());
    }

    @Test
    public void rejectsUnexpectedThingsAndMalformedAlternateResponses() throws Exception {
        for (String body : List.of("{}", thread(post("wrong")).toString(),
                "[{\"data\":{\"children\":[{\"kind\":\"t1\",\"data\":{\"name\":\"t3_post\"}}]}}]")) {
            OkHttpClient client = client(request -> request.url().encodedPath().contains("/api/info")
                    ? blocked(request) : response(request, 200, body));
            assertThrows(IOException.class, () -> client.newCall(request("/api/info?id=t3_post")).execute());
        }
    }

    @Test
    public void leavesOtherHostsMethodsAndDisabledHandlersUntouched() throws Exception {
        OkHttpClient client = client(RedditInfoResponsePatchTest::blocked);
        for (String url : List.of("https://notreddit.com/api/info?id=t3_post",
                "https://reddit.com.evil.example/api/info?id=t3_post")) {
            try (Response response = client.newCall(new Request.Builder().url(url).build()).execute()) {
                assertEquals(403, response.code());
            }
        }
        try (Response response = client.newCall(request("/by_id/t3_post").newBuilder()
                .post(new FormBody.Builder().build()).build()).execute()) {
            assertEquals(403, response.code());
        }
        OkHttpClient disabled = new OkHttpClient.Builder().addInterceptor(new RedditInfoResponsePatch())
                .addInterceptor(chain -> blocked(chain.request())).build();
        try (Response response = disabled.newCall(request("/api/info?id=t3_post")).execute()) {
            assertEquals(403, response.code());
        }
        assertEquals(3, requests.size());
    }

    @Test
    public void boundsContextObservationWithoutChangingLargeResponseBodies() throws Exception {
        String prefix = listing(thing("t1", comment("child", "post"))).toString();
        int limit = 8 * 1024 * 1024;
        String atLimit = prefix + " ".repeat(limit - prefix.length());
        String overLimit = atLimit + " ";
        OkHttpClient client = client(request -> response(request, 200,
                request.url().queryParameter("large") == null ? atLimit : overLimit));
        try (Response response = client.newCall(request("/comments/post.json?large=1")).execute()) {
            assertEquals(overLimit, response.body().string());
        }
        assertNull(context.postFor("t1_child"));
        try (Response response = client.newCall(request("/comments/post.json")).execute()) {
            assertEquals(atLimit, response.body().string());
        }
        assertEquals("post", context.postFor("t1_child"));
    }

    private OkHttpClient client(Responder responder) {
        return client(new RedditInfoResponsePatch(context), responder);
    }

    private OkHttpClient client(RedditInfoResponsePatch patch, Responder responder) {
        return new OkHttpClient.Builder().addInterceptor(patch::doIntercept)
                .addInterceptor(chain -> {
                    requests.add(chain.request());
                    try {
                        return responder.respond(chain.request());
                    } catch (JSONException exception) {
                        throw new IOException("Invalid HTTP test fixture", exception);
                    }
                }).build();
    }

    private static Request request(String path) {
        return new Request.Builder().url("https://oauth.reddit.com" + path)
                .header("Authorization", "fixture-authorization")
                .header("Cookie", "fixture-cookie")
                .header("User-Agent", "fixture-user-agent").build();
    }

    private static Response blocked(Request request) {
        return response(request, 403, "blocked", HTML);
    }

    private static Response response(Request request, int code, String json) {
        return response(request, code, json, JSON);
    }

    private static Response response(Request request, int code, String text, MediaType type) {
        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code)
                .message("fixture").header("Content-Type", type.toString())
                .body(ResponseBody.create(text, type)).build();
    }

    private static JSONArray children(Response response) throws Exception {
        return new JSONObject(response.body().string()).getJSONObject("data").getJSONArray("children");
    }

    private static JSONObject thing(String kind, JSONObject data) throws JSONException {
        return new JSONObject().put("kind", kind).put("data", data);
    }

    private static JSONObject post(String id) throws JSONException {
        return thing("t3", new JSONObject().put("id", id).put("name", "t3_" + id)
                .put("is_gallery", true).put("gallery_data", new JSONObject()));
    }

    private static JSONObject comment(String id, String post) throws JSONException {
        return new JSONObject().put("id", id).put("name", "t1_" + id)
                .put("link_id", "t3_" + post).put("parent_id", "t3_" + post).put("replies", "");
    }

    private static JSONObject community(String name) throws JSONException {
        return thing("t5", new JSONObject().put("id", "community").put("name", "t5_community")
                .put("display_name", name).put("icon_img", "https://example.invalid/icon.png"));
    }

    private static JSONObject listing(JSONObject... things) throws JSONException {
        return new JSONObject().put("kind", "Listing").put("data",
                new JSONObject().put("children", new JSONArray(List.of(things))));
    }

    private static JSONArray thread(JSONObject post, JSONObject... comments) throws JSONException {
        return new JSONArray().put(listing(post)).put(listing(comments));
    }

    private interface Responder {
        Response respond(Request request) throws IOException, JSONException;
    }
}
