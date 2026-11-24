/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.fixes.api;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import app.morphe.extension.shared.requests.RedditRequestParameters;
import app.morphe.extension.shared.requests.RedditRouteResponseShim;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class RedditInfoResponsePatch extends RedditRouteResponseShim {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final long MAX_CONTEXT_BYTES = 8L * 1024 * 1024;
    private static final int POST_PAGE_SIZE = 10;
    private static final RedditCommentContext SHARED_CONTEXT = new RedditCommentContext(10_000);
    private final RedditCommentContext context;
    private final boolean pagePostById;

    public RedditInfoResponsePatch() {
        this(false);
    }

    RedditInfoResponsePatch(RedditCommentContext context) {
        this(context, false);
    }

    public RedditInfoResponsePatch(boolean pagePostById) {
        this(SHARED_CONTEXT, pagePostById);
    }

    RedditInfoResponsePatch(RedditCommentContext context, boolean pagePostById) {
        super(request -> shouldIndex(request, normalizePath(request.url().encodedPath())));
        this.context = context;
        this.pagePostById = pagePostById;
    }

    @Override
    public boolean isPatchIncluded() {
        // Overridden by patch.
        return false;
    }

    @NonNull
    @Override
    protected Response shimResponse(@NonNull Chain chain, @NonNull Request request) throws IOException {
        String path = normalizePath(request.url().encodedPath());
        boolean info = path.equals("/api/info")
                && (request.method().equals("GET") || request.method().equals("POST"));
        boolean byId = path.startsWith("/by_id/") && request.method().equals("GET");
        if (pagePostById && byId) {
            Map<String, String> parameters = RedditRequestParameters.read(request);
            List<Lookup> lookups = plan(request, path, parameters);
            if (lookups != null && lookups.stream().allMatch(lookup -> lookup.kind.equals("t3"))) {
                return lookupPostPage(chain, request, parameters, lookups);
            }
        }
        Response response = chain.proceed(request);

        if ((info || byId) && isBlocked(response)) {
            final Map<String, String> parameters;
            final List<Lookup> lookups;
            try {
                parameters = RedditRequestParameters.read(request);
                lookups = plan(request, path, parameters);
            } catch (IOException exception) {
                response.close();
                throw exception;
            }
            if (lookups == null) {
                return response;
            }
            response.close();
            return lookup(chain, request, parameters, lookups, null);
        }
        indexResponse(response, request);
        return response;
    }

    private static boolean isBlocked(Response response) {
        return response.code() == 403 && response.header("WWW-Authenticate") == null
                && response.header("Content-Type", "").toLowerCase(Locale.ROOT).contains("text/html");
    }

    private Response lookupPostPage(Chain chain, Request original, Map<String, String> parameters,
                                    List<Lookup> lookups) throws IOException {
        List<String> names = new ArrayList<>(lookups.size());
        for (Lookup lookup : lookups) names.add("t3_" + lookup.id);
        String after = parameters.get("after");
        int start = after == null ? 0 : names.indexOf(after) + 1;
        if (after != null && (start == 0 || start == names.size())) {
            throw new IOException("Invalid Reddit post lookup cursor");
        }
        PageBudget budget = new PageBudget();
        while (true) {
            int end = Math.min(start + POST_PAGE_SIZE, names.size());
            List<String> page = names.subList(start, end);
            String ids = String.join(",", page);
            HttpUrl.Builder url = original.url().newBuilder()
                    .encodedPath("/by_id/" + ids + ".json")
                    .removeAllQueryParameters("after")
                    .removeAllQueryParameters("before")
                    .setQueryParameter("limit", Integer.toString(page.size()));
            Response response = budget.proceed(chain, lookupRequest(original, url.build()));
            if (isBlocked(response)) {
                response.close();
                url.encodedPath("/api/info.json").setQueryParameter("id", ids);
                response = budget.proceed(chain, lookupRequest(original, url.build()));
                if (isBlocked(response)) {
                    response.close();
                    response = lookup(chain, original, parameters, lookups.subList(start, end), budget);
                }
            }
            if (!response.isSuccessful()) return response;
            try (Response pageResponse = response) {
                JSONArray ordered = orderedPosts(pageResponse, page);
                if (ordered.length() != 0 || end == names.size()) {
                    return listingResponse(original, pageResponse.newBuilder(), ordered,
                            end == names.size() ? null : names.get(end - 1));
                }
            }
            // Boost cannot scroll a completely empty page to ask for the next one.
            start = end;
        }
    }

    private static JSONArray orderedPosts(Response response, List<String> page) throws IOException {
        Map<String, JSONObject> found = new HashMap<>();
        try {
            JSONArray children = new JSONObject(response.body().string())
                    .getJSONObject("data").getJSONArray("children");
            for (int i = 0; i < children.length(); i++) {
                JSONObject post = children.getJSONObject(i);
                if (post.getString("kind").equals("t3")) {
                    found.put(post.getJSONObject("data").getString("name").toLowerCase(Locale.ROOT), post);
                }
            }
        } catch (JSONException exception) {
            throw new IOException("Could not read Reddit post lookup listing", exception);
        }
        JSONArray ordered = new JSONArray();
        for (String id : page) {
            JSONObject post = found.get(id);
            if (post != null) ordered.put(post);
        }
        return ordered;
    }

    private List<Lookup> plan(Request request, String path, Map<String, String> parameters)
            throws IOException {
        String ids = parameters.get("id");
        if (path.startsWith("/by_id/")) {
            try {
                ids = URLDecoder.decode(path.substring("/by_id/".length()), "UTF-8");
            } catch (IllegalArgumentException exception) {
                throw new IOException("Invalid Reddit fullname path", exception);
            }
        }
        String communities = parameters.get("sr_name");
        List<Lookup> result = new ArrayList<>();
        if (ids == null && communities != null) {
            for (String name : unique(communities)) {
                if (!name.matches("[a-z0-9_]{1,64}")) {
                    throw new IOException("Invalid Reddit community name");
                }
                result.add(new Lookup("t5", name, null));
            }
        } else if (ids != null) {
            for (String fullname : unique(ids)) {
                String postId = RedditCommentContext.idFromFullname(fullname, "t3_");
                if (postId != null) {
                    result.add(new Lookup("t3", postId, null));
                    continue;
                }
                String commentId = RedditCommentContext.idFromFullname(fullname, "t1_");
                if (commentId == null) return null;
                postId = RedditCommentContext.idFromFullname(parameters.get("link_id"), "t3_");
                if (postId == null) postId = RedditCommentContext.postFromLink(request.header("Referer"));
                if (postId == null) postId = context.postFor(fullname);
                if (postId == null) return null;
                result.add(new Lookup("t1", postId, commentId));
            }
        } else {
            throw new IOException("Reddit info request has neither id nor sr_name");
        }
        if (result.isEmpty()) throw new IOException("Reddit info request has no lookup targets");
        return result;
    }

    private static LinkedHashSet<String> unique(String values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values.split(",")) {
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isEmpty()) result.add(normalized);
        }
        return result;
    }

    private Response lookup(Chain chain, Request original, Map<String, String> parameters,
                            List<Lookup> lookups, PageBudget budget) throws IOException {
        JSONArray things = new JSONArray();
        Response.Builder result = null;
        for (Lookup lookup : lookups) {
            HttpUrl.Builder url = original.url().newBuilder()
                    .encodedPath(lookup.path())
                    .query(null);
            copyParameter(parameters, url, "raw_json");
            copyParameter(parameters, url, "profile_img");
            if (budget != null) copyParameter(parameters, url, "sr_detail");
            if (!lookup.kind.equals("t5")) {
                url.addQueryParameter("limit", "1").addQueryParameter("depth", "1");
            }
            if (lookup.kind.equals("t1")) url.addQueryParameter("context", "0");
            Request focused = lookupRequest(original, url.build());
            Response response = budget == null ? chain.proceed(focused) : budget.proceed(chain, focused);
            boolean missing = response.code() == 404 && isJson(response);
            if (budget != null && !missing && isUnavailablePost(response)) missing = true;
            if (!response.isSuccessful() && !missing) return response;
            result = response.newBuilder().code(200).message("OK");
            try {
                if (missing) continue;
                ResponseBody body = response.body();
                if (body == null) throw new IOException("Reddit lookup response has no body");
                Object json = new JSONTokener(body.string()).nextValue();
                JSONObject thing = lookup.thing(json);
                if (thing != null) {
                    context.index(thing, lookup.kind.equals("t5") ? null : lookup.id);
                    things.put(thing);
                }
            } catch (JSONException exception) {
                throw new IOException("Could not parse Reddit lookup response", exception);
            } finally {
                response.close();
            }
        }
        if (result == null) throw new IOException("Reddit lookup returned no responses");
        return listingResponse(original, result, things, null);
    }

    private static boolean isUnavailablePost(Response response) throws IOException {
        if (response.code() != 403 || response.header("WWW-Authenticate") != null || !isJson(response)) {
            return false;
        }
        try (ResponseBody peek = response.peekBody(4096)) {
            String reason = new JSONObject(peek.string()).optString("reason");
            return reason.equals("private") || reason.equals("banned") || reason.equals("quarantined");
        } catch (JSONException ignored) {
            return false; // Preserve the original HTTP error.
        } catch (IOException failure) {
            response.close();
            throw failure;
        }
    }

    private static Request lookupRequest(Request original, HttpUrl url) {
        return original.newBuilder()
                .url(url)
                .header("Accept-Encoding", "identity")
                .removeHeader("Content-Type")
                .removeHeader("Content-Length")
                .removeHeader("Content-Encoding")
                .removeHeader("Transfer-Encoding")
                .removeHeader("If-None-Match")
                .removeHeader("If-Modified-Since")
                .removeHeader("If-Match")
                .removeHeader("If-Unmodified-Since")
                .removeHeader("Range")
                .removeHeader("If-Range")
                .get().build();
    }

    private static Response listingResponse(Request original, Response.Builder result,
                                            JSONArray things, String after) throws IOException {
        try {
            JSONObject data = new JSONObject()
                    .put("children", things)
                    .put("before", JSONObject.NULL)
                    .put("after", after == null ? JSONObject.NULL : after)
                    .put("dist", things.length())
                    .put("modhash", "");
            String json = new JSONObject().put("kind", "Listing").put("data", data).toString();
            return result.request(original).code(200).message("OK")
                    .removeHeader("Content-Length")
                    .removeHeader("Content-Encoding")
                    .removeHeader("Transfer-Encoding")
                    .removeHeader("ETag")
                    .removeHeader("Last-Modified")
                    .header("Content-Type", "application/json; charset=utf-8")
                    .body(ResponseBody.create(json, JSON)).build();
        } catch (JSONException exception) {
            throw new IOException("Could not build Reddit info listing", exception);
        }
    }

    private static final class PageBudget {
        private final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        private int remainingRequests = 2 * (POST_PAGE_SIZE + 2);

        Response proceed(Chain chain, Request request) throws IOException {
            if (chain.call().isCanceled() || Thread.currentThread().isInterrupted()) {
                throw new IOException("Reddit post lookup canceled");
            }
            long remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
            if (remainingMillis < 2 || remainingRequests-- == 0) {
                throw new IOException("Reddit post lookup page exceeded its request budget");
            }
            int timeout = (int) Math.min(2500, remainingMillis / 2);
            return chain.withConnectTimeout(timeout, TimeUnit.MILLISECONDS)
                    .withReadTimeout(timeout, TimeUnit.MILLISECONDS)
                    .proceed(request);
        }
    }

    private static void copyParameter(Map<String, String> parameters, HttpUrl.Builder url, String name) {
        String value = parameters.get(name);
        if (value != null) url.addQueryParameter(name, value);
    }

    private static boolean shouldIndex(Request request, String path) {
        return path.equals("/api/info") || path.startsWith("/by_id/")
                || path.equals("/api/morechildren") || path.equals("/api/comment")
                || path.equals("/api/editusertext")
                || (request.method().equals("GET") && (path.contains("/comments")
                || path.startsWith("/message/") || path.startsWith("/user/")
                || path.startsWith("/r/")));
    }

    private static boolean isJson(Response response) {
        ResponseBody body = response.body();
        MediaType type = body == null ? null : body.contentType();
        return type != null && type.toString().toLowerCase(Locale.ROOT).contains("json");
    }

    private void indexResponse(Response response, Request request) throws IOException {
        if (!response.isSuccessful() || !isJson(response)) return;
        String encoding = response.header("Content-Encoding");
        if (encoding != null && !encoding.equalsIgnoreCase("identity")) return;
        if (response.body().contentLength() > MAX_CONTEXT_BYTES) return;
        try (ResponseBody peek = response.peekBody(MAX_CONTEXT_BYTES + 1)) {
            if (peek.contentLength() > MAX_CONTEXT_BYTES) return;
            String text = peek.string();
            if (!text.contains("\"t1\"")) return;
            String postId = RedditCommentContext.postFromPath(request.url().encodedPath());
            if (postId == null) {
                postId = RedditCommentContext.idFromFullname(
                        request.url().queryParameter("link_id"), "t3_");
            }
            context.index(new JSONTokener(text).nextValue(), postId);
        } catch (JSONException exception) {
            throw new IOException("Could not read Reddit comment context", exception);
        }
    }

    private static final class Lookup {
        final String kind;
        final String id;
        final String commentId;

        Lookup(String kind, String id, String commentId) {
            this.kind = kind;
            this.id = id;
            this.commentId = commentId;
        }

        String path() {
            if (kind.equals("t5")) return "/r/" + id + "/about.json";
            if (kind.equals("t1")) return "/comments/" + id + "/comment/" + commentId + ".json";
            return "/comments/" + id + ".json";
        }

        JSONObject thing(Object json) throws JSONException, IOException {
            JSONObject thing;
            if (kind.equals("t5")) {
                if (!(json instanceof JSONObject)) throw new JSONException("Expected community object");
                thing = (JSONObject) json;
                if (!id.equalsIgnoreCase(thing.getJSONObject("data").getString("display_name"))) {
                    throw new IOException("Reddit returned an unexpected community");
                }
            } else {
                if (!(json instanceof JSONArray)) throw new JSONException("Expected comment listings");
                JSONArray children = ((JSONArray) json).getJSONObject(kind.equals("t3") ? 0 : 1)
                        .getJSONObject("data").getJSONArray("children");
                if (children.length() == 0) return null;
                thing = children.getJSONObject(0);
                String expected = kind + "_" + (kind.equals("t1") ? commentId : id);
                if (!expected.equalsIgnoreCase(thing.getJSONObject("data").getString("name"))) {
                    throw new IOException("Reddit returned an unexpected lookup fullname");
                }
                if (kind.equals("t1")) thing.getJSONObject("data").put("replies", "");
            }
            if (!kind.equals(thing.getString("kind"))) {
                throw new IOException("Reddit returned an unexpected lookup kind");
            }
            return thing;
        }
    }
}
