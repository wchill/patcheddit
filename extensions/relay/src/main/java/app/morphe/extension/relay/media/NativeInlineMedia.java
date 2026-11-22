/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.relay.media;

import android.widget.TextView;

/** Bound to Relay's own Glide by the patch. */
public final class NativeInlineMedia {
    private NativeInlineMedia() {}

    public static boolean isIncluded() { return false; }
    public static void load(TextView view, String url, Object target, int width, int height) {
        throw new IllegalStateException("Relay inline-media loader was not installed");
    }
    public static void clear(TextView view, Object target) {
        throw new IllegalStateException("Relay inline-media loader was not installed");
    }
}
