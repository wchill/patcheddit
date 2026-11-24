package app.morphe.extension.redditisfun.media;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebView;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Tap-to-open routes for RIF's stock browser/image viewer, independent of inline media. */
public final class OpenMediaLinks {
    private static final String FALLBACK = "morphe-rif-media-fallback";
    private static final long CACHE_LIFETIME = TimeUnit.MINUTES.toNanos(5);
    // Only public Matrix-to-CDN redirects are cached. Account-dependent Reddit shares aren't.
    private static final Map<URI, CachedImage> IMAGES = new LinkedHashMap<URI, CachedImage>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<URI, CachedImage> eldest) {
            return size() > 32;
        }
    };

    private OpenMediaLinks() {}

    public static boolean openRouter(String link, String display, Object linkType, String thread, String title,
                                     boolean nsfw, Object whitelist, Object fragments, Context context, Object source) {
        URI target = target(link);
        if (target == null) return false;
        Context caller = context != null ? context : Utils.getContext();
        if (caller == null) return false;
        if (!RifMediaLinks.isMatrix(target)) {
            openNative(Uri.parse(target.toString()), display, thread, title, nsfw, whitelist, fragments, caller, source);
            return true;
        }
        open(target, image -> openNative(image == null ? fallback(link) : Uri.parse(image.toString()), display,
                thread, title, nsfw, whitelist, fragments, caller, source));
        return true;
    }

    public static boolean openActivity(Activity activity) {
        Intent intent = activity.getIntent();
        if (intent == null || !(Intent.ACTION_VIEW.equals(intent.getAction())
                || "android.nfc.action.NDEF_DISCOVERED".equals(intent.getAction()))) return false;
        String link = intent.getDataString();
        URI target = target(link);
        if (target == null) return false;
        if (!RifMediaLinks.isMatrix(target)) {
            // Let the same stock activity initialize its native viewer with the original extras.
            intent.setDataAndType(Uri.parse(target.toString()), intent.getType());
            return false;
        }
        open(target, image -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            Intent replay = new Intent(intent).setClass(activity, activity.getClass())
                    .setDataAndType(image == null ? fallback(link) : Uri.parse(image.toString()), intent.getType());
            activity.startActivity(replay);
        });
        return true;
    }

    public static boolean openWebView(Context context, WebView view, String link) {
        URI target = target(link);
        if (target == null || context == null || view == null) return false;
        if (!RifMediaLinks.isMatrix(target)) {
            openNative(Uri.parse(target.toString()), null, null, null, false, null, null, context, null);
            return true;
        }
        open(target, image -> {
            if (image == null) {
                // Programmatic loadUrl does not call shouldOverrideUrlLoading again.
                view.loadUrl(link);
            } else {
                openNative(Uri.parse(image.toString()), null, null, null, false, null, null, context, null);
            }
        });
        return true;
    }

    private static URI target(String link) {
        return link == null || FALLBACK.equals(Uri.parse(link).getFragment()) ? null : RifMediaLinks.target(link);
    }

    private static Uri fallback(String link) {
        return Uri.parse(link).buildUpon().fragment(FALLBACK).build();
    }

    @FunctionalInterface
    private interface ImageResult {
        void open(URI image);
    }

    private static final class CachedImage {
        final URI image;
        final long time = System.nanoTime();

        CachedImage(URI image) {
            this.image = image;
        }
    }

    private static void open(URI matrix, ImageResult result) {
        CachedImage cached;
        synchronized (IMAGES) {
            cached = IMAGES.get(matrix);
        }
        if (cached != null && System.nanoTime() - cached.time < CACHE_LIFETIME) {
            Utils.runOnMainThread(() -> result.open(cached.image));
            return;
        }
        Utils.runOnBackgroundThread(() -> {
            URI resolved = null;
            try {
                resolved = RifMediaLinks.resolve(matrix, null);
                if (resolved != null) {
                    synchronized (IMAGES) {
                        IMAGES.put(matrix, new CachedImage(resolved));
                    }
                }
            } catch (IOException ignored) {
                Logger.printDebug(() -> "Could not resolve RIF Matrix image");
            }
            URI image = resolved;
            Utils.runOnMainThread(() -> result.open(image));
        });
    }

    // Rewritten with the verified free/Platinum browser router types.
    private static void openNative(Uri image, String display, String thread, String title, boolean nsfw,
                                   Object whitelist, Object fragments, Context context, Object source) {}
}
