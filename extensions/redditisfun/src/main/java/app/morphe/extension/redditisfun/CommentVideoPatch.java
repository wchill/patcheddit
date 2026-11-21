/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.redditisfun;

import android.net.Uri;

import app.morphe.extension.shared.fixes.media.CommentVideoLinks;

public final class CommentVideoPatch {
    private CommentVideoPatch() {}

    public static Object fallback(Object nativeResult, Uri link) {
        if (nativeResult != null || link == null) return nativeResult;
        String base = CommentVideoLinks.baseUrl(link.toString());
        if (base == null) return null;
        return createVideo(base + "/DASHPlaylist.mpd", base + "/HLSPlaylist.m3u8");
    }

    public static Object createVideo(String dash, String hls) {
        throw new IllegalStateException("Native RIF video factory was not bound");
    }
}
