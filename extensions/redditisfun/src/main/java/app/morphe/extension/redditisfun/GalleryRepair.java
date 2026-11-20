/*
 * Gallery repair by MojiRS, from https://github.com/MojiRS/morphe-rif-patches v1.9.0.
 * Adapted for this bundle. Licensed under GPL-3.0; see the repository LICENSE.
 */

package app.morphe.extension.redditisfun;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Repairs a Reddit gallery response before rif parses it.
 *
 * Reddit sends {"status":"failed"} with no "s" or "p" for the items of some galleries, and rif
 * rejects the whole gallery ("Error loading gallery") when any entry has no size. The images
 * still exist at i.redd.it/&lt;media_id&gt;.&lt;ext&gt;: this finds them with HEAD requests and
 * writes a normal entry for each. Items with no image are dropped. A gallery with nothing wrong
 * makes no network calls, and any failure leaves rif's input untouched.
 *
 * Called from the start of rif's gallery response parser, which runs on a pool thread.
 */
public final class GalleryRepair {
    private static final String TAG = "RifGalleryRepair";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 rif-inline-images";
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final Pattern MEDIA_ID = Pattern.compile("[A-Za-z0-9]+");
    private static final String[] EXTENSIONS = {"jpeg", "png", "gif", "webp", "jpg"};
    private static final int TIMEOUT_MS = 3000;
    // Items that miss every extension before the rest are written off (a deleted gallery).
    private static final int MAX_MISSED_ITEMS = 2;
    private static final long MAX_PROBE_MS = 15_000L;

    // The extension that hit last: galleries are usually uploaded in one format.
    private static volatile String lastHit;

    private GalleryRepair() {}

    static void forgetLastHit() {
        lastHit = null;
    }

    /** Whether an image exists at the URL. */
    interface Probe {
        boolean exists(String url);
    }

    /** Hook target: returns the stream rif should parse, never null for a non-null input. */
    public static InputStream repairGallery(InputStream in) {
        if (in == null) return null;
        ByteArrayOutputStream read = new ByteArrayOutputStream();
        try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) read.write(buf, 0, n);
            return new ByteArrayInputStream(repair(read.toByteArray(), GalleryRepair::headOk));
        } catch (Throwable t) {
            // What was read so far: rif sees the same truncated input it would have.
            Log.w(TAG, "gallery repair failed", t);
            return new ByteArrayInputStream(read.toByteArray());
        }
    }

    /**
     * The JSON to hand to rif: [original] itself if it needs no repair or can't be repaired, else
     * a copy with the failed images found through [probe] and the rest removed.
     */
    static byte[] repair(byte[] original, Probe probe) {
        try {
            return repairOrThrow(original, probe);
        } catch (Throwable t) {
            Log.w(TAG, "gallery repair failed", t);
            return original;
        }
    }

    private static byte[] repairOrThrow(byte[] original, Probe probe) throws JSONException {
        // The listing is either an object ({data: {children: [...]}}) or, for some requests, an
        // array of listings ([{data: {children: [...]}}, ...]) whose first one holds the post.
        Object root = new JSONTokener(new String(original, UTF_8)).nextValue();
        JSONObject listing;
        if (root instanceof JSONObject) {
            listing = (JSONObject) root;
        } else if (root instanceof JSONArray) {
            listing = ((JSONArray) root).getJSONObject(0);
        } else {
            return original;
        }
        JSONObject post = listing.getJSONObject("data").getJSONArray("children")
                .getJSONObject(0).getJSONObject("data");
        JSONObject meta = post.optJSONObject("media_metadata");
        JSONObject galleryData = post.optJSONObject("gallery_data");
        JSONArray items = galleryData == null ? null : galleryData.optJSONArray("items");
        if (meta == null || items == null) return original;

        List<String> brokenKeys = new ArrayList<>();
        Iterator<String> keys = meta.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!hasMedia(meta.optJSONObject(key))) brokenKeys.add(key);
        }
        if (brokenKeys.isEmpty()) return original;

        JSONArray kept = new JSONArray();
        int usable = 0;
        int missed = 0;
        long deadline = System.nanoTime() + MAX_PROBE_MS * 1_000_000L;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            String id = item == null ? null : item.optString("media_id", null);
            if (id == null || !meta.has(id)) {
                if (item != null) kept.put(item);
                continue;
            }
            JSONObject entry = meta.optJSONObject(id);
            if (hasMedia(entry)) {
                usable++;
                kept.put(item);
                continue;
            }
            JSONObject fixed = null;
            if (missed < MAX_MISSED_ITEMS && System.nanoTime() < deadline) {
                fixed = find(id, probe);
                if (fixed == null) missed++;
            }
            if (fixed != null) {
                meta.put(id, fixed);
                usable++;
                kept.put(item);
            } else {
                meta.remove(id);
            }
        }
        galleryData.put("items", kept);

        // An entry no item refers to would still make rif reject the gallery.
        brokenKeys.clear();
        keys = meta.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!hasMedia(meta.optJSONObject(key))) brokenKeys.add(key);
        }
        for (String key : brokenKeys) meta.remove(key);

        // Nothing to show: keep rif's own error message.
        if (usable == 0) return original;
        return root.toString().getBytes(UTF_8);
    }

    /** True if rif can size the entry: it has a source size or at least one preview. */
    private static boolean hasMedia(JSONObject entry) {
        if (entry == null) return false;
        if (entry.optJSONObject("s") != null) return true;
        JSONArray p = entry.optJSONArray("p");
        return p != null && p.length() > 0;
    }

    /** A rif media_metadata entry for the image behind [mediaId], or null if none is found. */
    private static JSONObject find(String mediaId, Probe probe) {
        if (!MEDIA_ID.matcher(mediaId).matches()) return null;
        String first = lastHit;
        List<String> order = new ArrayList<>();
        if (first != null) order.add(first);
        for (String ext : EXTENSIONS) if (!ext.equals(first)) order.add(ext);
        for (String ext : order) {
            String url = "https://i.redd.it/" + mediaId + "." + ext;
            boolean hit;
            try {
                hit = probe.exists(url);
            } catch (Throwable t) {
                hit = false;
            }
            if (!hit) continue;
            lastHit = ext;
            try {
                return entry(url, ext);
            } catch (JSONException e) {
                return null;
            }
        }
        return null;
    }

    static JSONObject entry(String url, String ext) throws JSONException {
        boolean gif = ext.equals("gif");
        JSONObject s = new JSONObject();
        s.put("u", url);
        s.put("x", 0);
        s.put("y", 0);
        if (gif) s.put("gif", url);
        JSONObject entry = new JSONObject();
        entry.put("status", "valid");
        entry.put("e", gif ? "AnimatedImage" : "Image");
        entry.put("m", "image/" + (ext.equals("jpg") ? "jpeg" : ext));
        entry.put("s", s);
        return entry;
    }

    /** HEAD without redirects: only 200 counts (a wrong extension gives 404 with an image body). */
    static boolean headOk(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("HEAD");
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            // With text/html i.redd.it answers 307 to reddit.com/media.
            c.setRequestProperty("Accept", "image/*");
            c.setRequestProperty("User-Agent", USER_AGENT);
            return c.getResponseCode() == 200;
        } catch (Throwable t) {
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
