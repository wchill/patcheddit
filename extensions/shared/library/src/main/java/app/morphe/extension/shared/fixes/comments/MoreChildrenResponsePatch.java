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
import java.util.HashSet;
import java.util.LinkedHashSet;
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
        Response.Builder responseBuilder = null;

        for (String childId : targetIds) {
            if (emittedIds.contains(childId)) continue;

            Request focusedRequest = request.newBuilder()
                    .url(focusedCommentUrl(request.url(), postId, childId, sort, profileImg))
                    .get()
                    .build();
            Response response = chain.proceed(focusedRequest);
            if (!response.isSuccessful()) return response;

            if (responseBuilder == null) responseBuilder = response.newBuilder();
            ResponseBody body = response.body();
            if (body == null) {
                response.close();
                throw new IOException("Reddit focused comment response has no body");
            }

            String responseJson = body.string();
            response.close();
            try {
                JSONObject focused = focusedComment(responseJson, childId);
                if (focused != null) {
                    flattenThing(focused, emittedIds, emittedFullNames, things);
                }
            } catch (JSONException exception) {
                throw new IOException("Could not parse Reddit focused comment response", exception);
            }
        }

        if (responseBuilder == null) throw new IOException("Reddit returned no focused comments");

        try {
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

    private static JSONObject focusedComment(String responseJson, String expectedId)
            throws JSONException, IOException {
        JSONArray response = new JSONArray(responseJson);
        JSONArray children = response.getJSONObject(1)
                .getJSONObject("data")
                .getJSONArray("children");
        if (children.length() == 0) return null;

        JSONObject focused = children.getJSONObject(0);
        String id = focused.getJSONObject("data").optString("id");
        if (!expectedId.equals(id)) {
            throw new IOException("Reddit returned unexpected focused comment " + id);
        }
        return focused;
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
        if (!(replies instanceof JSONObject)) return;
        JSONObject data = ((JSONObject) replies).optJSONObject("data");
        if (data == null) return;
        JSONArray children = data.optJSONArray("children");
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
