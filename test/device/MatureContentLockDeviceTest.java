/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.patcheddit.fixture;

import android.app.AlertDialog;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Only run on a disposable Boost installation: this enables the one-way lock. */
public final class MatureContentLockDeviceTest {
    private static final String KEY = "pref_patcheddit_mature_content_lock";
    private static final String ACTIVITY = "app.morphe.extension.shared.settings.MorpheSettingsActivity";
    private static final String OPTION = "morphe_enable_mature_content_lock";

    public static void run(Context context, Bundle arguments) throws Exception {
        if ("sidebar".equals(arguments.getString("phase"))) {
            verifyCachedSidebar(context);
            return;
        }
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean activate = "activate".equals(arguments.getString("phase"));
        if (activate) {
            check(!preferences.getBoolean(KEY, false), "NSFW lock was enabled by default");
            check(preferences.edit().putBoolean("pref_nsfw", true).commit(), "Could not seed native preference");
        }
        verifyMenu(context, activate);
        check(preferences.getBoolean(KEY, false), "The lock was not saved in ordinary preferences");

        ClassLoader loader = context.getClassLoader();
        Class<?> toggle = loader.loadClass("androidx.preference.CheckBoxPreference");
        onMain(() -> {
            Object nativeToggle = toggle.getConstructor(Context.class).newInstance(context);
            toggle.getMethod("setKey", String.class).invoke(nativeToggle, "pref_nsfw");
            toggle.getMethod("setChecked", boolean.class).invoke(nativeToggle, true);
            check(!(Boolean) toggle.getMethod("isChecked").invoke(nativeToggle), "Native NSFW option remained checked");
            check(!(Boolean) toggle.getMethod("isEnabled").invoke(nativeToggle), "Native NSFW option remained enabled");
            return null;
        });

        verifyHttpFiltering(loader);

        Class<?> boost = loader.loadClass("xs2");
        Object nativePreferences = boost.getMethod("n").invoke(null);
        String backup = (String) boost.getMethod("c", Context.class).invoke(nativePreferences, context);
        check(new JSONObject(backup).getBoolean(KEY), "The built-in Boost settings backup omitted the lock");
        if (activate) {
            check(preferences.edit().remove(KEY).commit(), "Could not simulate restoring into cleared settings");
            importSettings(context, backup);
            check(preferences.getBoolean(KEY, false), "Boost's native settings import did not restore the lock");
        }
    }

