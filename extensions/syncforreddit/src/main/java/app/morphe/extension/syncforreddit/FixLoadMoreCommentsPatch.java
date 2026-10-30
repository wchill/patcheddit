/*
 * Copyright 2026 trulow.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 * Adapted from https://github.com/notBrian2/revanced-rif-fixes.
 */

package app.morphe.extension.syncforreddit;

import android.util.Log;

import com.android.volley.NetworkResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reddit blocks /api/morechildren for third-party clients, so Sync's "load more comments" fails. The
 * comments are still served by /comments/{link}/_/{parent}, the endpoint Sync already uses for single
 * comment threads, so OAuthCommentsMoreRequest is sent there as a GET and the reply is cut down to the
 * flat {"json":{"data":{"things":[...]}}} list morechildren returned.
 *
 * Call order, all on the main thread: {@link #setMoreItem} (CommentsPresenterImpl, with the "more" row),
 * {@link #setMoreArgs} (start of the request constructor), {@link #moreUrl} and {@link #moreMethod}
 * (right before the request's super constructor). {@link #moreResponse} runs later on a network thread.
 */
@SuppressWarnings("unused")
public final class FixLoadMoreCommentsPatch {
    private static final String MARK = "patcheddit_more=";
    private static final Pattern PARENT_ID = Pattern.compile("[{ ]parent_id=([^,}]*)");

    private static final ThreadLocal<String> PARENT = new ThreadLocal<>();
    private static final ThreadLocal<String[]> ARGS = new ThreadLocal<>();
    /** Request key to {parent fullname, comma separated wanted ids}. */
    private static final Map<String, String[]> PENDING = new ConcurrentHashMap<>();
    private static final AtomicInteger NEXT_KEY = new AtomicInteger();

    private FixLoadMoreCommentsPatch() {
    }

    /**
     * Hooked into CommentsPresenterImpl's "load more" handler with the Post row of the "more" item. Its
     * toString() is AutoValue's "Post{id=.., link_id=.., parent_id=.., ...}", which avoids depending on the
     * obfuscated getter names that differ between Sync and Sync Pro.
     */
    public static void setMoreItem(Object post) {
        String parent = null;
        if (post != null) {
            Matcher m = PARENT_ID.matcher(post.toString());
            if (m.find()) {
                parent = m.group(1);
            }
        }
        PARENT.set(parent);
    }

    /** Hooked into the start of OAuthCommentsMoreRequest's constructor. */
    public static void setMoreArgs(String linkId, String children, String sort) {
        ARGS.set(new String[]{linkId, children, sort});
    }

    /** Replaces the api/morechildren URL the request is constructed with. */
    public static String moreUrl(String url) {
        String parent = PARENT.get();
        String[] args = ARGS.get();
        PARENT.remove();
        ARGS.remove();
        int api = url == null ? -1 : url.indexOf("api/morechildren");
        if (api < 0 || args == null || isEmpty(args[0]) || isEmpty(args[1])) {
            return url;
        }
        String linkId = args[0].startsWith("t3_") ? args[0].substring(3) : args[0];
        boolean topLevel = parent == null || !parent.startsWith("t1_");

        StringBuilder sb = new StringBuilder(url.substring(0, api)).append("comments/").append(linkId);
        if (!topLevel) {
            sb.append("/_/").append(parent.substring(3));
        }
        // Reddit caps one fetch at 500 comments. At the top level spend them on top-level comments and their
        // first replies: deeper replies come back as their own "load more" (the nested case, which loads fully).
        sb.append(".json?always_show_media=1&profile_img=true&limit=500&depth=").append(topLevel ? 2 : 10);
        String sort = args[2];
        if (!isEmpty(sort) && !"suggested".equalsIgnoreCase(sort)) {
            sb.append("&sort=").append(sort.toLowerCase(Locale.ENGLISH).replace("&", ""));
        }
        // The id list can be hundreds of ids long (Reddit answers 414), so the URL only carries a key to it.
        if (PENDING.size() > 50) {
            PENDING.clear();
        }
        String key = Integer.toString(NEXT_KEY.incrementAndGet());
        PENDING.put(key, new String[]{topLevel ? "t3_" + linkId : parent, args[1]});
        return sb.append('&').append(MARK).append(key).toString();
    }

    /** The Volley method for the request: rewritten requests are listing fetches, sent as GET. */
    public static int moreMethod(int method, String url) {
        return key(url) == null ? method : 0 /* Request.Method.GET */;
    }

    /** Hooked into OAuthCommentsMoreRequest.parseNetworkResponse() with the request URL. */
    public static NetworkResponse moreResponse(String url, NetworkResponse response) {
        String key = key(url);
        String[] pending = key == null ? null : PENDING.get(key);
        if (pending == null || response == null || response.data == null) {
            return response;
        }
        try {
            return Responses.withBody(response, things(Responses.body(response), pending[0], pending[1]));
        } catch (JSONException e) {
            Log.w(Responses.TAG, "Could not convert more comments response", e);
            return response;
        }
    }

    private static String things(String body, String parent, String wantedIds) throws JSONException {
        Set<String> wanted = new HashSet<>(Arrays.asList(wantedIds.split(",")));
        Set<String> shown = new HashSet<>();
        shown.add(parent);
        JSONArray things = new JSONArray();
        JSONArray top = new JSONArray(body).getJSONObject(1).getJSONObject("data").getJSONArray("children");
        if (walk(top, parent, wanted, shown, things) == 0) {
            // Nothing new within Reddit's 500 comment window (bottom of a huge thread). Without a "data"
            // object Sync's parser fails, so it shows "Error loading more comments" and keeps the item.
            return "{\"json\":{\"errors\":[[\"SYNC_FIXES\",\"These comments could not be loaded\",\"\"]]}}";
        }
        return new JSONObject().put("json", new JSONObject()
                .put("errors", new JSONArray())
                .put("data", new JSONObject().put("things", things))).toString();
    }

    /**
     * Pre-order walk, like morechildren: a parent always comes before its replies, which is what Sync uses to
     * work out each comment's indentation. Returns the number of comments added.
     */
    private static int walk(JSONArray children, String parent, Set<String> wanted, Set<String> shown,
                            JSONArray things) throws JSONException {
        int added = 0;
        for (int i = 0; i < children.length(); i++) {
            JSONObject child = children.optJSONObject(i);
            JSONObject data = child == null ? null : child.optJSONObject("data");
            if (data == null) {
                continue;
            }
            String parentId = data.optString("parent_id");
            String kind = child.optString("kind");
            if ("t1".equals(kind)) {
                JSONObject replies = data.optJSONObject("replies");
                // Like morechildren: the requested comments plus everything under them.
                boolean underAdded = shown.contains(parentId) && !parentId.equals(parent);
                if (underAdded || (wanted.contains(data.optString("id")) && shown.contains(parentId))) {
                    data.put("replies", "");
                    things.put(child);
                    shown.add(data.optString("name"));
                    added++;
                }
                JSONObject repliesData = replies == null ? null : replies.optJSONObject("data");
                JSONArray sub = repliesData == null ? null : repliesData.optJSONArray("children");
                if (sub != null) {
                    added += walk(sub, parent, wanted, shown, things);
                }
            } else if ("more".equals(kind) && shown.contains(parentId)) {
                // A deeper "load more" under a comment we added: keep it. One directly under the parent is only
                // useful while it still holds requested comments.
                if (!parentId.equals(parent) || overlaps(data.optJSONArray("children"), wanted)) {
                    things.put(child);
                }
            }
        }
        return added;
    }

    private static boolean overlaps(JSONArray ids, Set<String> wanted) {
        if (ids == null) {
            return false;
        }
        for (int i = 0; i < ids.length(); i++) {
            if (wanted.contains(ids.optString(i))) {
                return true;
            }
        }
        return false;
    }

    private static String key(String url) {
        int i = url == null ? -1 : url.indexOf(MARK);
        if (i < 0) {
            return null;
        }
        int start = i + MARK.length();
        int end = url.indexOf('&', start);
        return end < 0 ? url.substring(start) : url.substring(start, end);
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }
}
