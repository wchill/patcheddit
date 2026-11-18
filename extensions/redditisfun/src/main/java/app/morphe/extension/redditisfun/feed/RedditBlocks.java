/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.redditisfun.feed;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

public final class RedditBlocks {
    static final int MAX_BYTES = 1024 * 1024;
    private static final Map<Object, Boolean> REQUESTS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Pattern TITLE = Pattern.compile("<title\\b[^>]*>\\s*blocked\\s*</title\\s*>",
            Pattern.CASE_INSENSITIVE);

    public interface Request {
        boolean morpheIsFeed();
    }

    public interface Loader {
        Object morpheRequest();
    }

    private RedditBlocks() {}

    public static void begin(Object request, boolean signedIn) {
        if (!(request instanceof Request) || !((Request) request).morpheIsFeed()) return;
        if (signedIn) REQUESTS.put(request, false);
        else REQUESTS.remove(request);
    }

    public static InputStream inspect(Object request, InputStream input) {
        if (REQUESTS.get(request) == null) return input;
        ByteArrayOutputStream prefix = new ByteArrayOutputStream();
        IOException failure = null;
        byte[] buffer = new byte[8192];
        try {
            int count;
            while (prefix.size() < MAX_BYTES
                    && (count = input.read(buffer, 0, Math.min(buffer.length, MAX_BYTES - prefix.size()))) != -1) {
                prefix.write(buffer, 0, count);
            }
        } catch (IOException error) {
            failure = error;
        }
        byte[] bytes = prefix.toByteArray();
        REQUESTS.put(request, failure == null && blocked(bytes));
        IOException pending = failure;
        // Replay consumed bytes before rethrowing at the original failure boundary.
        InputStream tail = failure == null ? input : new FilterInputStream(input) {
            private IOException next = pending;

            private void fail() throws IOException {
                if (next != null) {
                    IOException error = next;
                    next = null;
                    throw error;
                }
            }

            @Override public int read() throws IOException {
                fail();
                return in.read();
            }

            @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                if (length == 0) return in.read(bytes, offset, length);
                fail();
                return in.read(bytes, offset, length);
            }
        };
        return new SequenceInputStream(new ByteArrayInputStream(bytes), tail);
    }

    private static boolean blocked(byte[] prefix) {
        String html = new String(prefix, StandardCharsets.UTF_8).trim();
        if (html.startsWith("\uFEFF")) html = html.substring(1).trim();
        if (!html.startsWith("<")) return false;
        String text = html.toLowerCase(Locale.ROOT).replace("\u2019", "'")
                .replace("&#39;", "'").replace("&apos;", "'");
        return TITLE.matcher(html).find() || text.contains("you've been blocked by network security")
                || text.contains("whoa there, pardner");
    }

    static boolean take(Object loader) {
        return loader instanceof Loader
                && Boolean.TRUE.equals(REQUESTS.remove(((Loader) loader).morpheRequest()));
    }

    public static boolean show(Object loader, Context context) {
        if (context == null || context instanceof Activity
                && (((Activity) context).isFinishing() || ((Activity) context).isDestroyed()) || !take(loader)) return false;
        try {
            int message = context.getResources().getIdentifier("patcheddit_reddit_blocked", "string", context.getPackageName());
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            return true;
        } catch (Resources.NotFoundException | WindowManager.BadTokenException failure) {
            Log.e("PatchedditRedditBlocks", "Could not show the feed block explanation", failure);
            return false;
        }
    }
}
