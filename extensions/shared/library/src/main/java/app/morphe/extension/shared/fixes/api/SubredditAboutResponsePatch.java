/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.fixes.api;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.regex.Pattern;
import java.util.logging.Level;
import java.util.logging.Logger;

import app.morphe.extension.shared.requests.RedditRouteResponseShim;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Supplies omitted display counters for strict subreddit-about parsers. */
public final class SubredditAboutResponsePatch extends RedditRouteResponseShim {
    private static final long MAX_BYTES = 512 * 1024;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final Logger LOGGER = Logger.getLogger(SubredditAboutResponsePatch.class.getName());
    private static final Pattern ABOUT = Pattern.compile("/r/[^/]+/about");

    public SubredditAboutResponsePatch() {
        super(request -> request.method().equals("GET")
                && ABOUT.matcher(normalizePath(request.url().encodedPath())).matches());
    }

    @Override
    public boolean isPatchIncluded() {
        return false; // Enabled by the client's API meta-patch.
    }

    @NonNull
    @Override
    protected Response shimResponse(@NonNull Chain chain, @NonNull Request request) throws IOException {
        Response response = chain.proceed(request);
        if (!response.isSuccessful()) return response;
        ResponseBody body = response.body();
        MediaType type = body == null ? null : body.contentType();
        if (type == null || !(type.subtype().equals("json") || type.subtype().endsWith("+json"))
                || body.contentLength() > MAX_BYTES
                || !response.header("Content-Encoding", "identity").equalsIgnoreCase("identity")) return response;
        String text;
        try (ResponseBody peek = response.peekBody(MAX_BYTES + 1)) {
            if (peek.contentLength() > MAX_BYTES) return response;
            text = peek.string();
        }
        try {
            JSONObject root = new JSONObject(text);
            JSONObject data = root.optJSONObject("data");
            if (!root.optString("kind").equals("t5") || data == null) return response;
            boolean changed = false;
            for (String key : new String[]{"accounts_active", "subscribers"}) {
                if (data.isNull(key)) {
                    data.put(key, 0);
                    changed = true;
                }
            }
            if (!changed) return response;
            response.close();
            return response.newBuilder()
                    .removeHeader("Content-Length").removeHeader("Content-Encoding")
                    .removeHeader("Transfer-Encoding").removeHeader("ETag").removeHeader("Last-Modified")
                    .header("Content-Type", JSON.toString())
                    .body(ResponseBody.create(root.toString(), JSON)).build();
        } catch (JSONException exception) {
            LOGGER.log(Level.WARNING, "Could not read subreddit display counters", exception);
            return response;
        }
    }
}
