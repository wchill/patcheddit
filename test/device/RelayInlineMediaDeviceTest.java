/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.patcheddit.fixture;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Animatable;
import android.os.SystemClock;
import android.text.Spanned;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

public final class RelayInlineMediaDeviceTest {
    private static final String KEY = "morphe_patch_inline_media", KEEP = "morphe_relay_inline_keep_urls";
    private static final String PREVIEW = "app.morphe.extension.relay.media.InlineMedia$Preview";
    private static final String URL = "https://preview.redd.it/PatchedditFixture/";
    private static final String QUERY = "?s=fixture%2Fsignature&run=" + System.nanoTime();
    private static ClassLoader loader;
    private static Activity activity;
    private static LinearLayout rows;
    private static Object adapter, holder;
    private static TextView comment;

    public static void verify(Context context, Activity host) throws Exception {
        loader = context.getClassLoader();
        activity = host;
        SharedPreferences prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences backup = context.getSharedPreferences("patcheddit_relay_inline_fixture", 0);
        if (backup.contains("pending")) restore(prefs, backup.getAll());
        Map<String, ?> before = new HashMap<>(prefs.getAll());
        restore(backup, before);
        check(backup.edit().putBoolean("pending", true).commit(), "Could not preserve inline preferences");
        check(prefs.edit().remove(KEY).remove(KEEP).commit(), "Could not configure default inline options");
        try {
            onMain(() -> {
                ScrollView scroll = new ScrollView(activity);
                rows = new LinearLayout(activity);
                rows.setOrientation(LinearLayout.VERTICAL);
                scroll.addView(rows, new ScrollView.LayoutParams(600, ViewGroup.LayoutParams.WRAP_CONTENT));
                activity.setContentView(scroll);
                Class<?> delegate = type("reddit.news.listings.comments.delegates.CommentsAdapterDelegateList");
                Object[] dependencies = new Object[7];
                dependencies[0] = listing(host.getClass().getMethod("getSupportFragmentManager").invoke(host));
                check(dependencies[0] != null, "Native listing host was not available");
                dependencies[1] = prefs;
                adapter = delegate.getConstructors()[0].newInstance(dependencies);
                holder = delegate.getMethod("a", ViewGroup.class).invoke(adapter, rows);
                rows.addView((View) holder.getClass().getField("itemView").get(holder));
                comment = (TextView) field(holder, "comment");
                return null;
            });
            String image = URL + "test.jpeg" + QUERY;
            String gif = URL + "test.gif" + QUERY;
            String webp = URL + "test.webp" + QUERY;
            String html = "<p>" + link(image, image) + "</p><p>" + link(gif, "Descriptive GIF")
                    + "</p><p>" + link(webp, webp) + "</p><pre><code>" + image + "</code></pre>";
            onMain(() -> { bind(html); return null; });
            await(() -> onMain(() -> loaded(comment) == 3), "Native comment previews did not load");
            onMain(() -> {
                List<Object> previews = previews(comment);
                Spanned rendered = (Spanned) comment.getText();
                check(comment.getText().toString().contains(image), "URLs were not kept by default");
                String[] labels = {image, "Descriptive GIF", webp};
                for (int i = 0; i < previews.size(); i++) {
                    int position = rendered.getSpanStart(previews.get(i));
                    check(rendered.subSequence(0, position).toString().trim().endsWith(labels[i]),
                            "Media was not immediately after its corresponding URL");
                }
                check(comment.getText().toString().contains("Descriptive GIF"), "Descriptive label was lost");
                check(animation(previews.get(1)).isRunning() && animation(previews.get(2)).isRunning(),
                        "Native GIF/WebP previews did not animate");
                Class<?> listener = type("reddit.news.listings.common.views.ActiveTextView2$OnLinkClickedListener");
                String[] clicked = {null};
                Object capture = Proxy.newProxyInstance(loader, new Class<?>[]{listener}, (proxy, method, args) -> {
                    if (method.getName().equals("toString")) return "Inline tap fixture";
                    clicked[0] = (String) args[0]; return null;
                });
                comment.getClass().getMethod("setLinkClickedListener", listener).invoke(comment, capture);
                int position = rendered.getSpanStart(previews.get(0));
                int line = comment.getLayout().getLineForOffset(position);
                float y = comment.getLayout().getLineBottom(line) - 10 + comment.getTotalPaddingTop();
                long now = SystemClock.uptimeMillis();
                for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
                    MotionEvent event = MotionEvent.obtain(now, now + 30, action, comment.getTotalPaddingLeft() + 10, y, 0);
                    comment.dispatchTouchEvent(event);
                    event.recycle();
                }
                check(image.equals(clicked[0]), "Inline image did not retain native link taps");
                check(prefs.edit().putBoolean(KEEP, false).commit(), "Could not change URL visibility");
                return null;
            });
            await(() -> onMain(() -> loaded(comment) == 3
                    && comment.getText().toString().indexOf(webp) < 0), "URL visibility did not update existing media");
            onMain(() -> {
                check(comment.getText().toString().contains(image), "Code text was removed");
                check(comment.getText().toString().contains("Descriptive GIF"), "URL option removed a descriptive label");
                Animatable old = animation(previews(comment).get(1));
                bind("<p>Recycled comment.</p>");
                check(previews(comment).isEmpty() && !old.isRunning(), "Recycling left animated media active");
                return null;
            });
            onMain(() -> { bind("<p>" + link(URL + "slow.jpeg" + QUERY, "Same label") + "</p>"); return null; });
            await(() -> onMain(() -> !previews(comment).isEmpty()
                    && (Boolean) field(previews(comment).get(0), "active")), "Slow preview did not start");
            onMain(() -> { bind("<p>Different body.</p>"); return null; });
            RelayMediaLinksDeviceTest.SLOW.countDown();
            Thread.sleep(150);
            onMain(() -> { check(previews(comment).isEmpty(), "Late media reached a recycled row"); return null; });
            onMain(() -> {
                bind("<p>![gif](giphy|PatchedditFixture)</p><pre><code>![gif](giphy|CodeExample)</code></pre>"
                        + "<p><img src=\"" + image.replace("&", "&amp;") + "\"></p>");
                return null;
            });
            await(() -> onMain(() -> loaded(comment) == 2), "GIF marker or embedded image did not load");
            onMain(() -> {
                check(comment.getText().toString().contains("![gif](giphy|CodeExample)"),
                        "Code example was converted into media");
                return null;
            });
            String corrupt = URL + "corrupt.png" + QUERY;
            onMain(() -> { bind("<p>" + link(corrupt, corrupt) + "</p>"); return null; });
            await(() -> onMain(() -> previews(comment).isEmpty() && comment.getText().toString().contains(corrupt)),
                    "Failed preview did not restore the hidden URL");
            onMain(() -> { bind("<p>" + link(gif, gif) + "</p>"); return null; });
            await(() -> onMain(() -> loaded(comment) == 1), "Animated preview did not reload");
            Object preview = onMain(() -> previews(comment).get(0));
            Animatable animation = animation(preview);
            onMain(() -> {
                rows.removeAllViews();
                check(!animation.isRunning(), "Detached preview kept animating");
                return null;
            });
            TextView post = onMain(() -> {
                TextView view = (TextView) type("reddit.news.listings.common.views.ActiveTextView2")
                        .getConstructor(Context.class, android.util.AttributeSet.class).newInstance(activity, null);
                rows.addView(view);
                Object fragment = type("reddit.news.WebAndCommentsFragment").getConstructor().newInstance();
                Object manager = host.getClass().getMethod("getSupportFragmentManager").invoke(host);
                set(fragment, "mHost", field(manager, "mHost"));
                set(fragment, "mAdded", true);
                set(fragment, "mView", rows);
                for (Field field : fragment.getClass().getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    field.setAccessible(true);
                    if (field.getType().getName().equals("reddit.news.listings.common.views.ActiveTextView2")) field.set(fragment, view);
                    else if (field.getType() == ViewGroup.class || field.getType() == LinearLayout.class) {
                        field.set(fragment, new LinearLayout(activity));
                    }
                }
                Method method = Arrays.stream(fragment.getClass().getDeclaredMethods()).filter(candidate ->
                        Modifier.isProtected(candidate.getModifiers())
                                && Arrays.equals(candidate.getParameterTypes(), new Class<?>[]{CharSequence.class}))
                        .findFirst().orElseThrow();
                method.setAccessible(true);
                method.invoke(fragment, parsed("<p>" + link(image, image) + "</p>"));
                return view;
            });
            await(() -> onMain(() -> loaded(post) == 1), "Native self-post body did not render inline media");
            onMain(() -> {
                check(prefs.edit().putBoolean(KEY, false).commit(), "Could not disable inline media");
                return null;
            });
            await(() -> onMain(() -> previews(post).isEmpty() && post.getText().toString().contains(image)),
                    "Disabling inline media did not restore native text");
        } finally {
            RelayMediaLinksDeviceTest.SLOW.countDown();
            onMain(() -> { rows.removeAllViews(); return null; });
            restore(prefs, before);
            check(backup.edit().clear().commit(), "Could not clear inline preference backup");
        }
    }

    private static String link(String url, String text) {
        return "<a href=\"" + url.replace("&", "&amp;") + "\">" + text + "</a>";
    }
    private static Object listing(Object manager) throws Exception {
        for (Object fragment : (List<?>) manager.getClass().getMethod("getFragments").invoke(manager)) {
            if (type("reddit.news.listings.common.ListingBaseFragment").isInstance(fragment)) return fragment;
            Object nested = listing(fragment.getClass().getMethod("getChildFragmentManager").invoke(fragment));
            if (nested != null) return nested;
        }
        return null;
    }
    private static Spanned parsed(String html) throws Exception {
        return (Spanned) type("reddit.news.utils.RedditUtils")
                .getMethod("j", String.class, boolean.class, String.class).invoke(null, html, true, "fixture");
    }
    private static void bind(String html) throws Exception {
        Object model = type("reddit.news.oauth.reddit.model.RedditComment").getConstructor().newInstance();
        set(model, "htmlBodySpanned", parsed(html));
        set(holder, "b", model);
        List<?> payloads = List.of(type("reddit.news.listings.links.payloads.EditedPayload").getConstructor().newInstance());
        Method method = Arrays.stream(adapter.getClass().getMethods()).filter(candidate ->
                candidate.getParameterCount() == 4 && candidate.getParameterTypes()[3] == List.class)
                .findFirst().orElseThrow();
        method.invoke(adapter, model, holder, 0, payloads);
    }
    private static List<Object> previews(TextView view) throws Exception {
        CharSequence text = view.getText();
        List<Object> result = new ArrayList<>();
        if (text instanceof Spanned) {
            result.addAll(Arrays.asList(((Spanned) text).getSpans(0, text.length(), type(PREVIEW))));
        }
        return result;
    }
    private static int loaded(TextView view) throws Exception {
        int count = 0;
        for (Object preview : previews(view)) if (field(preview, "drawable") != null) count++;
        return count;
    }
    private static Animatable animation(Object preview) throws Exception { return (Animatable) field(preview, "drawable"); }
    private static Class<?> type(String name) throws Exception { return loader.loadClass(name); }
    private static Object field(Object object, String name) throws Exception {
        for (Class<?> owner = object.getClass(); owner != null; owner = owner.getSuperclass()) {
            try { Field field = owner.getDeclaredField(name); field.setAccessible(true); return field.get(object); }
            catch (NoSuchFieldException next) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static void set(Object object, String name, Object value) throws Exception {
        for (Class<?> owner = object.getClass(); owner != null; owner = owner.getSuperclass()) {
            try { Field field = owner.getDeclaredField(name); field.setAccessible(true); field.set(object, value); return; }
            catch (NoSuchFieldException next) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static void restore(SharedPreferences prefs, Map<String, ?> before) {
        SharedPreferences.Editor edit = prefs.edit();
        for (String key : new String[]{KEY, KEEP}) {
            if (before.containsKey(key)) edit.putBoolean(key, (Boolean) before.get(key)); else edit.remove(key);
        }
        check(edit.commit(), "Could not restore inline media settings");
    }
    private static <T> T onMain(Callable<T> task) throws Exception {
        FutureTask<T> future = new FutureTask<>(task);
        PatchedditInstrumentation.instance.runOnMainSync(future);
        return future.get();
    }
    private static void await(Callable<Boolean> ready, String message) throws Exception {
        long until = SystemClock.elapsedRealtime() + 15_000;
        while (SystemClock.elapsedRealtime() < until) {
            if (ready.call()) return;
            Thread.sleep(40);
        }
        throw new AssertionError(message);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
