/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.patcheddit.fixture;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public final class BaconReaderSyncCommentVideoDeviceTest {
    private static final String KEY = "morphe_patch_fix_comment_video_links";
    private static final String LINK = "https://www.reddit.com/link/abc123/video/def456/player?context=3";
    private static final String BASE = "https://v.redd.it/link/abc123/asset/def456";
    private static final String WRONG_HOST = LINK.replace("www.reddit.com", "example.com");
    private static final String ORDINARY_VIDEO = "https://v.redd.it/ordinary123";
    private static final String BACON_BROWSER = "com.onelouder.baconreader.BrowserActivity";
    private static final String SYNC_VIEWER = "com.laurencedawson.reddit_sync.ui.activities.media.SingleImageActivity";
    private static final String SYNC_PLAYER = "com.laurencedawson.reddit_sync.ui.views.video.CustomExoPlayerView";

    public static void run(Context context, Bundle arguments) throws Exception {
        Class<?> settings = context.getClassLoader().loadClass("app.morphe.extension.shared.settings.PatchSettings");
        SharedPreferences preferences = (SharedPreferences) settings.getMethod("preferences", Context.class)
                .invoke(null, context);
        boolean hadSetting = preferences.contains(KEY);
        boolean previous = preferences.getBoolean(KEY, true);
        try {
            if (context.getPackageName().startsWith("com.onelouder.baconreader")
                    || "baconreader".equals(arguments.getString("stock"))) {
                setEnabled(preferences, true);
                baconReader(context, LINK, true);
                baconReader(context, WRONG_HOST, false);
                setEnabled(preferences, false);
                baconReader(context, LINK, false);
                setEnabled(preferences, true);
                baconReader(context, LINK, true);
            } else {
                sync(context, preferences);
            }
        } finally {
            SharedPreferences.Editor restore = preferences.edit();
            check((hadSetting ? restore.putBoolean(KEY, previous) : restore.remove(KEY)).commit(),
                    "Could not restore the Morphe switch");
        }
    }

    private static void baconReader(Context context, String link, boolean shouldPlay) throws Exception {
        Class<?> router = context.getClassLoader().loadClass("com.onelouder.baconreader.LinkProcessor");
        Intent intent = onMain(() -> (Intent) router.getMethod("getIntent")
                .invoke(router.getConstructor(Context.class, String.class).newInstance(context, link)));
        check(BACON_BROWSER.equals(component(intent)), "BaconReader did not use its internal browser activity");
        Activity browser = launch(intent);
        try {
            onMain(() -> {
                Object handler = field(browser, "mediaHandler").get(browser);
                String resolved = (String) field(handler, "resolvedUrl").get(handler);
                check(shouldPlay == (BASE + "/DASHPlaylist.mpd").equals(resolved),
                        "Wrong BaconReader comment-video resolution: " + resolved);
                if (shouldPlay) {
                    Object player = field(handler, "videoHandler").get(handler);
                    Object source = field(player, "mediaSource").get(player);
                    check(source != null && source.getClass().getName().endsWith(".DashMediaSource"),
                            "BaconReader did not hand the manifest to its native DASH player");
                }
                return null;
            });
        } finally {
            finish(browser);
        }
    }

    private static void sync(Context context, SharedPreferences preferences) throws Exception {
        Class<?> singleton = context.getClassLoader().loadClass("com.laurencedawson.reddit_sync.singleton.SettingsSingleton");
        Object settings = onMain(() -> singleton.getMethod("j").invoke(singleton.getMethod("d").invoke(null)));
        Field redditVideo = field(settings, "redditVideo");
        Field imageHandling = field(settings, "imageHandling");
        boolean previousVideo = redditVideo.getBoolean(settings);
        boolean previousImage = imageHandling.getBoolean(settings);
        try {
            onMain(() -> {
                redditVideo.setBoolean(settings, true);
                imageHandling.setBoolean(settings, true);
                return null;
            });
            setEnabled(preferences, true);
            verifySyncPlayer(syncRoute(context, LINK), LINK, BASE + "/HLSPlaylist.m3u8");
            check(!SYNC_VIEWER.equals(component(syncRoute(context, WRONG_HOST))), "Sync opened a lookalike URL as video");
            setEnabled(preferences, false);
            check(!SYNC_VIEWER.equals(component(syncRoute(context, LINK))), "Sync ignored the Morphe switch");
            verifySyncPlayer(syncRoute(context, ORDINARY_VIDEO), ORDINARY_VIDEO, ORDINARY_VIDEO + "/HLSPlaylist.m3u8");
            setEnabled(preferences, true);
            verifySyncPlayer(syncRoute(context, ORDINARY_VIDEO), ORDINARY_VIDEO, ORDINARY_VIDEO + "/HLSPlaylist.m3u8");
            onMain(() -> {
                redditVideo.setBoolean(settings, false);
                return null;
            });
            check(!SYNC_VIEWER.equals(component(syncRoute(context, LINK))), "Sync ignored its external video preference");
        } finally {
            onMain(() -> {
                redditVideo.setBoolean(settings, previousVideo);
                imageHandling.setBoolean(settings, previousImage);
                return null;
            });
        }
    }

    private static Intent syncRoute(Context context, String link) throws Exception {
        var route = context.getClassLoader().loadClass("y7.b").getMethod("b", Context.class, String.class);
        Intent[] result = new Intent[1];
        Context capture = new ContextWrapper(context) {
            @Override public void startActivity(Intent intent) { result[0] = intent; }
            @Override public void startActivity(Intent intent, Bundle options) { result[0] = intent; }
        };
        return onMain(() -> {
            check((Boolean) route.invoke(null, capture, link), "Sync did not handle " + link);
            return result[0];
        });
    }

    private static void verifySyncPlayer(Intent intent, String original, String expected) throws Exception {
        check(SYNC_VIEWER.equals(component(intent)), "Sync did not open its native media viewer");
        check(original.equals(intent.getStringExtra("url_content")), "Sync changed the original viewer URL");
        Activity viewer = launch(intent);
        try {
            String source = null;
            for (int attempt = 0; attempt < 30 && source == null; attempt++) {
                source = onMain(() -> {
                    View player = findSyncPlayer(viewer.getWindow().getDecorView());
                    return player == null ? null : (String) player.getClass().getMethod("getSource").invoke(player);
                });
                if (source == null) SystemClock.sleep(100);
            }
            check(expected.equals(source), "Wrong Sync native player source: " + source);
        } finally {
            finish(viewer);
        }
    }

    private static View findSyncPlayer(View view) {
        if (SYNC_PLAYER.equals(view.getClass().getName())) return view;
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View result = findSyncPlayer(group.getChildAt(i));
                if (result != null) return result;
            }
        }
        return null;
    }

    private static Field field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static String component(Intent intent) {
        return intent == null || intent.getComponent() == null ? null : intent.getComponent().getClassName();
    }

    private static void setEnabled(SharedPreferences preferences, boolean enabled) {
        check(preferences.edit().putBoolean(KEY, enabled).commit(), "Could not set the Morphe switch");
    }

    private static Activity launch(Intent intent) {
        return PatchedditInstrumentation.instance.startActivitySync(
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
    }

    private static void finish(Activity activity) {
        PatchedditInstrumentation.instance.runOnMainSync(activity::finish);
        PatchedditInstrumentation.instance.waitForIdleSync();
    }

    private static <T> T onMain(Callable<T> body) throws Exception {
        FutureTask<T> task = new FutureTask<>(body);
        PatchedditInstrumentation.instance.runOnMainSync(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
