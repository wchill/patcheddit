/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.patcheddit.fixture;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public final class CommentVideoLinksDeviceTest {
    private static final String LINK = "https://www.reddit.com/link/abc123/video/def456/player";
    private static final String BASE = "https://v.redd.it/link/abc123/asset/def456";
    private static final String KEY = "morphe_patch_fix_comment_video_links";
    private static final String MODEL = "com.andrewshu.android.reddit.things.objects.ThreadMediaRedditVideo";

    public static void run(Context context, Bundle arguments) throws Exception {
        if (context.getPackageName().startsWith("ml.docilealligator")) {
            infinityControl(context);
            return;
        }
        Class<?> settings = context.getClassLoader().loadClass("app.morphe.extension.shared.settings.PatchSettings");
        SharedPreferences preferences = (SharedPreferences) settings.getMethod("preferences", Context.class)
                .invoke(null, context);
        Object previous = preferences.getAll().get(KEY);
        FutureTask<Void> task = new FutureTask<>(() -> {
            try {
                for (boolean enabled : new boolean[]{false, true, false, true}) {
                    check(preferences.edit().putBoolean(KEY, enabled).commit(), "Could not set comment-video switch");
                    if (context.getPackageName().startsWith("com.andrewshu")) rif(context, enabled);
                    else if (context.getPackageName().equals("com.rubenmayayo.reddit")) boost(context, enabled);
                    else joey(context, enabled);
                }
            } finally {
                SharedPreferences.Editor restore = preferences.edit();
                if (previous == null) restore.remove(KEY); else restore.putBoolean(KEY, (Boolean) previous);
                check(restore.commit(), "Could not restore comment-video switch");
            }
            return null;
        });
        check(new Handler(Looper.getMainLooper()).post(task), "Could not schedule native video fixture");
        task.get(60, TimeUnit.SECONDS);
    }

    private static void rif(Context context, boolean enabled) throws Exception {
        ClassLoader loader = context.getClassLoader();
        Class<?> modelType = loader.loadClass(MODEL);
        Class<?> hook = loader.loadClass("app.morphe.extension.redditisfun.CommentVideoPatch");
        Method fallback = hook.getMethod("fallback", Object.class, Uri.class);
        Object resolved = fallback.invoke(null, null, Uri.parse(LINK));
        if (enabled) verifyRifVideo(modelType, resolved);
        else check(resolved == null, "Disabled RIF fallback produced a video");
        Class<?> json = loader.loadClass("com.bluelinelabs.logansquare.LoganSquare");
        Object existing = json.getMethod("parse", String.class, Class.class).invoke(null,
                "{\"dash_url\":\"https://example.com/original.mpd\","
                        + "\"hls_url\":\"https://example.com/original.m3u8\","
                        + "\"is_gif\":true,\"width\":1920,\"height\":1080,\"duration\":42}", modelType);
        String originalJson = (String) json.getMethod("serialize", Object.class).invoke(null, existing);
        check(fallback.invoke(null, existing, Uri.parse(LINK)) == existing, "Replaced successful native metadata");
        for (String invalid : new String[]{"https://example.com/link/abc123/video/def456/player",
                "https://reddit.com/link/abc123/video/../player", "https://v.redd.it/ordinary",
                "content://reddit.com/link/abc123/video/def456/player"}) {
            check(fallback.invoke(null, null, Uri.parse(invalid)) == null, "Derived a stream for an unrelated URL");
        }
        check(fallback.invoke(null, null, null) == null, "Derived a stream without native link context");

        boolean paid = context.getPackageName().endsWith("donation");
        String prefix = paid ? "k2." : "j2.";
        Class<?> listenerType = loader.loadClass(paid ? "w1.y" : "v1.y");
        for (String family : new String[]{"a", "c"}) {
            Class<?> outerType = loader.loadClass(prefix + family);
            Class<?> callbackType = loader.loadClass(prefix + family + "$b");
            Object outer = outerType.getConstructor().newInstance();
            int[] notifications = {0};
            Object listener = Proxy.newProxyInstance(loader, new Class<?>[]{listenerType}, (proxy, method, args) -> {
                if (method.getReturnType() == void.class) {
                    notifications[0]++;
                    return null;
                }
                if (method.getName().equals("toString")) return "fixture listener";
                if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                if (method.getName().equals("equals")) return proxy == args[0];
                throw new AssertionError(method);
            });
            java.lang.reflect.Constructor<?> constructor = callbackType.getDeclaredConstructor(
                    Uri.class, Context.class, listenerType, outerType);
            constructor.setAccessible(true);
            Object callback = constructor.newInstance(Uri.parse(LINK), context, listener, outer);
            Method deliver = callbackType.getDeclaredMethod(family.equals("a") ? "h0" : "f0", modelType);
            deliver.setAccessible(true);
            deliver.invoke(callback, new Object[]{null});
            Field stored = outerType.getDeclaredField("b");
            stored.setAccessible(true);
            if (enabled) {
                verifyRifVideo(modelType, stored.get(outer));
                check(notifications[0] == 1, "Native video continuation was not invoked exactly once");
            } else {
                check(stored.get(outer) == null && notifications[0] == 0, "Disabled callback lost its native failure path");
            }
            deliver.invoke(callback, existing);
            check(stored.get(outer) == existing, "Native callback replaced successful video metadata");
            check(originalJson.equals(json.getMethod("serialize", Object.class).invoke(null, existing)),
                    "Native metadata was mutated");
        }
    }

    private static void verifyRifVideo(Class<?> model, Object value) throws Exception {
        check(model.isInstance(value), "Fallback did not create the native RIF video type");
        String serialized = (String) model.getClassLoader().loadClass("com.bluelinelabs.logansquare.LoganSquare")
                .getMethod("serialize", Object.class).invoke(null, value);
        org.json.JSONObject data = new org.json.JSONObject(serialized);
        check((BASE + "/DASHPlaylist.mpd").equals(data.getString("dash_url")), "Wrong native DASH URL");
        check((BASE + "/HLSPlaylist.m3u8").equals(data.getString("hls_url")), "Wrong native HLS URL");
        check(!data.getBoolean("is_gif"), "Comment video was marked as a silent GIF");
    }

    private static void boost(Context context, boolean enabled) throws Exception {
        Class<?> type = context.getClassLoader().loadClass("f41");
        Object source = type.getConstructor(String.class, byte.class).newInstance(LINK, (byte) 1);
        String actual = (String) type.getMethod("d").invoke(source);
        check(actual.equals(enabled ? BASE + "/DASHPlaylist.mpd" : "https://v.redd.it/def456/DASHPlaylist.mpd"),
                "Boost lost the comment container or its native off behavior");
        Object normal = type.getConstructor(String.class, byte.class)
                .newInstance("https://v.redd.it/ordinary", (byte) 1);
        check("https://v.redd.it/ordinary/DASHPlaylist.mpd".equals(type.getMethod("d").invoke(normal)),
                "Boost changed normal post-video resolution");
    }

    private static void joey(Context context, boolean enabled) throws Exception {
        ClassLoader loader = context.getClassLoader();
        Class<?> type = loader.loadClass("jd.c");
        String base = enabled ? BASE : "https://www.reddit.com/link/abc123/video/def456";
        check((base + "/DASHPlaylist.mpd").equals(type.getMethod("f", String.class).invoke(null, LINK)),
                "Joey derived the wrong DASH directory");
        String quality = (String) loader.loadClass("zb.e").getMethod("e").invoke(null);
        check((base + "/" + quality).equals(type.getMethod("e", String.class).invoke(null, LINK)),
                "Joey lost its native video-quality selection");
        check("https://v.redd.it/ordinary/DASHPlaylist.mpd".equals(
                type.getMethod("f", String.class).invoke(null, "https://v.redd.it/ordinary")),
                "Joey changed normal post-video resolution");
    }

    private static void infinityControl(Context context) throws Exception {
        Class<?> type = context.getClassLoader().loadClass(
                context.getPackageName().endsWith("patreon") ? "x77" : "oe7");
        Map<String, Object> metadata = new HashMap<>();
        type.getMethod("p", String.class, Map.class).invoke(null, LINK.replace("www.", ""), metadata);
        check(metadata.size() == 1, "Infinity did not recognize the native comment video");
        List<String> urls = new ArrayList<>();
        collectUrls(metadata.values().iterator().next(), urls, 0);
        check(urls.contains(BASE + "/HLSPlaylist.m3u8"), "Infinity's existing native route is not correct");
    }

    private static void collectUrls(Object value, List<String> urls, int depth) throws Exception {
        if (value == null || depth > 1) return;
        for (Field field : value.getClass().getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            Object child = field.get(value);
            if (child instanceof String) urls.add((String) child);
            else if (!field.getType().isPrimitive()) collectUrls(child, urls, depth + 1);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
