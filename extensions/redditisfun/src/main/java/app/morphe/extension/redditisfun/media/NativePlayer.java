/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.redditisfun.media;

import android.content.Context;
import android.net.Uri;
import android.view.TextureView;
import android.view.View;

import okhttp3.OkHttpClient;

/** Bound to the input APK's existing ExoPlayer; no second playback library. */
final class NativePlayer {
    private NativePlayer() {}
    private static IllegalStateException unbound() { return new IllegalStateException("RIF inline player was not bound"); }
    static Object create(Context context, TextureView texture, Uri uri, OkHttpClient client, boolean dash) { throw unbound(); }
    static View controls(Context context, Object player) { throw unbound(); }
    static void play(Object player, boolean play) { throw unbound(); }
    static void volume(Object player, float volume) { throw unbound(); }
    static void repeat(Object player, int repeat) { throw unbound(); }
    static void seek(Object player, long position) { throw unbound(); }
    static void release(Object player) { throw unbound(); }
    static long position(Object player) { throw unbound(); }
    static int repeat(Object player) { throw unbound(); }
    static int state(Object player) { throw unbound(); }
    static boolean playing(Object player) { throw unbound(); }
    static boolean failed(Object player) { throw unbound(); }
}