    private static void verifyHttpFiltering(ClassLoader loader) throws Exception {
        Class<?> clientType = loader.loadClass("okhttp3.OkHttpClient");
        Class<?> interceptorType = loader.loadClass("okhttp3.Interceptor");
        Class<?> chainType = loader.loadClass("okhttp3.Interceptor$Chain");
        Class<?> requestType = loader.loadClass("okhttp3.Request");
        Class<?> responseType = loader.loadClass("okhttp3.Response");
        Class<?> bodyType = loader.loadClass("okhttp3.ResponseBody");
        Class<?> protocolType = loader.loadClass("okhttp3.Protocol");
        Class<?> mediaType = loader.loadClass("okhttp3.MediaType");
        Class<?> responseBuilderType = loader.loadClass("okhttp3.Response$Builder");
        Object transport = Proxy.newProxyInstance(loader, new Class<?>[]{interceptorType}, (proxy, method, args) -> {
            if (!method.getName().equals("intercept")) throw new UnsupportedOperationException(method.getName());
            String feed = "{\"kind\":\"Listing\",\"data\":{\"children\":["
                    + "{\"kind\":\"t3\",\"data\":{\"over_18\":true}},"
                    + "{\"kind\":\"t3\",\"data\":{\"over_18\":false,\"id\":\"safe\"}}]}}";
            Object json = mediaType.getMethod("get", String.class).invoke(null, "application/json");
            Object body = bodyType.getMethod("create", String.class, mediaType).invoke(null, feed, json);
            Object response = responseBuilderType.getConstructor().newInstance();
            responseBuilderType.getMethod("request", requestType).invoke(response, chainType.getMethod("request").invoke(args[0]));
            responseBuilderType.getMethod("protocol", protocolType).invoke(response, protocolType.getField("HTTP_1_1").get(null));
            responseBuilderType.getMethod("code", int.class).invoke(response, 200);
            responseBuilderType.getMethod("message", String.class).invoke(response, "OK");
            responseBuilderType.getMethod("body", bodyType).invoke(response, body);
            return responseBuilderType.getMethod("build").invoke(response);
        });

        Object original = clientType.getConstructor().newInstance();
        Object builder = clientType.getMethod("newBuilder").invoke(original);
        builder.getClass().getMethod("addInterceptor", interceptorType).invoke(builder, transport);
        Object client = builder.getClass().getMethod("build").invoke(builder);
        var interceptors = (List<?>) clientType.getMethod("interceptors").invoke(client);
        String filter = "app.morphe.extension.shared.fixes.feed.MatureContentInterceptor";
        check(interceptors.stream().filter(hook -> hook.getClass().getName().equals(filter)).count() == 1,
                "Boost's native OkHttp client did not install exactly one content filter");

        Class<?> requestBuilderType = loader.loadClass("okhttp3.Request$Builder");
        Object requestBuilder = requestBuilderType.getConstructor().newInstance();
        requestBuilderType.getMethod("url", String.class).invoke(requestBuilder, "https://oauth.reddit.com/hot");
        Object request = requestBuilderType.getMethod("build").invoke(requestBuilder);
        Object call = clientType.getMethod("newCall", requestType).invoke(client, request);
        try (var response = (AutoCloseable) loader.loadClass("okhttp3.Call").getMethod("execute").invoke(call)) {
            Object body = responseType.getMethod("body").invoke(response);
            String filtered = (String) bodyType.getMethod("string").invoke(body);
            var children = new JSONObject(filtered).getJSONObject("data").getJSONArray("children");
            check(children.length() == 1 && children.getJSONObject(0).getJSONObject("data").getString("id").equals("safe"),
                    "Boost's native OkHttp response still contains the marked post");
        }
    }

