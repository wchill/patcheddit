/*
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.fixes.api;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.shared.requests.PatchedditInterceptor;
import app.morphe.extension.shared.requests.RedditRouteResponseShim;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Since late October 2026 Reddit returns {@code media_metadata} entries of galleries that were
 * created through its public API as {@code {"status":"failed"}} (or briefly
 * {@code "unprocessed"}) with no URLs. The images themselves are fine and are served at
 * {@code https://i.redd.it/<media_id>.<ext>}. Clients that only read valid entries end up with
 * empty image URLs ("Glide failed to grab this image" in Sync).
 *
 * <p>This interceptor rebuilds such entries as valid ones. i.redd.it only serves the exact
 * extension of the upload, so each candidate URL is checked with a HEAD request first.
 * Only single-post lookups are touched ({@code /api/info}, {@code /by_id/}, {@code /comments/}),
 * never subreddit or front page listings, to keep the extra requests bounded.
 * Probing is also bounded by a probe count and a time budget, and stops at the first
 * transport error, so a slow i.redd.it can't hold up the original response for long.
 *
 * <p>Same approach as Apollo Reborn PR #1589.
 */
public final class FailedGalleryMetadataPatch extends PatchedditInterceptor {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final long MAX_BODY_BYTES = 8L * 1024 * 1024;
    private static final int MAX_PROBES_PER_RESPONSE = 80;
    /** Overall time allowed for repairing one response, on top of the original request. */
    private static final long REPAIR_BUDGET_NANOS = TimeUnit.SECONDS.toNanos(6);
    private static final int PROBE_TIMEOUT_SECONDS = 3;
    private static final String[] DEFAULT_EXTENSIONS = {"jpg", "png", "jpeg", "webp", "gif"};
    private static final Pattern MEDIA_URL = Pattern.compile(
            "redd\\.it/(?:[^/?#]*-)?([a-z0-9]+)\\.([a-z0-9]{3,4})(?:[?#]|$)",
            Pattern.CASE_INSENSITIVE);

    enum Probe {FOUND, MISSING, FAILED}

    /**
     * Checks whether a URL exists. {@link Probe#FAILED} means a transport error, after which no
     * more probes are made for the response. Throws only if the call was canceled.
     * Swappable for tests.
     */
    interface Prober {
        Probe probe(Chain chain, Request original, String url) throws IOException;
    }

    private final Prober prober;

    public FailedGalleryMetadataPatch() {
        this(FailedGalleryMetadataPatch::head);
    }

    FailedGalleryMetadataPatch(Prober prober) {
        this.prober = prober;
    }

    @Override
    public boolean isPatchIncluded() {
        // Overridden by patch.
        return false;
    }

    @NonNull
    @Override
    protected Response doIntercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        Response response = chain.proceed(request);
        if (!RedditRouteResponseShim.isRedditHost(request.url().host())) return response;
        String path = RedditRouteResponseShim.normalizePath(request.url().encodedPath());
        boolean singlePost = path.equals("/api/info") || path.startsWith("/by_id/")
                || path.startsWith("/comments/") || path.contains("/comments/");
        if (!singlePost || !response.isSuccessful()) return response;
        String encoding = response.header("Content-Encoding");
        if (encoding != null && !encoding.equalsIgnoreCase("identity")) return response;
        ResponseBody body = response.body();
        MediaType type = body == null ? null : body.contentType();
        if (type == null || !type.toString().toLowerCase(Locale.ROOT).contains("json")) {
            return response;
        }
        if (body.contentLength() > MAX_BODY_BYTES) return response;

        String text;
        try (ResponseBody peek = response.peekBody(MAX_BODY_BYTES + 1)) {
            if (peek.contentLength() > MAX_BODY_BYTES) return response;
            text = peek.string();
        }
        if (!text.contains("media_metadata") || !text.contains("gallery_data")
                || !(text.contains("\"failed\"") || text.contains("\"unprocessed\""))) {
            return response;
        }

