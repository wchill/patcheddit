/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.fixes.comments;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import app.morphe.extension.shared.requests.RedditRequestParameters;
import app.morphe.extension.shared.requests.RedditRouteResponseShim;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class MoreChildrenResponsePatch extends RedditRouteResponseShim {
    private static final MediaType JSON = MediaType.get("application/json");
    // Keeps large thread expansions responsive and well below Reddit's API rate limit.
    static final int MAX_UPSTREAM_REQUESTS = 20;

    public MoreChildrenResponsePatch() {
        super("/api/morechildren", "GET", "POST");
    }

    @Override
    public boolean isPatchIncluded() {
        // Overridden by patch.
        return false;
    }

    @NonNull
    @Override
    protected Response shimResponse(@NonNull Chain chain, @NonNull Request request)
            throws IOException {
        Map<String, String> parameters = RedditRequestParameters.read(request);
        String linkId = required(parameters, "link_id");
        String children = required(parameters, "children");
        String postId = stripPrefix(linkId, "t3_");
        String sort = parameters.get("sort");
        String profileImg = parameters.get("profile_img");

        LinkedHashSet<String> targetIds = new LinkedHashSet<>();
        for (String child : children.split(",")) {
            if (!child.isEmpty()) targetIds.add(stripPrefix(child, "t1_"));
        }
        if (targetIds.isEmpty()) throw new IOException("Reddit morechildren request has no child IDs");

        JSONArray things = new JSONArray();
        Set<String> emittedIds = new HashSet<>();
        Set<String> emittedFullNames = new HashSet<>();
        Map<String, JSONObject> bulkComments = new HashMap<>();
        List<String> deferredIds = new ArrayList<>();
        boolean bulkAttempted = false;
        boolean progressed = false;
        int upstreamRequests = 0;
        String continuationParent = null;
        int continuationDepth = 0;
        Response failure = null;
        Response.Builder responseBuilder = null;

        for (String childId : targetIds) {
            if (emittedIds.contains(childId)) continue;

            JSONObject focused = bulkComments.get(childId);
            if (focused == null) {
                if (upstreamRequests >= MAX_UPSTREAM_REQUESTS) {
                    deferredIds.add(childId);
                    continue;
                }
                upstreamRequests++;
                Request focusedRequest = request.newBuilder()
                        .url(focusedCommentUrl(request.url(), postId, childId, sort, profileImg))
                        .get()
                        .build();
                Response response = chain.proceed(focusedRequest);
                if (!response.isSuccessful()) {
                    if (response.code() < 500) {
                        closeQuietly(failure);
                        return response;
                    }
                    // Retry comments hit by transient server errors through a continuation.
                    closeQuietly(failure);
                    failure = response;
                    deferredIds.add(childId);
                    continue;
                }

                if (responseBuilder == null) responseBuilder = response.newBuilder();
                String responseJson = responseBody(response);
                response.close();
                try {
                    focused = focusedComment(responseJson, childId);
                } catch (JSONException exception) {
                    closeQuietly(failure);
                    throw new IOException("Could not parse Reddit focused comment response", exception);
                }
            }
            progressed = true;
            if (focused == null) continue;

            try {
                JSONObject focusedData = focused.getJSONObject("data");
                String parentId = focusedData.optString("parent_id");
                if (continuationParent == null) {
                    continuationParent = parentId;
                    continuationDepth = focusedData.optInt("depth", 0);
                }
                flattenThing(focused, emittedIds, emittedFullNames, things);

                int remaining = 0;
                if (!bulkAttempted) {
                    for (String id : targetIds) {
                        if (!emittedIds.contains(id) && ++remaining == 2) break;
                    }
                }
                if (remaining >= 2
                        && upstreamRequests < MAX_UPSTREAM_REQUESTS
                        && (parentId.startsWith("t1_") || parentId.equals("t3_" + postId))) {
                    bulkAttempted = true;
                    upstreamRequests++;
                    boolean topLevel = parentId.startsWith("t3_");
                    String parentCommentId = topLevel ? null : stripPrefix(parentId, "t1_");
                    Request bulkRequest = request.newBuilder()
                            .url(parentListingUrl(request.url(), postId, parentCommentId, sort, profileImg))
                            .get()
                            .build();
                    Response bulkResponse = chain.proceed(bulkRequest);
                    if (!bulkResponse.isSuccessful()) {
                        int code = bulkResponse.code();
                        if (code != 404 && code < 500) {
                            closeQuietly(failure);
                            return bulkResponse;
                        }
                        bulkResponse.close();
                    } else {
                        String bulkJson = responseBody(bulkResponse);
                        bulkResponse.close();
                        JSONArray candidates;
                        if (topLevel) {
                            candidates = listingChildren(bulkJson);
                        } else {
                            JSONObject parent = focusedComment(bulkJson, parentCommentId);
                            candidates = parent == null ? null : replyChildren(parent.getJSONObject("data").opt("replies"));
                        }
                        if (candidates != null) collectComments(candidates, targetIds, bulkComments);
                    }
                }
            } catch (JSONException exception) {
                closeQuietly(failure);
                throw new IOException("Could not parse Reddit comment listing", exception);
            }
        }

        if (!progressed && failure != null) return failure;
        closeQuietly(failure);
        if (responseBuilder == null) throw new IOException("Reddit returned no focused comments");

        try {
            JSONArray continuation = new JSONArray();
            for (String id : deferredIds) {
                if (!emittedIds.contains(id)) continuation.put(id);
            }
            if (continuation.length() > 0) {
                // Like Reddit, return the remaining children as a placeholder for the next request.
                String firstId = continuation.getString(0);
                things.put(new JSONObject().put("kind", "more").put("data", new JSONObject()
                        .put("count", continuation.length())
                        .put("name", "t1_" + firstId)
                        .put("id", firstId)
                        .put("parent_id", continuationParent != null ? continuationParent : "t3_" + postId)
                        .put("depth", continuationDepth)
                        .put("children", continuation)));
            }
            JSONObject data = new JSONObject().put("things", things);
            JSONObject json = new JSONObject()
                    .put("errors", new JSONArray())
                    .put("data", data);
            String responseJson = new JSONObject().put("json", json).toString();
            return responseBuilder
                    .request(request)
                    .removeHeader("Content-Length")
                    .header("Content-Type", "application/json")
                    .body(ResponseBody.create(responseJson, JSON))
                    .build();
        } catch (JSONException exception) {
            throw new IOException("Could not build Reddit morechildren response", exception);
        }
    }

    private static HttpUrl focusedCommentUrl(
            HttpUrl original,
            String postId,
            String commentId,
            String sort,
            String profileImg
    ) {
        HttpUrl.Builder url = original.newBuilder()
                .encodedPath("/comments/" + postId + "/comment/" + commentId + ".json")
                .query(null)
                .addQueryParameter("context", "0")
                .addQueryParameter("raw_json", "1");
        if (sort != null && !sort.isEmpty()) url.addQueryParameter("sort", sort);
        if (profileImg != null && !profileImg.isEmpty()) {
            url.addQueryParameter("profile_img", profileImg);
        }
        return url.build();
    }

    private static HttpUrl parentListingUrl(
            HttpUrl original, String postId, String parentCommentId, String sort, String profileImg
    ) {
        HttpUrl.Builder url = (parentCommentId == null
                ? original.newBuilder().encodedPath("/comments/" + postId + ".json").query(null)
                : focusedCommentUrl(original, postId, parentCommentId, sort, profileImg).newBuilder())
                .addQueryParameter("limit", "500")
                .addQueryParameter("depth", parentCommentId == null ? "2" : "10");
        if (parentCommentId == null) {
            url.addQueryParameter("raw_json", "1");
            if (sort != null && !sort.isEmpty()) url.addQueryParameter("sort", sort);
            if (profileImg != null && !profileImg.isEmpty()) {
                url.addQueryParameter("profile_img", profileImg);
            }
        }
        return url.build();
    }

    private static void closeQuietly(Response response) {
        if (response != null) response.close();
    }

    private static String responseBody(Response response) throws IOException {
        ResponseBody body = response.body();
        if (body == null) {
            response.close();
            throw new IOException("Reddit comment response has no body");
        }
        return body.string();
    }

    private static JSONArray listingChildren(String responseJson) throws JSONException {
        return new JSONArray(responseJson).getJSONObject(1)
                .getJSONObject("data").getJSONArray("children");
    }

    private static JSONObject focusedComment(String responseJson, String expectedId)
            throws JSONException, IOException {
        JSONArray children = listingChildren(responseJson);
        if (children.length() == 0) return null;

        JSONObject focused = children.getJSONObject(0);
        String id = focused.getJSONObject("data").optString("id");
        if (!expectedId.equals(id)) {
            throw new IOException("Reddit returned unexpected focused comment " + id);
        }
        return focused;
    }

    private static JSONArray replyChildren(Object replies) {
        if (!(replies instanceof JSONObject)) return null;
        JSONObject data = ((JSONObject) replies).optJSONObject("data");
        return data == null ? null : data.optJSONArray("children");
    }

    private static void collectComments(
            JSONArray children, Set<String> targets, Map<String, JSONObject> comments
    ) throws JSONException {
        for (int i = 0; i < children.length(); i++) {
            JSONObject thing = children.getJSONObject(i);
            if (!"t1".equals(thing.getString("kind"))) continue;
            JSONObject data = thing.getJSONObject("data");
            String id = data.getString("id");
            if (targets.contains(id)) comments.put(id, thing);
            JSONArray replies = replyChildren(data.opt("replies"));
            if (replies != null) collectComments(replies, targets, comments);
        }
    }

    private static void flattenThing(
            JSONObject thing,
            Set<String> emittedIds,
            Set<String> emittedFullNames,
            JSONArray output
    ) throws JSONException {
        String kind = thing.getString("kind");
        JSONObject data = thing.getJSONObject("data");

        if ("t1".equals(kind)) {
            Object replies = data.opt("replies");
            String id = data.getString("id");
            if (emittedIds.add(id)) {
                // morechildren is flat, but a focused listing includes complete reply subtrees.
                data.put("replies", "");
                output.put(thing);
                emittedFullNames.add(data.getString("name"));
            }
            flattenReplies(replies, emittedIds, emittedFullNames, output);
        } else if ("more".equals(kind)
                && emittedFullNames.contains(data.optString("parent_id"))) {
            output.put(thing);
        } else {
            throw new JSONException("Unexpected focused comment kind or parent: " + kind);
        }
    }

    private static void flattenReplies(
            Object replies,
            Set<String> emittedIds,
            Set<String> emittedFullNames,
            JSONArray output
    ) throws JSONException {
        JSONArray children = replyChildren(replies);
        if (children == null) return;
        for (int i = 0; i < children.length(); i++) {
            flattenThing(
                    children.getJSONObject(i),
                    emittedIds,
                    emittedFullNames,
                    output
            );
        }
    }

    private static String required(Map<String, String> parameters, String name) throws IOException {
        String value = parameters.get(name);
        if (value == null || value.isEmpty()) {
            throw new IOException("Reddit morechildren request is missing " + name);
        }
        return value;
    }

    private static String stripPrefix(String value, String prefix) throws IOException {
        if (value == null || !value.startsWith(prefix)) return value;
        String stripped = value.substring(prefix.length());
        if (stripped.isEmpty()) throw new IOException("Invalid Reddit fullname " + value);
        return stripped;
    }
}