    // Exercises the native drawer's cached data load without touching a real account or server.
    private static void verifyCachedSidebar(Context context) throws Exception {
        String safe = "fixture_sidebar_safe", mature = "fixture_sidebar_mature", unknown = "fixture_sidebar_unknown";
        String invalid = "fixture_sidebar_invalid";
        List<String> cached = List.of(safe, mature, unknown, invalid);
        ClassLoader loader = context.getClassLoader();
        Class<?> redditType = loader.loadClass("xd2");
        redditType.getMethod("D").invoke(null);
        Object reddit = field(redditType, "r").get(null);
        Object client = field(redditType, "e").get(reddit);
        Class<?> adapter = loader.loadClass("net.dean.jraw.http.HttpAdapter");
        Class<?> requestType = loader.loadClass("net.dean.jraw.http.HttpRequest");
        Class<?> headersType = loader.loadClass("okhttp3.Headers");
        Class<?> responseType = loader.loadClass("net.dean.jraw.http.RestResponse");
        var response = responseType.getDeclaredConstructor(
                requestType, String.class, headersType, int.class, String.class, String.class);
        response.setAccessible(true);
        Class<?> headersBuilder = loader.loadClass("okhttp3.Headers$Builder");
        Object builder = headersBuilder.getConstructor().newInstance();
        headersBuilder.getMethod("add", String.class, String.class).invoke(builder, "Content-Type", "application/json");
        Object headers = headersBuilder.getMethod("build").invoke(builder);
        CountDownLatch requested = new CountDownLatch(1);
        CountDownLatch allowResponse = new CountDownLatch(1);
        Object transport = Proxy.newProxyInstance(loader, new Class<?>[]{adapter}, (proxy, method, args) -> {
            if ("getDefaultHeaders".equals(method.getName())) return Map.of();
            if (!"execute".equals(method.getName())) throw new UnsupportedOperationException(method.getName());
            URL url = (URL) requestType.getMethod("getUrl").invoke(args[0]);
            String json;
            if (url.getPath().equals("/api/v1/access_token")) {
                json = "{\"access_token\":\"fixture\",\"token_type\":\"bearer\",\"expires_in\":3600,\"scope\":\"*\"}";
            } else if (url.getPath().equals("/api/info")) {
                check(url.getQuery().contains("sr_name=") && cached.stream().allMatch(url.getQuery()::contains),
                        "Native lookup did not request the cached communities together");
                requested.countDown();
                if (!allowResponse.await(10, TimeUnit.SECONDS)) throw new AssertionError("Fixture lookup was not released");
                json = "{\"kind\":\"Listing\",\"data\":{\"children\":["
                        + "{\"kind\":\"t5\",\"data\":{\"display_name\":\"" + mature + "\",\"over18\":true}},"
                        + "{\"kind\":\"t5\",\"data\":{\"display_name\":\"" + safe + "\",\"over_18\":false}},"
                        + "{\"kind\":\"t5\",\"data\":{\"display_name\":\"" + unknown + "\"}},"
                        + "{\"kind\":\"t5\",\"data\":{\"display_name\":\"" + invalid + "\",\"over18\":\"false\"}},"
                        + "{\"kind\":\"t5\",\"data\":null}]}}";
            } else {
                throw new java.io.IOException("Offline sidebar fixture");
            }
            return response.newInstance(args[0], json, headers, 200, "OK", "HTTP/1.1");
        });
        field(loader.loadClass("net.dean.jraw.http.RestClient"), "httpAdapter").set(client, transport);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        check(prefs.edit().putBoolean(KEY, false)
                        .putInt("pref_saved_version", context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode)
                        .putString("pref_dev_reddit_client_id", "fixture")
                        .putString("pref_dev_reddit_redirect_url", "http://localhost")
                        .putString("pref_dev_reddit_user_agent", "android:fixture:1").commit(),
                "Could not configure native sidebar fixture");
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(context.getDatabasePath("reddit.db").getPath(), null,
                SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.NO_LOCALIZED_COLLATORS)) {
            for (String name : cached) {
                ContentValues row = new ContentValues();
                row.put("Name", name);
                row.put("Subscribed", true);
                db.insertOrThrow("Subreddit", null, row);
            }
            Class<?> drawer = loader.loadClass("xj0");
            String activityName = "com.rubenmayayo.reddit.ui.submissions.subreddit.MainActivity";
            var instrumentation = PatchedditInstrumentation.instance;
            var monitor = instrumentation.addMonitor(activityName, null, false);
            Activity main;
            try {
                context.startActivity(new Intent().setClassName(context, activityName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                main = monitor.waitForActivityWithTimeout(15_000);
                check(main != null, "Native Boost Home did not start");
            } finally {
                instrumentation.removeMonitor(monitor);
            }
            Field subscriptions = field(drawer, "a");
            Field name = field(loader.loadClass("com.rubenmayayo.reddit.models.reddit.SubscriptionViewModel"), "a");
            var reload = drawer.getMethod("E1");
            try {
                onMain(() -> { reload.invoke(main); return null; });
                List<String> unlocked = onMain(() -> subscriptionNames(main, subscriptions, name));
                check(unlocked.containsAll(cached) && unlocked.containsAll(List.of("all", "popular")),
                        "Missing native sidebar rows");

                check(prefs.edit().putBoolean(KEY, true).commit(), "Could not lock fixture");
                onMain(() -> { reload.invoke(main); return null; });
                List<String> pending = onMain(() -> subscriptionNames(main, subscriptions, name));
                check(cached.stream().noneMatch(pending::contains) && pending.containsAll(List.of("all", "popular")),
                        "Sidebar exposed unverified rows while the metadata request was pending");
                check(requested.await(10, TimeUnit.SECONDS), "The drawer did not request community metadata");
                allowResponse.countDown();
                for (int attempt = 0; attempt < 80; attempt++) {
                    List<String> visible = onMain(() -> subscriptionNames(main, subscriptions, name));
                    if (visible.contains(safe)) {
                        check(!visible.contains(mature) && !visible.contains(unknown) && !visible.contains(invalid),
                                "The drawer showed a mature or unverified community after refreshing");
                        break;
                    }
                    if (attempt == 79) throw new AssertionError("The drawer did not redisplay the verified safe row");
                    Thread.sleep(50);
                }
                try (var saved = db.rawQuery("SELECT count(*) FROM Subreddit WHERE Name IN (?, ?, ?, ?) AND Subscribed = 1",
                        cached.toArray(new String[0]))) {
                    check(saved.moveToFirst() && saved.getInt(0) == cached.size(), "Filtering modified saved subscriptions");
                }
            } finally {
                allowResponse.countDown();
                onMain(() -> { main.finish(); return null; });
            }
        }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Field result = type.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static List<String> subscriptionNames(Object activity, Field subscriptions, Field name) throws Exception {
        List<String> names = new ArrayList<>();
        for (Object subscription : (ArrayList<?>) subscriptions.get(activity)) {
            names.add((String) name.get(subscription));
        }
        return names;
    }

    private static void importSettings(Context context, String backup) throws Exception {
        var file = Files.createTempFile(context.getCacheDir().toPath(), "lock-settings", ".json");
        try {
            Files.write(file, backup.getBytes(StandardCharsets.UTF_8));
            var instrumentation = PatchedditInstrumentation.instance;
            String type = "com.rubenmayayo.reddit.BackupActivity";
            var monitor = instrumentation.addMonitor(type, null, false);
            Activity activity;
            try {
                context.startActivity(new Intent().setClassName(context, type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                activity = monitor.waitForActivityWithTimeout(15_000);
                check(activity != null, "Boost's native Backup screen did not start");
            } finally {
                instrumentation.removeMonitor(monitor);
            }
            onMain(() -> {
                var result = activity.getClass().getDeclaredMethod("onActivityResult", int.class, int.class, Intent.class);
                result.setAccessible(true);
                result.invoke(activity, 2982, Activity.RESULT_OK, new Intent().setData(Uri.fromFile(file.toFile())));
                activity.finish();
                return null;
            });
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static void verifyMenu(Context context, boolean activate) throws Exception {
        var instrumentation = PatchedditInstrumentation.instance;
        var monitor = instrumentation.addMonitor(ACTIVITY, null, false);
        PreferenceActivity activity;
        try {
            context.startActivity(new Intent().setClassName(context, ACTIVITY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            activity = (PreferenceActivity) monitor.waitForActivityWithTimeout(15_000);
            check(activity != null, "Patcheddit settings did not start");
        } finally {
            instrumentation.removeMonitor(monitor);
        }
        Field dialog = activity.getClass().getDeclaredField("visibleDialog");
        dialog.setAccessible(true);
        try {
            onMain(() -> {
                Preference option = activity.findPreference(OPTION);
                if (!activate) {
                    check(option == null, "The optional control was still present after repatching");
                    return null;
                }
                check(option instanceof CheckBoxPreference && option.isEnabled(),
                        "The opt-in checkbox is missing or disabled");
                CheckBoxPreference checkbox = (CheckBoxPreference) option;
                requestLock(checkbox);
                ((AlertDialog) dialog.get(activity)).getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
                return null;
            });
            if (activate) {
                instrumentation.waitForIdleSync();
                onMain(() -> {
                    CheckBoxPreference checkbox = (CheckBoxPreference) activity.findPreference(OPTION);
                    check(!checkbox.isChecked() && checkbox.isEnabled()
                                    && !PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY, false),
                            "Cancelling changed the NSFW lock");
                    requestLock(checkbox);
                    ((AlertDialog) dialog.get(activity)).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                    return null;
                });
                instrumentation.waitForIdleSync();
                onMain(() -> {
                    CheckBoxPreference checkbox = (CheckBoxPreference) activity.findPreference(OPTION);
                    check(checkbox.isChecked() && !checkbox.isEnabled(),
                            "The activated lock is not shown as checked and disabled");
                    return null;
                });
            }
        } finally {
            onMain(() -> { activity.finish(); return null; });
        }
    }

    private static void requestLock(CheckBoxPreference checkbox) {
        check(!checkbox.isChecked(), "The lock was checked before confirmation");
        check(!checkbox.getOnPreferenceChangeListener().onPreferenceChange(checkbox, true),
                "The checkbox allowed an unconfirmed change");
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
