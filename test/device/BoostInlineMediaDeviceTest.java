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
import android.graphics.Color;
import android.graphics.drawable.Animatable;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public final class BoostInlineMediaDeviceTest {
    private static final String INLINE = "app.morphe.extension.boost.media.InlineMedia";
    private static final String KEY = "morphe_patch_load_all_inline_media";
    private static final String KEEP = "morphe_boost_inline_keep_urls";
    private static final String BASE = "http://patcheddit.fixture/";
    private static ClassLoader loader;
    private static Activity activity;
    private static LinearLayout rows;
    private static final Map<String, byte[]> PAYLOADS = new HashMap<>();
    private static final CountDownLatch SLOW = new CountDownLatch(1);

    public static void run(Context context, Bundle arguments) throws Exception {
        loader = context.getClassLoader();
        SharedPreferences preferences = android.preference.PreferenceManager.getDefaultSharedPreferences(context);
        String[] keys = {KEY, KEEP, "pref_comments_inline_media", "pref_saved_version",
                "pref_dev_reddit_client_id", "pref_dev_reddit_redirect_url", "pref_dev_reddit_user_agent"};
        String[] defaults = {"fixture", "http://localhost", "android:fixture:1"};
        SharedPreferences backup = context.getSharedPreferences("patcheddit_inline_fixture_backup", Context.MODE_PRIVATE);
        if (backup.contains("pending")) {
            restore(preferences, keys, backup.getAll());
            check(backup.edit().clear().commit(), "Could not clear recovered fixture backup");
        }
        if ("true".equals(arguments.getString("reset-fixture-settings"))) {
            SharedPreferences.Editor reset = preferences.edit().remove(KEY).remove(KEEP);
            for (int i = 4; i < keys.length; i++) {
                if (defaults[i - 4].equals(preferences.getString(keys[i], ""))) reset.remove(keys[i]);
            }
            check(reset.commit(), "Could not remove initial fixture settings");
        }
        Map<String, ?> before = new HashMap<>(preferences.getAll());
        restore(backup, keys, before);
        check(backup.edit().putBoolean("pending", true).commit(), "Could not save fixture recovery state");
        SharedPreferences.Editor setup = preferences.edit().putBoolean(KEY, true).remove(KEEP).putBoolean(keys[2], true)
                .putInt(keys[3], context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode);
        for (int i = 4; i < keys.length; i++) {
            if (preferences.getString(keys[i], "").isEmpty()) setup.putString(keys[i], defaults[i - 4]);
        }
        check(setup.commit(), "Could not configure inline-media fixture");
        try {
            transport(context);
            var instrumentation = PatchedditInstrumentation.instance;
            var monitor = instrumentation.addMonitor(
                    "com.rubenmayayo.reddit.ui.submissions.subreddit.MainActivity", null, false);
            try {
                context.startActivity(context.getPackageManager().getLaunchIntentForPackage(context.getPackageName())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                activity = monitor.waitForActivityWithTimeout(15_000);
                check(activity != null, "Native Boost test host did not start");
            } finally { instrumentation.removeMonitor(monitor); }
            onMain(() -> {
                ScrollView scroll = new ScrollView(activity);
                rows = new LinearLayout(activity);
                rows.setOrientation(LinearLayout.VERTICAL);
                scroll.addView(rows);
                activity.setContentView(scroll);
                return null;
            });
            String html = body();
            LinearLayout table = onMain(() -> table(html));
            await(() -> onMain(() -> loaded(table) == 5), "Not all five linked images loaded");
            onMain(() -> {
                ordered(table, 5);
                check(text(table).contains(BASE + "one.png"), "Image URLs were not kept by default");
                check(text(table).contains("Descriptive link"), "Descriptive label was lost");
                preferences.edit().putBoolean(KEEP, false).commit();
                table.invalidate();
                return null;
            });
            await(() -> onMain(() -> loaded(table) == 5 && !text(table).contains(BASE + "one.png")),
                    "URL option did not update existing media");
            onMain(() -> {
                check(text(table).contains("Descriptive link"), "URL option removed a descriptive label");
                ordered(table, 5);
                Animatable animation = (Animatable) previews(table).get(3).getDrawable();
                check(animation.isRunning(), "Native GIF did not animate");
                render(table, "<p>Recycled body without media.</p>");
                check(previews(table).isEmpty() && text(table).contains("Recycled body"), "Recycled body retained old media");
                check(!animation.isRunning(), "Recycled GIF kept running");
                return null;
            });
            LinearLayout slow = onMain(() -> table("<p><a href=\"" + BASE + "slow.png\">slow</a></p>"));
            await(() -> onMain(() -> !previews(slow).isEmpty() && (Boolean) get(previews(slow).get(0), "active")),
                    "Slow image did not start loading");
            onMain(() -> { render(slow, "<p>Different body.</p>"); return null; });
            SLOW.countDown();
            Thread.sleep(200);
            onMain(() -> {
                check(previews(slow).isEmpty() && text(slow).equals("Different body."), "Late media reached a recycled row");
                rows.removeAllViews();
                return null;
            });
            String bad = BASE + "corrupt.png";
            LinearLayout corrupt = onMain(() -> table("<p><a href=\"" + bad + "\">" + bad + "</a></p>"));
            await(() -> onMain(() -> previews(corrupt).get(0).getVisibility() == View.GONE && text(corrupt).contains(bad)),
                    "Failure did not restore the hidden URL");
            onMain(() -> { rows.removeAllViews(); return null; });
            markersAndTables(preferences);
            nativeBodies(preferences, html);
            settings(context);
        } finally {
            SLOW.countDown();
            if (activity != null) onMain(() -> { if (rows != null) rows.removeAllViews(); activity.finish(); return null; });
            restore(preferences, keys, before);
            check(backup.edit().clear().commit(), "Could not clear fixture recovery state");
        }
    }

    private static void nativeBodies(SharedPreferences preferences, String html) throws Exception {
        for (boolean comment : new boolean[]{true, false}) {
            LinearLayout body = onMain(() -> {
                rows.removeAllViews();
                String layout = comment ? "row_comment_content" : "row_submission_comments";
                View root = activity.getLayoutInflater().inflate(activity.getResources()
                        .getIdentifier(layout, "layout", activity.getPackageName()), rows, false);
                rows.addView(root, new LinearLayout.LayoutParams(320, -2));
                Class<?> holderType = loader.loadClass("com.rubenmayayo.reddit.ui.adapters."
                        + (comment ? "CommentViewHolder" : "SubmissionViewHolder"));
                Class<?> listener = loader.loadClass(comment ? "nv" : "j23");
                Object callback = Proxy.newProxyInstance(loader, new Class<?>[]{listener}, (proxy, method, args) -> {
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    return null;
                });
                Object holder = comment ? holderType.getConstructor(View.class, listener, boolean.class)
                        .newInstance(root, callback, false)
                        : holderType.getConstructor(View.class, listener, loader.loadClass("oh1"))
                        .newInstance(root, callback, loader.loadClass("oh1").getEnumConstants()[0]);
                Class<?> modelType = loader.loadClass("com.rubenmayayo.reddit.models.reddit."
                        + (comment ? "CommentModel" : "SubmissionModel"));
                Object model = modelType.getConstructor().newInstance();
                for (Class<?> type = modelType; type != Object.class; type = type.getSuperclass()) {
                    for (Field field : type.getDeclaredFields()) {
                        if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) continue;
                        field.setAccessible(true);
                        if (field.getType() == String.class) field.set(model, "#808080");
                        else if (field.getType() == Date.class) field.set(model, new Date());
                        else if (field.getType() == Boolean.class) field.set(model, false);
                        else if (field.getType() == List.class || field.getType() == ArrayList.class) field.set(model, new ArrayList<>());
                    }
                }
                Field content = modelType.getDeclaredField(comment ? "d0" : "S");
                content.setAccessible(true);
                content.set(model, html);
                Field raw = modelType.getDeclaredField(comment ? "a0" : "T");
                raw.setAccessible(true);
                raw.set(model, "fixture");
                Method bind = holderType.getDeclaredMethod("w", modelType, boolean.class, boolean.class,
                        boolean.class, loader.loadClass("pg2"));
                bind.invoke(holder, model, false, !comment, true, null);
                Field view = holderType.getDeclaredField(comment ? "commentTv" : "selfTv");
                view.setAccessible(true);
                LinearLayout table = (LinearLayout) view.get(holder);
                if (comment) {
                    preferences.edit().putBoolean(KEY, false).commit();
                    bind.invoke(holder, model, false, false, true, null);
                    check(previews(table).isEmpty() && text(table).contains(BASE + "one.png"),
                            "Disabled patch did not restore native comment text");
                    Field legacy = holderType.getDeclaredField("mediaPreviewIv");
                    legacy.setAccessible(true);
                    check(((ImageView) legacy.get(holder)).getVisibility() == View.VISIBLE,
                            "Disabled patch did not restore Boost's single preview");
                    preferences.edit().putBoolean(KEY, true).commit();
                    bind.invoke(holder, model, false, false, true, null);
                }
                check(previews(table).size() == 5, "Native " + (comment ? "comment" : "post") + " binder missed media");
                return table;
            });
            await(() -> onMain(() -> loaded(body) == 5), "Native body images did not load");
            onMain(() -> { ordered(body, 5); rows.removeAllViews(); return null; });
        }
        onMain(() -> {
            preferences.edit().putBoolean(KEY, false).commit();
            LinearLayout disabled = table(html);
            check(previews(disabled).isEmpty() && text(disabled).contains(BASE + "one.png"), "Disabled patch changed native text");
            preferences.edit().putBoolean(KEY, true).putBoolean("pref_comments_inline_media", false).commit();
            render(disabled, html);
            check(previews(disabled).isEmpty(), "Native inline-media switch was ignored");
            preferences.edit().putBoolean("pref_comments_inline_media", true).putBoolean(KEEP, true).commit();
            rows.removeAllViews();
            return null;
        });
    }

    private static void markersAndTables(SharedPreferences preferences) throws Exception {
        String html = "<p>![gif](giphy|PatchedditFixture) <img src=\""
                + BASE + "one.png\" /></p><pre><code>![gif](giphy|CodeExample)</code></pre><p>Footer.</p>";
        String nativeCode = onMain(() -> {
            preferences.edit().putBoolean(KEY, false).commit();
            String original = text(table(html));
            rows.removeAllViews();
            preferences.edit().putBoolean(KEY, true).commit();
            return original.substring(original.lastIndexOf("![gif](giphy|Code"));
        });
        LinearLayout embedded = onMain(() -> table(html));
        await(() -> onMain(() -> loaded(embedded) == 2), "GIF markers and embedded images were not loaded");
        onMain(() -> {
            check(previews(embedded).size() == 2 && text(embedded).endsWith(nativeCode),
                    "A code example changed: count=" + previews(embedded).size() + ", text=" + text(embedded));
            rows.removeAllViews();
            return null;
        });
        LinearLayout withTable = onMain(() -> table("<p><a href=\"" + BASE + "one.png\">Image</a></p>"
                + "<table><thead><tr><th>Column</th></tr></thead><tbody><tr><td>Cell</td></tr></tbody></table><p>Footer</p>"));
        await(() -> onMain(() -> loaded(withTable) == 1), "Media beside a native table did not load");
        onMain(() -> {
            check(deepText(withTable).contains("Cell") && deepText(withTable).contains("Footer"),
                    "Inline media removed native table content");
            rows.removeAllViews();
            return null;
        });
    }

    private static String deepText(View view) {
        if (view instanceof TextView) return ((TextView) view).getText().toString();
        StringBuilder text = new StringBuilder();
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) text.append(deepText(group.getChildAt(i)));
        }
        return text.toString();
    }

    private static void restore(SharedPreferences preferences, String[] keys, Map<String, ?> values) {
        SharedPreferences.Editor editor = preferences.edit();
        for (String key : keys) {
            Object value = values.get(key);
            if (value == null) editor.remove(key);
            else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Integer) editor.putInt(key, (Integer) value);
            else editor.putString(key, (String) value);
        }
        check(editor.commit(), "Could not restore fixture preferences");
    }

    private static void settings(Context context) throws Exception {
        Activity settings = PatchedditInstrumentation.instance.startActivitySync(new Intent()
                .setClassName(context, "app.morphe.extension.shared.settings.MorpheSettingsActivity")
                .putExtra("morphe_screen", "morphe_category_media").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        onMain(() -> {
            android.preference.PreferenceActivity screen = (android.preference.PreferenceActivity) settings;
            check(((android.preference.CheckBoxPreference) screen.findPreference(KEEP)).isChecked(),
                    "Boost URL visibility setting is missing or disabled");
            settings.finish();
            return null;
        });
    }

    private static String body() {
        StringBuilder html = new StringBuilder("<p>Before ");
        for (String name : new String[]{"one.png", "two.png", "three.png", "animated.gif", "five.png"}) {
            String url = BASE + name;
            html.append("<a href=\"").append(url).append("\">")
                    .append(name.equals("two.png") ? "Descriptive link" : url).append("</a> between ");
        }
        return html.append("After.</p>").toString();
    }

    private static LinearLayout table(String html) throws Exception {
        LinearLayout table = (LinearLayout) loader.loadClass("com.rubenmayayo.reddit.ui.customviews.TableTextView")
                .getConstructor(Context.class, android.util.AttributeSet.class).newInstance(activity, null);
        rows.addView(table, new LinearLayout.LayoutParams(300, -2));
        render(table, html);
        return table;
    }

    private static void render(LinearLayout table, String html) throws Exception {
        table.getClass().getMethod("setTextHtml", String.class).invoke(table, html);
        loader.loadClass(INLINE).getMethod("bind", LinearLayout.class, Object.class).invoke(null, table, null);
    }

    private static List<ImageView> previews(LinearLayout table) {
        List<ImageView> result = new ArrayList<>();
        for (int i = 0; i < table.getChildCount(); i++) {
            View view = table.getChildAt(i);
            if (view.getClass().getName().equals(INLINE + "$MediaImage")) result.add((ImageView) view);
        }
        return result;
    }

    private static int loaded(LinearLayout table) {
        int result = 0;
        for (ImageView image : previews(table)) if (image.getDrawable() != null) result++;
        return result;
    }

    private static void ordered(LinearLayout table, int expected) throws Exception {
        List<ImageView> images = previews(table);
        check(images.size() == expected, "Wrong number of inline media items");
        String[] targets = {"one.png", "two.png", "three.png", "animated.gif", "five.png"};
        for (int i = 0; i < images.size(); i++) {
            ImageView image = images.get(i);
            check((BASE + targets[i]).equals(get(image, "link")), "Media is associated with the wrong URL");
            TextView prefix = (TextView) get(image, "prefix");
            check(table.indexOfChild(image) == table.indexOfChild(prefix) + 1 && image.getTop() >= prefix.getBottom(),
                    "Media is not immediately after its URL");
            if (i + 1 < images.size()) check(((View) get(images.get(i + 1), "prefix")).getTop() >= image.getBottom(),
                    "Media overlaps following text");
        }
    }

    private static String text(LinearLayout table) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < table.getChildCount(); i++) {
            View view = table.getChildAt(i);
            if (view instanceof TextView) result.append(((TextView) view).getText());
        }
        return result.toString();
    }

    private static void transport(Context context) throws Exception {
        for (String name : new String[]{"one.png", "two.png", "three.png", "five.png", "slow.png"}) {
            Bitmap image = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888);
            image.eraseColor(name.equals("two.png") ? Color.BLUE : Color.RED);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            image.compress(Bitmap.CompressFormat.PNG, 100, bytes);
            image.recycle();
            PAYLOADS.put("/" + name, bytes.toByteArray());
        }
        try (InputStream stream = PatchedditInstrumentation.instance.getContext().getAssets().open("inline-animation.gif")) {
            PAYLOADS.put("/animated.gif", stream.readAllBytes());
        }
        PAYLOADS.put("/corrupt.png", new byte[]{1, 2, 3});
        Class<?> interceptor = loader.loadClass("okhttp3.Interceptor");
        Object fake = Proxy.newProxyInstance(loader, new Class<?>[]{interceptor}, (proxy, method, args) -> {
            if (method.getName().equals("toString")) return "inline media fixture";
            Object chain = args[0];
            Object request = chain.getClass().getMethod("request").invoke(chain);
            Object url = request.getClass().getMethod("url").invoke(request);
            String host = (String) url.getClass().getMethod("host").invoke(url);
            String path = (String) url.getClass().getMethod("encodedPath").invoke(url);
            boolean marker = host.equals("media.giphy.com") && path.startsWith("/media/PatchedditFixture/");
            if (!host.equals("patcheddit.fixture") && !marker) {
                return chain.getClass().getMethod("proceed", request.getClass()).invoke(chain, request);
            }
            if (marker) path = "/animated.gif";
            if (path.equals("/slow.png") && !SLOW.await(15, TimeUnit.SECONDS)) throw new IOException("Fixture timed out");
            byte[] bytes = PAYLOADS.get(path);
            int status = bytes == null ? 404 : 200;
            if (bytes == null) bytes = new byte[0];
            Object body = loader.loadClass("okhttp3.ResponseBody")
                    .getMethod("create", byte[].class, loader.loadClass("okhttp3.MediaType")).invoke(null, bytes, null);
            Class<?> responseType = loader.loadClass("okhttp3.Response$Builder");
            Object response = responseType.getConstructor().newInstance();
            responseType.getMethod("request", request.getClass()).invoke(response, request);
            responseType.getMethod("protocol", loader.loadClass("okhttp3.Protocol"))
                    .invoke(response, loader.loadClass("okhttp3.Protocol").getField("HTTP_1_1").get(null));
            responseType.getMethod("code", int.class).invoke(response, status);
            responseType.getMethod("message", String.class).invoke(response, "fixture");
            responseType.getMethod("body", loader.loadClass("okhttp3.ResponseBody")).invoke(response, body);
            return responseType.getMethod("build").invoke(response);
        });
        Class<?> builderType = loader.loadClass("okhttp3.OkHttpClient$Builder");
        Object builder = builderType.getConstructor().newInstance();
        builderType.getMethod("addInterceptor", interceptor).invoke(builder, fake);
        Object client = builderType.getMethod("build").invoke(builder);
        Class<?> glide = loader.loadClass("com.bumptech.glide.a");
        Object instance = glide.getMethod("a", Context.class).invoke(null, context);
        Field glideContext = glide.getDeclaredField("x");
        glideContext.setAccessible(true);
        Object registryContext = glideContext.get(instance);
        Object registry = registryContext.getClass().getMethod("a").invoke(registryContext);
        Class<?> factory = loader.loadClass("com.bumptech.glide.integration.okhttp3.b$a");
        Object replacement = factory.getConstructor(loader.loadClass("okhttp3.Call$Factory")).newInstance(client);
        registry.getClass().getMethod("k", factory).invoke(registry, replacement);
    }

    private static Object get(Object value, String name) throws Exception {
        Field field = value.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(value);
    }
    private static <T> T onMain(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        PatchedditInstrumentation.instance.runOnMainSync(task);
        return task.get(10, TimeUnit.SECONDS);
    }
    private static void await(Callable<Boolean> ready, String error) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 15_000;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (ready.call()) return;
            Thread.sleep(30);
        }
        throw new AssertionError(error);
    }
    private static void check(boolean value, String error) { if (!value) throw new AssertionError(error); }
}
