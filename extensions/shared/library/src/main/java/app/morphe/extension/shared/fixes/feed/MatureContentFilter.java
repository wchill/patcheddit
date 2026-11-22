/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.fixes.feed;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.util.Iterator;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import app.morphe.extension.shared.settings.MatureContentLock;

public final class MatureContentFilter {
    static final int MAX_BODY = 16 * 1024 * 1024;

    private MatureContentFilter() {}

    static boolean isRedditHost(String host) {
        return host != null && (host.equals("reddit.com") || host.endsWith(".reddit.com"));
    }

    static boolean isCommunityNameSearch(String path) {
        return path.equals("/api/search_reddit_names") || path.equals("/api/subreddit_autocomplete");
    }

    static boolean isSearch(String path) {
        return isCommunityNameSearch(path) || path.endsWith("/search")
                || path.endsWith("/subreddits/search") || path.endsWith("/api/subreddit_autocomplete_v2");
    }

    static String path(String path) {
        String lower = path.toLowerCase(Locale.ROOT).replaceFirst("/+$", "");
        return lower.endsWith(".json") ? lower.substring(0, lower.length() - 5) : lower;
    }

    public static String filterLegacyResponse(String body, String url, int code, String contentType) throws IOException {
        if (!MatureContentLock.isLocked() || url == null || body == null || code < 200 || code >= 300) return body;
        URI route;
        try {
            route = URI.create(url);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Could not identify the Reddit response for content filtering.", exception);
        }
        if (!isRedditHost(route.getHost()) || !isJson(contentType)) return body;
        return filter(body, path(route.getPath()));
    }

    static boolean isJson(String contentType) {
        if (contentType == null) return false;
        String type = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return type.equals("application/json") || type.endsWith("+json");
    }

    // Infinity's native Retrofit response body has no request URL. Filter only Reddit-shaped data.
    public static String filterNativeBody(String body) throws IOException {
        if (!MatureContentLock.isLocked() || body == null) return body;
        String value = body.trim();
        return value.startsWith("{") || value.startsWith("[") ? filter(body, null) : body;
    }

    public static Reader filterNativeReader(Reader reader) throws IOException {
        if (!MatureContentLock.isLocked()) return reader;
        try (Reader source = reader) {
            StringWriter text = new StringWriter();
            char[] buffer = new char[8192];
            for (int count; (count = source.read(buffer)) != -1; ) {
                if (text.getBuffer().length() + count > MAX_BODY) {
                    throw new IOException("Response exceeds the mature content filter's size limit.");
                }
                text.write(buffer, 0, count);
            }
            return new StringReader(filterNativeBody(text.toString()));
        }
    }

    static String filter(String body, String path) throws IOException {
        return filter(body, path, false);
    }

    static String filter(String body, String path, boolean safeNamesRequested) throws IOException {
        if (body.length() > MAX_BODY) throw new IOException("Reddit response exceeds the content filter's size limit.");
        try {
            JSONTokener parser = new JSONTokener(body);
            Object root = parser.nextValue();
            if (parser.nextClean() != 0) throw new JSONException("Unexpected data after Reddit JSON.");
            if (root instanceof JSONObject && blocked((JSONObject) root)) {
                throw new IOException("Mature content is disabled.");
            }
            boolean changed = filterChildren(root, 0, false);
            if (path != null && root instanceof JSONObject) {
                JSONObject object = (JSONObject) root;
                // This endpoint returns bare names without a mature-content label.
                if (!safeNamesRequested && isCommunityNameSearch(path) && object.optJSONArray("names") != null) {
                    object.put("names", new JSONArray());
                    changed = true;
                }
                if (path.equals("/api/v1/me/prefs") || path.equals("/api/v1/me")) {
                    JSONObject preferences = object.optJSONObject("data");
                    if (preferences == null) preferences = object;
                    for (String key : new String[]{"over_18", "over18", "search_include_over_18"}) {
                        if (preferences.optBoolean(key, false)) {
                            preferences.put(key, false);
                            changed = true;
                        }
                    }
                }
            }
            return changed ? root.toString() : body;
        } catch (JSONException exception) {
            throw new IOException("Could not filter the Reddit JSON response.", exception);
        }
    }

    private static boolean filterChildren(Object value, int depth, boolean communities) throws JSONException {
        if (depth > 64) throw new JSONException("Reddit JSON is nested too deeply.");
        boolean changed = false;
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = array.length() - 1; i >= 0; i--) {
                Object item = array.opt(i);
                if (item instanceof JSONObject && (blocked((JSONObject) item) || (communities && marked((JSONObject) item)))) {
                    array.remove(i);
                    changed = true;
                } else {
                    changed |= filterChildren(item, depth + 1, false);
                }
            }
        } else if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            for (Iterator<String> keys = object.keys(); keys.hasNext(); ) {
                String key = keys.next();
                Object child = object.opt(key);
                if (child instanceof JSONObject && blocked((JSONObject) child)) {
                    keys.remove();
                    changed = true;
                } else {
                    changed |= filterChildren(child, depth + 1, "subreddits".equals(key));
                }
            }
        }
        return changed;
    }

    private static boolean blocked(JSONObject object) {
        String kind = object.optString("kind");
        JSONObject data = object.optJSONObject("data");
        if (data == null) data = object;
        boolean thing = kind.equals("t3") || kind.equals("t5") || data.has("display_name")
                || (data.has("subreddit") && data.has("title"));
        if (!thing) return false;
        if (marked(data) || marked(data.optJSONObject("sr_detail"))) return true;
        JSONArray crossposts = data.optJSONArray("crosspost_parent_list");
        if (crossposts != null) {
            for (int i = 0; i < crossposts.length(); i++) {
                if (marked(crossposts.optJSONObject(i))) return true;
            }
        }
        return false;
    }

    private static boolean marked(JSONObject data) {
        return data != null && (data.optBoolean("over_18") || data.optBoolean("over18"));
    }
}
