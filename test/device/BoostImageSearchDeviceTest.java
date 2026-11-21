/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.patcheddit.fixture;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

public final class BoostImageSearchDeviceTest {
    private static final String KEY = "morphe_patch_fix_image_search";
    private static final String IMAGE = "https://preview.redd.it/photo.jpeg?format=pjpg&s=a%2Fb%3D&width=1200#part";
    private static ClassLoader loader;
    private static Activity activity;
    private static Object menu;
    private static final List<Intent> launches = new ArrayList<>();

    public static void run(Context context, Bundle arguments) throws Exception {
        loader = context.getClassLoader();
        SharedPreferences prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(context);
        String[] keys = {KEY, "pref_saved_version", "pref_dev_reddit_client_id",
                "pref_dev_reddit_redirect_url", "pref_dev_reddit_user_agent"};
        SharedPreferences backup = context.getSharedPreferences("patcheddit_image_search_fixture", 0);
        if (backup.contains("pending")) restore(prefs, keys, backup.getAll());
        Map<String, ?> original = new HashMap<>(prefs.getAll());
        restore(backup, keys, original);
        check(backup.edit().putBoolean("pending", true).commit(), "Could not preserve fixture settings");
        SharedPreferences.Editor setup = prefs.edit().remove(KEY)
                .putInt(keys[1], context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode);
        String[] defaults = {"fixture", "http://localhost", "android:fixture:1"};
        for (int i = 2; i < keys.length; i++) if (prefs.getString(keys[i], "").isEmpty()) setup.putString(keys[i], defaults[i - 2]);
        check(setup.commit(), "Could not configure native image-search fixture");
        var instrumentation = PatchedditInstrumentation.instance;
        var launch = instrumentation.addMonitor("com.rubenmayayo.reddit.ui.submissions.subreddit.MainActivity", null, false);
        var capture = new Instrumentation.ActivityMonitor() {
            @Override public Instrumentation.ActivityResult onStartActivity(Intent intent) {
                if (!Intent.ACTION_VIEW.equals(intent.getAction())) return null;
                launches.add(new Intent(intent));
                return new Instrumentation.ActivityResult(Activity.RESULT_OK, null);
            }
        };
        instrumentation.addMonitor(capture);
        try {
            context.startActivity(context.getPackageManager().getLaunchIntentForPackage(context.getPackageName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            activity = launch.waitForActivityWithTimeout(15000);
            check(activity != null, "Native Boost host did not start");
            onMain(() -> {
                type("v32").getMethod("a", Context.class, String.class).invoke(null, activity, IMAGE);
                View nativeView = menuView();
                check(nativeView != null, "Native link menu did not open");
                Object callback = field(nativeView, "c");
                menu = field(callback, "a");
                dismiss();
                Object model = type("com.rubenmayayo.reddit.models.reddit.SubmissionModel").getConstructor().newInstance();
                for (Class<?> owner = model.getClass(); owner != null; owner = owner.getSuperclass()) {
                    for (Field field : owner.getDeclaredFields()) {
                        if (!Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                            field.setAccessible(true);
                            field.set(model, IMAGE);
                        }
                    }
                }
                set(model, "F0", 1);
                set(menu, "d", model);
                return null;
            });
            String[] names = {"Yandex", "Google", "TinEye"};
            for (int index = 0; index < names.length; index++) {
                int selected = index;
                onMain(() -> {
                    type("v32").getMethod("b", String.class).invoke(menu, IMAGE);
                    View nativeView = menuView();
                    List<?> options = (List<?>) field(nativeView, "b");
                    List<?> engines = null;
                    java.util.Set<Integer> ids = new java.util.HashSet<>();
                    for (Object option : options) {
                        int id = (Integer) field(option, "a");
                        ids.add(id);
                        if (id == 5) {
                            check((Integer) field(option, "x") != 0, "Localized submenu title was lost");
                            engines = children(option);
                        }
                    }
                    check(ids.containsAll(List.of(0, 1, 2, 3)), "Search override removed native link actions");
                    check(engines != null && engines.size() == 3, "Original search submenu was not replaced");
                    Object engine = engines.get(selected);
                    check(names[selected].equals(field(engine, "y")), "Engine order/title changed");
                    check((Integer) field(engine, "a") == 201 + selected, "Wrong engine ID");
                    Object callback = field(nativeView, "c");
                    callback.getClass().getMethod("a", engine.getClass()).invoke(callback, engine);
                    check(!((android.app.Dialog) field(menu, "b")).isShowing(), "Search selection did not dismiss the native menu");
                    return null;
                });
                Intent intent = launches.get(index);
                String target = intent.getDataString();
                check(IMAGE.equals(URLDecoder.decode(target.substring(target.indexOf("url=") + 4), "UTF-8")),
                        "Image URL was truncated or incorrectly encoded");
                if (index == 1) check(target.startsWith("https://lens.google.com/uploadbyurl?url="), "Google did not use Lens");
            }
            onMain(() -> {
                check(prefs.edit().putBoolean(KEY, false).commit(), "Could not disable image-search override");
                type("v32").getMethod("b", String.class).invoke(menu, IMAGE);
                List<?> options = (List<?>) field(menuView(), "b");
                for (Object option : options) if ((Integer) field(option, "a") == 5) {
                    check((Integer) field(children(option).get(1), "a") == 52,
                            "Disabled patch did not preserve the native submenu");
                }
                return null;
            });
        } finally {
            instrumentation.removeMonitor(launch);
            instrumentation.removeMonitor(capture);
            if (activity != null) onMain(() -> { dismiss(); activity.finish(); return null; });
            restore(prefs, keys, original);
            check(backup.edit().clear().commit(), "Could not clear fixture backup");
        }
    }

    private static View menuView() throws Exception {
        Class<?> manager = Class.forName("android.view.WindowManagerGlobal");
        Object global = manager.getMethod("getInstance").invoke(null);
        List<?> roots = (List<?>) field(global, "mViews");
        for (int index = roots.size() - 1; index >= 0; index--) {
            View result = find((View) roots.get(index));
            if (result != null) return result;
        }
        return null;
    }
    private static List<?> children(Object option) throws Exception {
        for (Field field : option.getClass().getDeclaredFields()) if (field.getType() == ArrayList.class) {
            field.setAccessible(true);
            return (List<?>) field.get(option);
        }
        throw new NoSuchFieldException("Native submenu children");
    }
    private static View find(View view) {
        if (view.getClass().getName().equals("com.rubenmayayo.reddit.ui.customviews.menu.MenuView")) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View match = find(((ViewGroup) view).getChildAt(i));
            if (match != null) return match;
        }
        return null;
    }
    private static void dismiss() throws Exception {
        if (menu != null && field(menu, "b") != null) ((android.app.Dialog) field(menu, "b")).dismiss();
    }
    private static Class<?> type(String name) throws Exception { return loader.loadClass(name); }
    private static Object field(Object object, String name) throws Exception {
        for (Class<?> owner = object.getClass(); owner != null; owner = owner.getSuperclass()) {
            try { Field field = owner.getDeclaredField(name); field.setAccessible(true); return field.get(object); }
            catch (NoSuchFieldException next) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static void set(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }
    private static void restore(SharedPreferences prefs, String[] keys, Map<String, ?> before) {
        SharedPreferences.Editor edit = prefs.edit();
        for (String key : keys) {
            Object value = before.get(key);
            if (value == null) edit.remove(key);
            else if (value instanceof Integer) edit.putInt(key, (Integer) value);
            else if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
            else edit.putString(key, (String) value);
        }
        check(edit.commit(), "Could not restore fixture settings");
    }
    private static <T> T onMain(Callable<T> action) throws Exception {
        FutureTask<T> future = new FutureTask<>(action);
        PatchedditInstrumentation.instance.runOnMainSync(future);
        return future.get();
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
