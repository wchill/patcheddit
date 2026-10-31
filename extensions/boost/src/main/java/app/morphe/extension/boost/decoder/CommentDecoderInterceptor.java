/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.boost.decoder;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import app.morphe.extension.boost.utils.MarkdownRenderer;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.requests.PatchInterceptor;
import app.morphe.extension.shared.requests.RedditRouteResponseShim;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Adds decoded NATO and binary blocks to comments in Reddit responses. Only body_html is
 * rewritten: Boost displays it, while copying and editing keep using the original body.
 *
 * @noinspection unused
 */
public final class CommentDecoderInterceptor extends PatchInterceptor {
    private static Set<String> allowedSubreddits;

    @Override
    public boolean isPatchIncluded() {
        // Overridden by patch.
        return false;
    }

    /**
     * Comma-separated subreddit allowlist. Empty means every subreddit.
     */
    public static String getAllowedSubreddits() {
        // Overridden by patch.
        return "";
    }

    @NonNull
    @Override
    protected Response doIntercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        Response response = chain.proceed(request);
        if (!response.isSuccessful()
                || !RedditRouteResponseShim.isRedditHost(request.url().host())
                || !mayContainComments(RedditRouteResponseShim.normalizePath(request.url().encodedPath()))) {
            return response;
        }
        ResponseBody body = response.body();
        MediaType type = body == null ? null : body.contentType();
        if (type == null || !"json".equals(type.subtype())) return response;

        String json = body.string();
        String patched = json;
        try {
            patched = decodeComments(json, "1".equals(request.url().queryParameter("raw_json")));
        } catch (JSONException | RuntimeException ex) {
            Logger.printException(() -> "Failed to decode comments in " + request.url().encodedPath(), ex);
        }
        return response.newBuilder()
                .removeHeader("Content-Length")
                .body(ResponseBody.create(patched, type))
                .build();
    }

    private static boolean mayContainComments(String path) {
        return path.contains("/comments/")
                || path.endsWith("/comments")
                || path.startsWith("/user/")
                || path.equals("/api/morechildren")
                || path.equals("/api/info");
    }

    private static String decodeComments(String json, boolean rawJson) throws JSONException {
        Object root = new JSONTokener(json).nextValue();
        boolean changed = false;
        // Iterative, so deep reply trees cannot overflow the stack.
        ArrayDeque<Object> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            Object node = pending.pop();
            if (node instanceof JSONArray) {
                JSONArray array = (JSONArray) node;
                for (int i = 0; i < array.length(); i++) pending.push(array.get(i));
            } else if (node instanceof JSONObject) {
                JSONObject object = (JSONObject) node;
                if ("t1".equals(object.optString("kind"))) {
                    JSONObject data = object.optJSONObject("data");
                    if (data != null) changed |= decodeComment(data, rawJson);
                }
                for (Iterator<String> keys = object.keys(); keys.hasNext(); ) {
                    pending.push(object.get(keys.next()));
                }
            }
        }
        return changed ? root.toString() : json;
    }

    private static boolean decodeComment(JSONObject data, boolean rawJson) throws JSONException {
        if (data.isNull("body") || !isAllowedSubreddit(data.optString("subreddit"))) return false;
        String body = data.getString("body");
        // Without raw_json, Reddit entity-escapes these three characters in body.
        if (!rawJson) body = body.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");

        String annotated = CommentDecoder.annotate(body);
        if (annotated.equals(body)) return false;
        data.put("body_html", MarkdownRenderer.render(annotated));
        return true;
    }

    private static boolean isAllowedSubreddit(String subreddit) {
        if (allowedSubreddits == null) {
            Set<String> parsed = new HashSet<>();
            for (String entry : getAllowedSubreddits().split(",")) {
                String name = entry.trim().toLowerCase(Locale.ROOT).replaceFirst("^/?r/", "");
                if (!name.isEmpty()) parsed.add(name);
            }
            allowedSubreddits = parsed;
        }
        return allowedSubreddits.isEmpty()
                || allowedSubreddits.contains(subreddit.toLowerCase(Locale.ROOT));
    }
}
