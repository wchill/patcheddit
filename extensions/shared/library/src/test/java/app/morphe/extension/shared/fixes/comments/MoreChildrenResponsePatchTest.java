/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.fixes.comments;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

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
import static org.junit.Assert.assertTrue;

public class MoreChildrenResponsePatchTest {
    private static final MediaType JSON = MediaType.get("application/json");

    @Test
    public void preservesGrandchildrenAndTheirMorePlaceholdersInPreorder() throws Exception {
        List<Request> requests = new ArrayList<>();
        JSONObject more = more("great", "remaining");
        JSONObject great = comment("great", "grand", more);
        JSONObject grand = comment("grand", "child", great);
        JSONObject child = comment("child", "parent", grand);
        OkHttpClient client = client(requests, listing(child));

        try (Response response = client.newCall(request("child")).execute()) {
            JSONArray things = new JSONObject(response.body().string())
                    .getJSONObject("json").getJSONObject("data").getJSONArray("things");
            assertEquals(4, things.length());
            assertEquals("child", things.getJSONObject(0).getJSONObject("data").getString("id"));
            assertEquals("grand", things.getJSONObject(1).getJSONObject("data").getString("id"));
            assertEquals("great", things.getJSONObject(2).getJSONObject("data").getString("id"));
            for (int i = 0; i < 3; i++) {
                assertEquals("", things.getJSONObject(i).getJSONObject("data").getString("replies"));
            }
            assertEquals("t1_child", things.getJSONObject(1).getJSONObject("data").getString("parent_id"));
            assertEquals("t1_grand", things.getJSONObject(2).getJSONObject("data").getString("parent_id"));
            assertEquals("more", things.getJSONObject(3).getString("kind"));
            JSONObject moreData = things.getJSONObject(3).getJSONObject("data");
            assertEquals("t1_great", moreData.getString("parent_id"));
            assertEquals("remaining", moreData.getJSONArray("children").getString(0));
            assertEquals("/api/morechildren", response.request().url().encodedPath());
        }
        assertEquals(1, requests.size());
        assertEquals("/comments/post/comment/child.json", requests.get(0).url().encodedPath());
        assertEquals("0", requests.get(0).url().queryParameter("context"));
        assertEquals("new", requests.get(0).url().queryParameter("sort"));
        assertEquals("Bearer fixture", requests.get(0).header("Authorization"));
    }

    @Test
    public void doesNotFetchOrEmitRequestedDescendantsTwice() throws Exception {
        List<Request> requests = new ArrayList<>();
        JSONObject child = comment("child", "parent",
                comment("grand", "child", comment("great", "grand")));
        OkHttpClient client = client(requests, listing(child));

        try (Response response = client.newCall(request("child,grand,great,child")).execute()) {
            JSONArray things = new JSONObject(response.body().string())
                    .getJSONObject("json").getJSONObject("data").getJSONArray("things");
            assertEquals(3, things.length());
        }
        assertEquals(1, requests.size());
    }

    @Test
    public void loadsSiblingCommentsFromOneParentListingAndFlattensTheirReplies() throws Exception {
        List<Request> requests = new ArrayList<>();
        JSONObject second = comment("second", "parent", comment("reply", "second", more("reply", "later")));
        String focused = listing(comment("first", "parent"));
        String parent = listing(comment("parent", "ancestor",
                comment("unrelated", "parent"), comment("first", "parent"),
                second, more("parent", "other"), comment("third", "parent")));
        OkHttpClient client = client(requests, focused, parent);

        try (Response response = client.newCall(request("first,second,third")).execute()) {
            JSONArray things = new JSONObject(response.body().string())
                    .getJSONObject("json").getJSONObject("data").getJSONArray("things");
            assertEquals(5, things.length());
            assertEquals("first", things.getJSONObject(0).getJSONObject("data").getString("id"));
            assertEquals("second", things.getJSONObject(1).getJSONObject("data").getString("id"));
            assertEquals("reply", things.getJSONObject(2).getJSONObject("data").getString("id"));
            assertEquals("more", things.getJSONObject(3).getString("kind"));
            assertEquals("t1_reply", things.getJSONObject(3).getJSONObject("data").getString("parent_id"));
            assertEquals("third", things.getJSONObject(4).getJSONObject("data").getString("id"));
            assertEquals("", things.getJSONObject(1).getJSONObject("data").getString("replies"));
        }
        assertEquals(2, requests.size());
        assertEquals("/comments/post/comment/parent.json", requests.get(1).url().encodedPath());
        assertEquals("500", requests.get(1).url().queryParameter("limit"));
        assertEquals("10", requests.get(1).url().queryParameter("depth"));
        assertEquals("new", requests.get(1).url().queryParameter("sort"));
        assertEquals("Bearer fixture", requests.get(1).header("Authorization"));
    }

