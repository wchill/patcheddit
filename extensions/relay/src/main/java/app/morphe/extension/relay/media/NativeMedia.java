/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.relay.media;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.Callable;

import reddit.news.oauth.reddit.model.links.MediaPreview;

/** Bound to validated Relay APIs by the patch. */
public final class NativeMedia {
    private NativeMedia() {}

    public static Object just(ArrayList<MediaPreview> previews) { throw unbound(); }
    public static Object defer(Callable<ArrayList<MediaPreview>> request) { throw unbound(); }
    public static ArrayList<MediaPreview> parseDash(Object fetcher, String url, InputStream input) {
        throw unbound();
    }
    public static Object gifRequest(Object manager, String url) { throw unbound(); }
    public static boolean isRedditVideo(Object url) { throw unbound(); }

    private static IllegalStateException unbound() {
        return new IllegalStateException("Relay media bridge was not installed");
    }
}
