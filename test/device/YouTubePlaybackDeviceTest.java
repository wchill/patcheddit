/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.patcheddit.fixture;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONTokener;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** On a disposable device, opens a public video through each app's native YouTube route. */
public final class YouTubePlaybackDeviceTest {
    private static final String ACTIVITY = "app.morphe.extension.youtube.YouTubeActivity";
    private static final String ID = "M7lc1UVf-VE";
    private static final String WATCH_URL = "https://www.youtube.com/watch?v=" + ID + "&t=6s";
    private static final String SHORTS_URL = "https://www.youtube.com/shorts/" + ID + "?t=6s";

    public static void run(Context context, Bundle options) throws Exception {
        verifyVideo(context, WATCH_URL);
        verifyVideo(context, SHORTS_URL);
    }

    private static void verifyVideo(Context context, String url) throws Exception {
        var instrumentation = PatchedditInstrumentation.instance;
        var monitor = instrumentation.addMonitor(ACTIVITY, null, false);
        Activity activity;
        try {
            onMain(() -> { openVideo(context, url); return null; });
            activity = monitor.waitForActivityWithTimeout(12_000);
            check(activity != null, "The native route did not open the replacement player for " + url);
        } finally {
            instrumentation.removeMonitor(monitor);
        }

        try {
            var web = onMain(() -> (WebView) ((android.view.ViewGroup) field(activity, "playerView")).getChildAt(0));
            JSONArray playback = null;
            for (int attempt = 0; attempt < 40; attempt++) {
                Object result = query(web, """
                        typeof player !== 'undefined' && player.getVideoData
                            ? JSON.stringify([location.origin, player.getVideoData().video_id, player.getCurrentTime()])
                            : null
                        """);
                if (result instanceof String text) {
                    playback = new JSONArray(text);
                    if (ID.equals(playback.optString(1)) && playback.optDouble(2, 0) > 6.5) break;
                }
                Thread.sleep(500);
            }
            check(playback != null && ("https://" + context.getPackageName()).equals(playback.optString(0)),
                    "The iframe did not use the installed app's HTTPS origin");
            check(ID.equals(playback.optString(1)) && playback.optDouble(2, 0) > 6.5,
                    "The embedded video didn't advance beyond the requested timestamp");
        } finally {
            onMain(() -> { activity.finish(); return null; });
        }
    }

    private static void openVideo(Context context, String url) throws Exception {
        ClassLoader loader = context.getClassLoader();
        String pkg = context.getPackageName();
        if (pkg.startsWith("com.andrewshu.")) {
            boolean platinum = pkg.endsWith("donation");
            Class<?> dispatcher = loader.loadClass(platinum ? "m3.g" : "l3.g");
            Class<?> browserMode = loader.loadClass(platinum ? "o3.d" : "n3.d");
            Object mode = java.util.Arrays.stream(browserMode.getEnumConstants())
                    .filter(value -> "ALLOW_IN_APP_OVERRIDE".equals(((Enum<?>) value).name()))
                    .findFirst().orElseThrow();
            for (Method method : dispatcher.getDeclaredMethods()) {
                if (!method.getName().equals("g") || method.getParameterCount() != 10
                        || method.getParameterTypes()[2] != browserMode) continue;
                method.setAccessible(true);
                method.invoke(null, Uri.parse(url), "YouTube fixture", mode, null, null, false, null, null, context, null);
                return;
            }
            throw new AssertionError("Missing RIF in-app link dispatcher");
        }
        Class<?> settings = loader.loadClass("com.laurencedawson.reddit_sync.singleton.SettingsSingleton");
        Object current = settings.getMethod("j").invoke(settings.getMethod("d").invoke(null));
        Field embedded = current.getClass().getField("embedYouTube");
        boolean original = embedded.getBoolean(current);
        embedded.setBoolean(current, true);
        try {
            boolean pro = pkg.endsWith(".pro");
            Class<?> route = loader.loadClass(pro ? "j7.b" : "y7.b");
            Object awards = Array.newInstance(loader.loadClass("r0.d"), 0);
            route.getMethod(pro ? "d" : "c", Context.class, awards.getClass(), String.class,
                    String.class, String.class, String.class, String.class, boolean.class, boolean.class, boolean.class)
                    .invoke(null, context, awards, "", url, url, "YouTube fixture", "", false, false, false);
        } finally {
            embedded.setBoolean(current, original);
        }
    }

    private static Object query(WebView web, String script) throws Exception {
        var result = new CompletableFuture<String>();
        onMain(() -> { web.evaluateJavascript(script, result::complete); return null; });
        return new JSONTokener(result.get(5, TimeUnit.SECONDS)).nextValue();
    }

    private static Object field(Object instance, String name) throws Exception {
        var field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    private static <T> T onMain(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        PatchedditInstrumentation.instance.runOnMainSync(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