    @Test
    public void loadsTopLevelCommentsFromPostListing() throws Exception {
        List<Request> requests = new ArrayList<>();
        JSONObject first = comment("first", "unused");
        first.getJSONObject("data").put("parent_id", "t3_post");
        JSONObject second = comment("second", "unused");
        second.getJSONObject("data").put("parent_id", "t3_post");
        JSONObject third = comment("third", "unused");
        third.getJSONObject("data").put("parent_id", "t3_post");
        OkHttpClient client = client(requests, listing(first), listing(first, second, third));

        try (Response response = client.newCall(request("first,second,third")).execute()) {
            JSONArray things = new JSONObject(response.body().string())
                    .getJSONObject("json").getJSONObject("data").getJSONArray("things");
            assertEquals(3, things.length());
            assertEquals("t3_post", things.getJSONObject(1).getJSONObject("data").getString("parent_id"));
        }
        assertEquals(2, requests.size());
        assertEquals("/comments/post.json", requests.get(1).url().encodedPath());
        assertEquals("2", requests.get(1).url().queryParameter("depth"));
        assertEquals("1", requests.get(1).url().queryParameter("raw_json"));
    }

    @Test
    public void fallsBackForRequestedCommentsOutsideTheParentListing() throws Exception {
        List<Request> requests = new ArrayList<>();
        OkHttpClient client = client(requests,
                listing(comment("first", "parent")),
                listing(comment("parent", "ancestor", comment("second", "parent"))),
                listing(comment("third", "parent")));

        try (Response response = client.newCall(request("first,second,third")).execute()) {
            JSONArray things = new JSONObject(response.body().string())
                    .getJSONObject("json").getJSONObject("data").getJSONArray("things");
            assertEquals(3, things.length());
            assertEquals("second", things.getJSONObject(1).getJSONObject("data").getString("id"));
            assertEquals("third", things.getJSONObject(2).getJSONObject("data").getString("id"));
        }
        assertEquals(3, requests.size());
        assertEquals("/comments/post/comment/third.json", requests.get(2).url().encodedPath());
    }

    @Test
    public void fallsBackWhenTheParentListingIsNotFound() throws Exception {
        List<Request> requests = new ArrayList<>();
        MoreChildrenResponsePatch shim = new MoreChildrenResponsePatch();
        String first = listing(comment("first", "parent"));
        String second = listing(comment("second", "parent"));
        String third = listing(comment("third", "parent"));
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> shim.shimResponse(chain, chain.request()))
                .addInterceptor(chain -> {
                    Request current = chain.request();
                    requests.add(current);
                    String path = current.url().encodedPath();
                    if (path.endsWith("/parent.json")) return response(current, 404, "{}");
                    return response(current, 200, path.endsWith("/first.json") ? first
                            : path.endsWith("/second.json") ? second : third);
                }).build();