        final String repaired;
        try {
            Object json = new JSONTokener(text).nextValue();
            Repairer repairer = new Repairer(chain, request, System.nanoTime() + REPAIR_BUDGET_NANOS);
            repairer.walk(json);
            if (repairer.changed == 0) return response;
            repaired = json.toString();
        } catch (JSONException | RuntimeException exception) {
            // Never break a response we could not understand.
            return response;
        } catch (IOException canceled) {
            response.close();
            throw canceled;
        }
        response.close();
        return response.newBuilder()
                .removeHeader("Content-Length")
                .removeHeader("ETag")
                .header("Content-Type", "application/json; charset=utf-8")
                .body(ResponseBody.create(repaired, JSON))
                .build();
    }

    private final class Repairer {
        final Chain chain;
        final Request original;
        final long deadline;
        int probes;
        int changed;
        boolean stopped;
        String lastExtension;

        Repairer(Chain chain, Request original, long deadline) {
            this.chain = chain;
            this.original = original;
            this.deadline = deadline;
        }

        void walk(Object node) throws JSONException, IOException {
            if (node instanceof JSONArray) {
                JSONArray array = (JSONArray) node;
                for (int i = 0; i < array.length(); i++) walk(array.get(i));
            } else if (node instanceof JSONObject) {
                JSONObject object = (JSONObject) node;
                if (object.optJSONObject("media_metadata") != null
                        && object.optJSONObject("gallery_data") != null) {
                    repairPost(object);
                }
                JSONArray names = object.names();
                if (names == null) return;
                for (int i = 0; i < names.length(); i++) {
                    Object child = object.opt(names.getString(i));
                    if (child instanceof JSONObject || child instanceof JSONArray) walk(child);
                }
            }
        }

        void repairPost(JSONObject post) throws JSONException, IOException {
            JSONObject metadata = post.getJSONObject("media_metadata");
            JSONArray items = post.getJSONObject("gallery_data").optJSONArray("items");
            if (items == null) return;
            String hintedId = null;
            String hintedExtension = null;
            for (String key : new String[]{"thumbnail", "url_overridden_by_dest", "url"}) {
                Matcher matcher = MEDIA_URL.matcher(post.optString(key, ""));
                if (matcher.find()) {
                    hintedId = matcher.group(1);
                    hintedExtension = matcher.group(2).toLowerCase(Locale.ROOT);
                    break;
                }
            }
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                String id = item.optString("media_id", "");
                if (!id.matches("[A-Za-z0-9]+")) continue;
                JSONObject entry = metadata.optJSONObject(id);
                if (entry != null && entry.optJSONObject("s") != null) continue;
                String status = entry == null ? "" : entry.optString("status", "");
                if (entry != null && !status.equals("failed") && !status.equals("unprocessed")) {
                    continue;
                }
                String mimeExtension = entry == null ? null : extensionFromMime(entry.optString("m", ""));
                String extension = findExtension(id, mimeExtension,
                        id.equalsIgnoreCase(hintedId) ? hintedExtension : null, hintedExtension);
                if (extension == null) continue;
                String url = "https://i.redd.it/" + id + "." + extension;
                JSONObject source = new JSONObject().put("u", url);
                if (extension.equals("gif")) source.put("gif", url);
                JSONObject fixed = entry == null ? new JSONObject() : entry;
                fixed.put("status", "valid")
                        .put("e", extension.equals("gif") ? "AnimatedImage" : "Image")
                        .put("m", "image/" + (extension.equals("jpeg") ? "jpg" : extension))
                        .put("id", id)
                        .put("s", source)
                        .put("p", new JSONArray());
                metadata.put(id, fixed);
                changed++;
            }
        }

        String findExtension(String id, String... hints) throws IOException {
            LinkedHashSet<String> candidates = new LinkedHashSet<>();
            for (String hint : hints) if (hint != null) candidates.add(hint);
            if (lastExtension != null) candidates.add(lastExtension);
            for (String extension : DEFAULT_EXTENSIONS) candidates.add(extension);
            List<String> ordered = new ArrayList<>(candidates);
            for (String extension : ordered) {
                if (stopped || probes >= MAX_PROBES_PER_RESPONSE || System.nanoTime() >= deadline) {
                    stopped = true;
                    return null;
                }
                probes++;
                Probe result = prober.probe(chain, original, "https://i.redd.it/" + id + "." + extension);
                if (result == Probe.FOUND) {
                    lastExtension = extension;
                    return extension;
                }
                if (result == Probe.FAILED) {
                    // i.redd.it is unreachable or stalling; don't hold the response any longer.
                    stopped = true;
                    return null;
                }
            }
            return null;
        }
    }

    static String extensionFromMime(String mime) {
        if (mime == null) return null;
        String lower = mime.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("image/")) return null;
        String subtype = lower.substring("image/".length());
        switch (subtype) {
            case "jpg":
            case "jpeg":
            case "png":
            case "webp":
            case "gif":
                return subtype;
            default:
                return null;
        }
    }

    private static Probe head(Chain chain, Request original, String url) throws IOException {
        if (chain.call().isCanceled()) throw new IOException("Canceled");
        String userAgent = original.header("User-Agent");
        Request probe = new Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", userAgent == null ? "Mozilla/5.0" : userAgent)
                .build();
        Chain bounded = chain
                .withConnectTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .withReadTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        try (Response response = bounded.proceed(probe)) {
            if (response.code() == 200) return Probe.FOUND;
            return response.code() == 404 ? Probe.MISSING : Probe.FAILED;
        } catch (IOException exception) {
            if (chain.call().isCanceled()) throw exception;
            return Probe.FAILED;
        }
    }
}
