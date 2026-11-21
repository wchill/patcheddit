/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.patcheddit.fixture;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class RifPlatinumAdsDeviceTest {
    private static final String BLOCK_ADS = "morphe_patch_block_ads";
    private static final String FAKE_PREMIUM = "morphe_patch_fake_reddit_premium";

    public static void run(Context context, Bundle arguments) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            exercise(context, arguments);
            return;
        }
        CountDownLatch finished = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];
        check(new Handler(Looper.getMainLooper()).post(() -> {
            try {
                exercise(context, arguments);
            } catch (Throwable error) {
                failure[0] = error;
            } finally {
                finished.countDown();
            }
        }), "Could not schedule native feed fixture");
        check(finished.await(30, TimeUnit.SECONDS), "Native feed fixture timed out");
        if (failure[0] != null) throw new AssertionError("Native feed fixture failed", failure[0]);
    }

    private static void exercise(Context context, Bundle arguments) throws Exception {
        ClassLoader loader = context.getClassLoader();
        boolean free = optionalClass(loader, "com.andrewshu.android.reddit.ads.AdViewHelper") != null;
        String sku = free ? "free" : "platinum";
        String expected = arguments == null ? null : arguments.getString("expected-sku");
        check(expected == null || expected.equals(sku), "Expected " + expected + " native SKU, found " + sku);
        Class<?> feedType = Class.forName(free ? "e5.e0" : "f5.e0", true, loader);
        check(optionalClass(loader, free ? "f5.e0" : "e5.e0") == null, "Ambiguous native feed classes");
        Method gate = feedType.getDeclaredMethod("d0");
        check(gate.getReturnType() == boolean.class && Modifier.isProtected(gate.getModifiers())
                && !Modifier.isStatic(gate.getModifiers()), "Unexpected native ad-slot dispatch");
        gate.setAccessible(true);

        // Stock constructors only initialize loader state and weak references; no load is started.
        Constructor<?> constructor = feedType.getDeclaredConstructor(Activity.class, Uri.class);
        constructor.setAccessible(true);
        Object feed = constructor.newInstance(new FixtureActivity(context),
                Uri.parse("https://www.reddit.com/r/patcheddit_fixture/.json"));
        Class<?> nativePreferences = Class.forName(free ? "k4.v" : "l4.v", true, loader);
        Method singleton = nativePreferences.getDeclaredMethod("C");
        singleton.setAccessible(true);
        Object preferences = singleton.invoke(null);
        check(preferences != null, "Native application preferences were not initialized");
        Field ads = nativePreferences.getDeclaredField("g");
        Field premium = nativePreferences.getDeclaredField("h");
        ads.setAccessible(true);
        premium.setAccessible(true);
        boolean previousAds = ads.getBoolean(preferences);
        boolean previousPremium = premium.getBoolean(preferences);
        Class<?> patchSettings = Class.forName("app.morphe.extension.shared.settings.PatchSettings", true, loader);
        SharedPreferences switches = (SharedPreferences) patchSettings.getMethod("preferences", Context.class)
                .invoke(null, context);
        boolean blockingIncluded = optionalMethod(patchSettings, "isEnabled_" + BLOCK_ADS) != null;
        try (SavedSwitches ignored = new SavedSwitches(switches)) {
            try {
                if (free) {
                    // A non-themed fixture and premium removal off make native eligibility deterministic,
                    // independent of the installed account, theme, or renameable package.
                    premium.setBoolean(preferences, false);
                    for (boolean fakePremium : new boolean[]{false, true}) {
                        setSwitches(switches, false, fakePremium);
                        ads.setBoolean(preferences, false);
                        check(!eligible(gate, feed), "Free gate ignored disabled native ads");
                        ads.setBoolean(preferences, true);
                        check(eligible(gate, feed), "Paid safeguard incorrectly suppressed genuine free ads");
                        if (blockingIncluded) {
                            setSwitches(switches, true, fakePremium);
                            check(!eligible(gate, feed), "Separate Block ads switch did not suppress free slots");
                        }
                    }
                } else {
                    for (boolean blocked : new boolean[]{false, true}) {
                        for (boolean fakePremium : new boolean[]{false, true}) {
                            setSwitches(switches, blocked, fakePremium);
                            for (boolean nativeAds : new boolean[]{false, true}) {
                                for (boolean nativePremium : new boolean[]{false, true}) {
                                    ads.setBoolean(preferences, nativeAds);
                                    premium.setBoolean(preferences, nativePremium);
                                    check(!eligible(gate, feed), "Platinum allowed an impossible native-ad slot: "
                                            + "ads=" + nativeAds + ", premium=" + nativePremium
                                            + ", blocked=" + blocked + ", fakePremium=" + fakePremium);
                                }
                            }
                        }
                        if (!free || blockingIncluded) {
                            setSwitches(switches, true, false);
                            ads.setBoolean(preferences, true);
                            premium.setBoolean(preferences, false);
                            verifyFeedPages(loader, feed);
                        }
                    }
                }
            } finally {
                ads.setBoolean(preferences, previousAds);
                premium.setBoolean(preferences, previousPremium);
            }
        }
    }

    private static boolean eligible(Method gate, Object feed) throws Exception {
        return (Boolean) gate.invoke(feed);
    }

    private static void verifyFeedPages(ClassLoader loader, Object feed) throws Exception {
        Method parse = feed.getClass().getSuperclass().getDeclaredMethod("b0", InputStream.class);
        parse.setAccessible(true);
        Class<?> loaderBase = feed.getClass().getMethod("m").getDeclaringClass();
        Field reset = loaderBase.getDeclaredField("f");
        reset.setAccessible(true);
        reset.setBoolean(feed, false);
        Method name = Class.forName("com.andrewshu.android.reddit.things.objects.Thing", true, loader)
                .getMethod("getName");
        for (int page = 0; page < 2; page++) {
            org.json.JSONArray children = new org.json.JSONArray();
            for (int index = 0; index < 20; index++) {
                String id = "fixture" + page + "x" + index;
                children.put(new org.json.JSONObject().put("kind", "t3").put("data",
                        new org.json.JSONObject().put("id", id).put("name", "t3_" + id)
                                .put("title", "Fixture " + index).put("subreddit", "patcheddit_fixture")
                                .put("author", "fixture").put("is_self", true).put("selftext", "Fixture")
                                .put("url", "https://www.reddit.com/r/patcheddit_fixture/comments/" + id)));
            }
            String json = new org.json.JSONObject().put("kind", "Listing").put("data",
                    new org.json.JSONObject().put("children", children).put("after", "t3_next" + page)).toString();
            List<?> items = (List<?>) parse.invoke(feed,
                    new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
            check(items != null, "Active feed parser unexpectedly returned no result");
            check(items.size() == 20, "Feed parser added an ad gap or lost normal posts");
            for (int index = 0; index < items.size(); index++) {
                check(("t3_fixture" + page + "x" + index).equals(name.invoke(items.get(index))),
                        "Feed parser changed normal post order or introduced an ad row");
            }
        }
    }

    private static void setSwitches(SharedPreferences preferences, boolean blocked, boolean premium) {
        check(preferences.edit().putBoolean(BLOCK_ADS, blocked).putBoolean(FAKE_PREMIUM, premium).commit(),
                "Could not save fixture switches");
    }

    private static Class<?> optionalClass(ClassLoader loader, String name) throws Exception {
        try {
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException absent) {
            return null;
        }
    }

    private static Method optionalMethod(Class<?> type, String name) {
        try {
            return type.getDeclaredMethod(name);
        } catch (NoSuchMethodException absent) {
            return null;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class FixtureActivity extends Activity {
        FixtureActivity(Context context) {
            attachBaseContext(context);
        }
    }

    private static final class SavedSwitches implements AutoCloseable {
        private final SharedPreferences preferences;
        private final Boolean blocked;
        private final Boolean premium;

        SavedSwitches(SharedPreferences preferences) {
            this.preferences = preferences;
            blocked = (Boolean) preferences.getAll().get(BLOCK_ADS);
            premium = (Boolean) preferences.getAll().get(FAKE_PREMIUM);
        }

        @Override
        public void close() {
            SharedPreferences.Editor editor = preferences.edit();
            if (blocked == null) editor.remove(BLOCK_ADS); else editor.putBoolean(BLOCK_ADS, blocked);
            if (premium == null) editor.remove(FAKE_PREMIUM); else editor.putBoolean(FAKE_PREMIUM, premium);
            check(editor.commit(), "Could not restore original fixture switches");
        }
    }
}