        try (Response response = client.newCall(request("first,second,third")).execute()) {
            JSONArray things = new JSONObject(response.body().string())
                    .getJSONObject("json").getJSONObject("data").getJSONArray("things");
            assertEquals(3, things.length());
        }
        assertEquals(4, requests.size());
    }

    @Test
    public void propagatesBulkRateLimitsWithoutReturningPartialResults() throws Exception {
        List<Request> requests = new ArrayList<>();
        MoreChildrenResponsePatch shim = new MoreChildrenResponsePatch();
        String first = listing(comment("first", "parent"));
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> shim.shimResponse(chain, chain.request()))
                .addInterceptor(chain -> {
                    requests.add(chain.request());
                    return response(chain.request(), requests.size() == 1 ? 200 : 429,
                            requests.size() == 1 ? first : "{\"error\":429}");
                }).build();

        try (Response response = client.newCall(request("first,second,third")).execute()) {
            assertEquals(429, response.code());
            assertTrue(response.body().string().contains("429"));
        }
        assertEquals(2, requests.size());
    }

    @Test
    public void expandingADeepPlaceholderRetainsItsRealParent() throws Exception {
        List<Request> requests = new ArrayList<>();
        OkHttpClient client = client(requests, listing(comment("remaining", "great",
                comment("deep", "remaining"))));
        try (Response response = client.newCall(request("remaining")).execute()) {
            JSONArray things = new JSONObject(response.body().string())
                    .getJSONObject("json").getJSONObject("data").getJSONArray("things");
            assertEquals(2, things.length());
            assertEquals("t1_great", things.getJSONObject(0).getJSONObject("data").getString("parent_id"));
            assertEquals("t1_remaining", things.getJSONObject(1).getJSONObject("data").getString("parent_id"));
        }
    }

    @Test
    public void returnsAnEmptyExpansionWhenRedditHasNoFocusedComment() throws Exception {
        List<Request> requests = new ArrayList<>();
        OkHttpClient client = client(requests, listing());

        try (Response response = client.newCall(request("missing")).execute()) {
            assertEquals(200, response.code());
            JSONArray things = new JSONObject(response.body().string())
                    .getJSONObject("json").getJSONObject("data").getJSONArray("things");
            assertEquals(0, things.length());
        }
        assertEquals(1, requests.size());
    }

    @Test
    public void continuesAfterRedditHasNoFocusedComment() throws Exception {
        List<Request> requests = new ArrayList<>();
        MoreChildrenResponsePatch shim = new MoreChildrenResponsePatch();
        String empty = listing();
        String present = listing(comment("present", "parent"));
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> shim.shimResponse(chain, chain.request()))
                .addInterceptor(chain -> {
                    requests.add(chain.request());
                    return response(chain.request(), 200,
                            requests.size() == 1 ? empty : present);
                }).build();

        try (Response response = client.newCall(request("missing,present")).execute()) {
            JSONArray things = new JSONObject(response.body().string())
                    .getJSONObject("json").getJSONObject("data").getJSONArray("things");
            assertEquals(1, things.length());
            assertEquals("present",
                    things.getJSONObject(0).getJSONObject("data").getString("id"));
        }
        assertEquals(2, requests.size());
    }

    @Test
    public void propagatesTransportFailureInsteadOfReturningAPartialSuccessfulExpansion() throws Exception {
        List<Request> requests = new ArrayList<>();
        MoreChildrenResponsePatch shim = new MoreChildrenResponsePatch();
        String first = listing(comment("first", "parent"));
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> shim.shimResponse(chain, chain.request()))
                .addInterceptor(chain -> {
                    requests.add(chain.request());
                    if (requests.size() == 1) {
                        return response(chain.request(), 200, first);
                    }
                    return response(chain.request(), 429, "{\"error\":429}");
                }).build();
        try (Response response = client.newCall(request("first,second")).execute()) {
            assertEquals(429, response.code());
            assertTrue(response.body().string().contains("429"));
        }
        assertEquals(2, requests.size());
    }

    @Test
    public void defersChildrenBeyondTheRequestBudgetToAContinuationPlaceholder() throws Exception {
        List<Request> requests = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 25; i++) ids.add("c" + i);
        OkHttpClient client = pathClient(requests, (current, id) -> response(current, 200,
                listing(id.equals("parent") ? comment("parent", "ancestor") : comment(id, "parent"))));

        try (Response response = client.newCall(request(String.join(",", ids))).execute()) {
            JSONArray things = new JSONObject(response.body().string())
                    .getJSONObject("json").getJSONObject("data").getJSONArray("things");
            int loaded = MoreChildrenResponsePatch.MAX_UPSTREAM_REQUESTS - 1;
            assertEquals(loaded + 1, things.length());
            JSONObject more = things.getJSONObject(loaded);
            assertEquals("more", more.getString("kind"));
            JSONObject moreData = more.getJSONObject("data");
            assertEquals("t1_parent", moreData.getString("parent_id"));
            assertEquals(ids.size() - loaded, moreData.getInt("count"));
            assertEquals(ids.get(loaded), moreData.getJSONArray("children").getString(0));
            assertEquals("t1_" + ids.get(loaded), moreData.getString("name"));
        }
        assertEquals(MoreChildrenResponsePatch.MAX_UPSTREAM_REQUESTS, requests.size());
    }

    @Test
    public void defersChildrenAfterServerErrorsInsteadOfFailingTheExpansion() throws Exception {
        List<Request> requests = new ArrayList<>();
        OkHttpClient client = pathClient(requests, (current, id) ->
                id.equals("parent") || id.equals("second")
                        ? response(current, 500, "{}")
                        : response(current, 200, listing(comment(id, "parent"))));

        try (Response response = client.newCall(request("first,second,third")).execute()) {
            assertEquals(200, response.code());
            JSONArray things = new JSONObject(response.body().string())
                    .getJSONObject("json").getJSONObject("data").getJSONArray("things");
            assertEquals(3, things.length());
            assertEquals("first", things.getJSONObject(0).getJSONObject("data").getString("id"));
            assertEquals("third", things.getJSONObject(1).getJSONObject("data").getString("id"));
            JSONObject moreData = things.getJSONObject(2).getJSONObject("data");
            assertEquals("second", moreData.getJSONArray("children").getString(0));
            assertEquals(1, moreData.getJSONArray("children").length());
        }
        assertEquals(4, requests.size());
    }

    @Test
    public void propagatesServerErrorsWhenNoChildCouldBeLoaded() throws Exception {
        List<Request> requests = new ArrayList<>();
        OkHttpClient client = pathClient(requests,
                (current, id) -> response(current, 503, "{\"error\":503}"));

        try (Response response = client.newCall(request("first,second")).execute()) {
            assertEquals(503, response.code());
            assertTrue(response.body().string().contains("503"));
        }
        assertEquals(2, requests.size());
    }

    private interface PathResponder {
        Response respond(Request request, String id) throws Exception;
    }

    private static OkHttpClient pathClient(List<Request> requests, PathResponder responder) {
        MoreChildrenResponsePatch shim = new MoreChildrenResponsePatch();
        return new OkHttpClient.Builder()
                .addInterceptor(chain -> shim.shimResponse(chain, chain.request()))
                .addInterceptor(chain -> {
                    Request current = chain.request();
                    requests.add(current);
                    String path = current.url().encodedPath();
                    String id = path.substring(path.lastIndexOf('/') + 1, path.length() - ".json".length());
                    try {
                        return responder.respond(current, id);
                    } catch (Exception exception) {
                        throw new java.io.IOException(exception);
                    }
                }).build();
    }

    private static OkHttpClient client(List<Request> requests, String... listings) {
        MoreChildrenResponsePatch shim = new MoreChildrenResponsePatch();
        return new OkHttpClient.Builder()
                .addInterceptor(chain -> shim.shimResponse(chain, chain.request()))
                .addInterceptor(chain -> {
                    requests.add(chain.request());
                    return response(chain.request(), 200, listings[Math.min(requests.size() - 1, listings.length - 1)]);
                }).build();
    }

    private static Request request(String children) {
        return new Request.Builder()
                .url("https://oauth.reddit.com/api/morechildren")
                .header("Authorization", "Bearer fixture")
                .post(new FormBody.Builder()
                        .add("link_id", "t3_post").add("children", children).add("sort", "new").build())
                .build();
    }

    private static Response response(Request request, int code, String json) {
        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(code).message("fixture").body(ResponseBody.create(json, JSON)).build();
    }

    private static String listing(JSONObject... comments) throws JSONException {
        return new JSONArray().put(new JSONObject()).put(new JSONObject()
                .put("kind", "Listing")
                .put("data", new JSONObject().put("children", new JSONArray(List.of(comments)))))
                .toString();
    }

    private static JSONObject comment(String id, String parent, JSONObject... replies) throws JSONException {
        Object children = replies.length == 0 ? "" : new JSONObject()
                .put("kind", "Listing").put("data",
                        new JSONObject().put("children", new JSONArray(List.of(replies))));
        return new JSONObject().put("kind", "t1").put("data", new JSONObject()
                .put("id", id).put("name", "t1_" + id).put("parent_id", "t1_" + parent)
                .put("body", "fixture").put("replies", children));
    }

    private static JSONObject more(String parent, String... ids) throws JSONException {
        return new JSONObject().put("kind", "more").put("data", new JSONObject()
                .put("id", "more_" + parent).put("parent_id", "t1_" + parent)
                .put("count", ids.length).put("children", new JSONArray(List.of(ids))));
    }
}
