/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.patcheddit.fixture;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public final class BoostNavigationInsetsDeviceTest {
    private static final String MAIN =
            "com.rubenmayayo.reddit.ui.submissions.subreddit.MainActivity";
    private static final String SUBREDDIT =
            "com.rubenmayayo.reddit.ui.submissions.subreddit.SubredditActivity";
    private static final String SUBREDDIT_SEARCH =
            "com.rubenmayayo.reddit.ui.subreddits.SearchSubredditActivity";
    private static final String NAVIGATION =
            "app.morphe.extension.boost.utils.BoostSearchBottomNavigation";
    private static final String UNDERLAY =
            "morphe_boost_bottom_navigation_decor_underlay";
    private static final String FALLBACK =
            "MORPHE_BOOST_MAIN_NAV_BAR_SURFACE_OVERLAY_V4";
    private static final String STATUS_SURFACE =
            "MORPHE_BOOST_COMMENTS_SYSTEM_BAR_SURFACE_V1";

    public static void run(Context context, Bundle arguments) throws Exception {
        SharedPreferences preferences =
                android.preference.PreferenceManager.getDefaultSharedPreferences(context);
        Map<String, ?> previous = preferences.getAll();
        String[] keys = {
                "pref_dev_reddit_client_id", "pref_dev_reddit_redirect_url",
                "pref_dev_reddit_user_agent", "pref_saved_version",
                "pref_bottom_navigation", "pref_bottom_navigation_hide_on_scroll",
                "pref_show_subreddit_header"
        };
        SharedPreferences.Editor setup = preferences.edit()
                .putBoolean("pref_bottom_navigation", true)
                .putBoolean("pref_bottom_navigation_hide_on_scroll", false)
                .putInt("pref_saved_version",
                        context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode);
        if (!preferences.contains(keys[0])) setup.putString(keys[0], "fixture");
        if (!preferences.contains(keys[1])) setup.putString(keys[1], "http://localhost");
        if (!preferences.contains(keys[2])) setup.putString(keys[2], "android:fixture:1");
        check(setup.commit(), "Could not configure native navigation");

        var instrumentation = PatchedditInstrumentation.instance;
        var monitor = instrumentation.addMonitor(MAIN, null, false);
        Activity activity = null;
        try {
            context.startActivity(new Intent().setClassName(context, MAIN)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            activity = monitor.waitForActivityWithTimeout(15000);
            check(activity != null, "Boost Home did not start");
            Activity home = activity;
            View navigation = null;
            for (int attempt = 0; attempt < 100; attempt++) {
                navigation = onMain(() -> {
                    View view = home.findViewById(context.getResources().getIdentifier(
                            "bottom_navigation_view", "id", context.getPackageName()));
                    return view != null && view.isShown() && view.getHeight() > 0
                            && home.getWindow().getDecorView().findViewWithTag(UNDERLAY) != null
                            && home.getWindow().getDecorView().findViewWithTag(FALLBACK) != null
                            ? view : null;
                });
                if (navigation != null) break;
                SystemClock.sleep(100);
            }
            check(navigation != null, "Patched navigation and system-bar surfaces were not laid out");
            View bar = navigation;
            onMain(() -> {
                View decor = home.getWindow().getDecorView();
                for (String tag : new String[]{FALLBACK, UNDERLAY}) {
                    View surface = decor.findViewWithTag(tag);
                    staleSurfaceCannotCoverControls(home, bar, surface);
                    surfaceFollowsCurrentInsets(surface);
                }

                Class<?> hook = context.getClassLoader().loadClass(NAVIGATION);
                hook.getMethod("enforceMaterialOnlyVisibility", Activity.class, boolean.class)
                        .invoke(null, home, false);
                View underlay = decor.findViewWithTag(UNDERLAY);
                underlay.dispatchApplyWindowInsets(insets(96, 0));
                check(underlay.getVisibility() == View.GONE,
                        "A later inset dispatch restored the disabled navigation underlay");
                check(bar.getVisibility() == View.GONE, "Disabling navigation did not hide the native bar");
                hook.getMethod("enforceMaterialOnlyVisibility", Activity.class, boolean.class)
                        .invoke(null, home, true);
                check(bar.getVisibility() == View.VISIBLE, "Navigation could not be re-enabled");
                return null;
            });

            if (android.os.Build.VERSION.SDK_INT >= 35) {
                for (boolean header : new boolean[]{false, true}) {
                    check(preferences.edit().putBoolean("pref_show_subreddit_header", header).commit(),
                            "Could not set the subreddit header preference");
                    testSubredditStatusBar(context, SUBREDDIT);
                }
                testSubredditStatusBar(context, SUBREDDIT_SEARCH);
            }
        } finally {
            instrumentation.removeMonitor(monitor);
            if (activity != null) {
                Activity home = activity;
                onMain(() -> { home.finish(); return null; });
            }
            SharedPreferences.Editor restore = preferences.edit();
            for (String key : keys) {
                Object value = previous.get(key);
                if (value == null) restore.remove(key);
                else if (value instanceof Boolean) restore.putBoolean(key, (Boolean) value);
                else if (value instanceof Integer) restore.putInt(key, (Integer) value);
                else restore.putString(key, (String) value);
            }
            check(restore.commit(), "Could not restore native preferences");
        }
    }

    private static void testSubredditStatusBar(Context context, String name) throws Exception {
        var instrumentation = PatchedditInstrumentation.instance;
        var monitor = instrumentation.addMonitor(name, null, false);
        Activity activity;
        try {
            Intent intent = new Intent().setClassName(context, name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (SUBREDDIT.equals(name)) {
                intent.setAction(Intent.ACTION_VIEW).setData(Uri.parse("https://www.reddit.com/r/patcheddit/"));
            }
            context.startActivity(intent);
            activity = monitor.waitForActivityWithTimeout(15_000);
            check(activity != null, name + " did not start");
        } finally {
            instrumentation.removeMonitor(monitor);
        }

        try {
            ScrollView scroll = onMain(() -> {
                FrameLayout content = activity.findViewById(android.R.id.content);
                ScrollView list = new ScrollView(activity);
                LinearLayout rows = new LinearLayout(activity);
                rows.setOrientation(LinearLayout.VERTICAL);
                for (int i = 0; i < 3; i++) {
                    View row = new View(activity);
                    row.setBackgroundColor(i % 2 == 0 ? Color.MAGENTA : Color.GREEN);
                    rows.addView(row, new LinearLayout.LayoutParams(-1,
                            activity.getResources().getDisplayMetrics().heightPixels));
                }
                list.addView(rows);
                content.addView(list, new FrameLayout.LayoutParams(-1, -1));
                return list;
            });
            for (int attempt = 0; attempt < 100 && !onMain(() -> scroll.getHeight() > 0); attempt++) {
                SystemClock.sleep(50);
            }
            onMain(() -> {
                View decor = activity.getWindow().getDecorView();
                int top = decor.getRootWindowInsets().getInsets(WindowInsets.Type.statusBars()).top;
                check(top > 0 && scroll.getHeight() > top, "No status inset or scrollable content on " + name);
                int[] position = new int[2], origin = new int[2];
                scroll.getLocationInWindow(position);
                decor.getLocationInWindow(origin);
                scroll.setTranslationY(origin[1] - position[1]);

                int attribute = activity.getResources().getIdentifier("ToolbarColor", "attr", activity.getPackageName());
                TypedValue value = new TypedValue();
                check(activity.getTheme().resolveAttribute(attribute, value, true), "No native toolbar color");
                int expected = value.data | 0xff000000;
                for (int y : new int[]{0, scroll.getChildAt(0).getHeight() / 3}) {
                    scroll.scrollTo(0, y);
                    check(scroll.getScrollY() == y, "Synthetic rows did not scroll");
                    Bitmap bitmap = Bitmap.createBitmap(decor.getWidth(), top + 1, Bitmap.Config.ARGB_8888);
                    try {
                        decor.draw(new Canvas(bitmap));
                        int x = bitmap.getWidth() / 2;
                        check(bitmap.getPixel(x, top) == (y == 0 ? Color.MAGENTA : Color.GREEN),
                                "Test rows did not draw immediately below the status bar on " + name);
                        int actual = bitmap.getPixel(x, top / 2);
                        check(actual == expected, "Scrolled content shows through the status bar on " + name
                                + " (expected " + Integer.toHexString(expected)
                                + ", got " + Integer.toHexString(actual) + ")");
                    } finally {
                        bitmap.recycle();
                    }
                }
                View surface = decor.findViewWithTag(STATUS_SURFACE);
                check(surface != null, "No status bar surface on " + name);
                for (int height : new int[]{top / 2, 0, top}) {
                    WindowInsets insets = new WindowInsets.Builder()
                            .setInsets(WindowInsets.Type.statusBars(), Insets.of(0, height, 0, 0))
                            .build();
                    check(surface.dispatchApplyWindowInsets(insets) == insets, "Status surface consumed insets");
                    check(surface.getLayoutParams().height == height, "Status surface retained a stale inset");
                }
                return null;
            });
        } finally {
            onMain(() -> { activity.finish(); return null; });
        }
    }

    private static void staleSurfaceCannotCoverControls(Activity activity, View bar, View surface) {
        int visibility = surface.getVisibility();
        int height = surface.getLayoutParams().height;
        Drawable background = surface.getBackground();
        int left = surface.getLeft(), top = surface.getTop();
        int right = surface.getRight(), bottom = surface.getBottom();
        Bitmap expected = null, actual = null;
        try {
            surface.setVisibility(View.GONE);
            expected = navigationPixels(activity, bar);
            surface.setBackground(new ColorDrawable(Color.MAGENTA));
            surface.setVisibility(View.VISIBLE);
            View decor = activity.getWindow().getDecorView();
            int staleHeight = bar.getHeight() + 96;
            surface.getLayoutParams().height = staleHeight;
            surface.measure(View.MeasureSpec.makeMeasureSpec(decor.getWidth(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(staleHeight, View.MeasureSpec.EXACTLY));
            surface.layout(0, decor.getHeight() - staleHeight, decor.getWidth(), decor.getHeight());
            actual = navigationPixels(activity, bar);
            check(expected.sameAs(actual),
                    "A stale system-bar surface painted over native navigation: " + surface.getTag());
        } finally {
            if (expected != null) expected.recycle();
            if (actual != null) actual.recycle();
            surface.setBackground(background);
            surface.getLayoutParams().height = height;
            surface.layout(left, top, right, bottom);
            surface.setVisibility(visibility);
        }
    }

    private static Bitmap navigationPixels(Activity activity, View bar) {
        View decor = activity.getWindow().getDecorView();
        Bitmap window = Bitmap.createBitmap(decor.getWidth(), decor.getHeight(), Bitmap.Config.ARGB_8888);
        try {
            decor.draw(new Canvas(window));
            int[] origin = new int[2], position = new int[2];
            decor.getLocationOnScreen(origin);
            bar.getLocationOnScreen(position);
            return Bitmap.createBitmap(window, position[0] - origin[0], position[1] - origin[1],
                    bar.getWidth(), bar.getHeight() - bar.getPaddingBottom());
        } finally {
            window.recycle();
        }
    }

    private static void surfaceFollowsCurrentInsets(View surface) {
        surface.setEnabled(true);
        for (int bottom : new int[]{96, 32, 0, 24, 0, 96}) {
            WindowInsets insets = insets(bottom, bottom == 0 ? 96 : 0);
            check(surface.dispatchApplyWindowInsets(insets) == insets,
                    "Surface consumed insets intended for native content");
            check(surface.getLayoutParams().height == bottom,
                    "Surface retained an obsolete bottom inset: " + surface.getTag());
            check(surface.getVisibility() == (bottom == 0 ? View.GONE : View.VISIBLE),
                    "Surface visibility did not follow the current bottom inset");
        }
        surface.setEnabled(false);
        surface.setVisibility(View.GONE);
        surface.dispatchApplyWindowInsets(insets(32, 0));
        check(surface.getVisibility() == View.GONE, "Insets restored a suppressed fallback surface");
    }

    private static WindowInsets insets(int bottom, int right) {
        return new WindowInsets.Builder()
                .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, right, bottom))
                .build();
    }

    private static <T> T onMain(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        PatchedditInstrumentation.instance.runOnMainSync(task);
        return task.get(30, TimeUnit.SECONDS);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
