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
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public final class JoeyAdsDeviceTest {
    private static final String KEY = "morphe_patch_disable_ads";

    public static void run(Context context, Bundle arguments) throws Exception {
        check(context.getPackageName().startsWith("app.patcheddit.rollout."), "Use a disposable Joey installation");
        ClassLoader loader = context.getClassLoader();
        Class<?> base = loader.loadClass("o.o.joey.Activities.BaseActivity");
        Class<?> home = loader.loadClass("o.o.joey.Activities.HomeActivity");
        Class<?> billing = loader.loadClass("o.o.joey.Billing.c");
        Class<?> state = loader.loadClass("lb.n");
        Object statePreferences = method(state, "f").invoke(null);
        SharedPreferences settings = android.preference.PreferenceManager.getDefaultSharedPreferences(context);
        settings.edit().remove(KEY).commit();

        var instrumentation = PatchedditInstrumentation.instance;
        var monitor = instrumentation.addMonitor(home.getName(), null, false);
        Activity activity;
        try {
            context.startActivity(context.getPackageManager().getLaunchIntentForPackage(context.getPackageName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            activity = monitor.waitForActivityWithTimeout(15_000);
            check(activity != null, "Joey did not launch");
        } finally {
            instrumentation.removeMonitor(monitor);
        }

        // A fresh install has other native ad exemptions; isolate the billing result.
        Class<?> configType = loader.loadClass("yd.w0");
        Object config = field(configType, "b").get(method(configType, "D0").invoke(null));
        Object configured = method(config.getClass(), "w", Map.class).invoke(config, Map.of(
                "dtdafni3", "-1", "NODOUFATS", "-1", "AASOTLIU", "false"));
        Class<?> task = loader.loadClass("com.google.android.gms.tasks.Task");
        method(loader.loadClass("com.google.android.gms.tasks.Tasks"), "await", task, long.class, TimeUnit.class)
                .invoke(null, configured, 10L, TimeUnit.SECONDS);

        var verify = new FutureTask<Void>(() -> {
            Object adStatus = method(billing, "z").invoke(method(billing, "m").invoke(null));
            method(adStatus.getClass(), "o", Object.class).invoke(adStatus, false);
            method(state, "U", int.class).invoke(statePreferences, 9);
            SharedPreferences nativePrefs = (SharedPreferences) field(state, "a").get(statePreferences);
            check(nativePrefs.edit().putInt("NODOFU", 0).commit(), "Could not seed the native usage count");
            field(base, "S").setBoolean(activity, false);
            field(base, "y0").setBoolean(activity, false);
            field(base, "E0").setBoolean(activity, false);
            field(base, "D0").setBoolean(activity, false);
            field(home, "n1").setBoolean(activity, false);
            Method disabled = method(base, "b1");
            Method buildBanner = method(base, "U1");
            Field inlineAds = field(loader.loadClass("ya.a"), "h0");
            boolean savedInlineAds = inlineAds.getBoolean(null);
            int containerId = context.getResources().getIdentifier("ad_frameLayout", "id", "o.o.joey");
            check(containerId != 0, "Missing native ad container ID");
            FrameLayout banner = activity.findViewById(containerId);
            check(banner != null, "Missing native ad container");
            try {
                check((Boolean) method(state, "A").invoke(statePreferences), "Existing ad-free gate was not applied");
                for (boolean inline : new boolean[]{false, true}) {
                    inlineAds.setBoolean(null, inline);
                    check((Boolean) disabled.invoke(activity), "Expired ad grace reopened the banner gate");
                    buildBanner.invoke(activity);
                    banner.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.UNSPECIFIED);
                    check(banner.getChildCount() == 0 && banner.getMeasuredHeight() == 0,
                            "Disabled banner still reserves space (inline ads: " + inline + ")");
                }
                settings.edit().putBoolean(KEY, false).commit();
                check(!(Boolean) disabled.invoke(activity), "Turning the patch off did not restore the native gate");
                settings.edit().putBoolean(KEY, true).commit();
                check((Boolean) disabled.invoke(activity), "Turning the patch back on did not disable the banner");
                check(Boolean.FALSE.equals(method(loader.loadClass("androidx.lifecycle.LiveData"), "e").invoke(adStatus)),
                        "Disabling ads changed the native billing state");
            } finally {
                inlineAds.setBoolean(null, savedInlineAds);
                settings.edit().remove(KEY).commit();
                activity.finish();
            }
            return null;
        });
        instrumentation.runOnMainSync(verify);
        verify.get(10, TimeUnit.SECONDS);
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameters) throws Exception {
        Method method = owner.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
