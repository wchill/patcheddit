/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.patcheddit.fixture;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
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

    private record RowLayout(TextView body, int height, int top) {}

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
            for (int index : new int[]{1, 2}) {
                onMain(() -> {
                    Spanned text = (Spanned) comment.getText();
                    int position = text.getSpanStart(previews(comment).get(index));
                    int y = comment.getTotalPaddingTop()
                            + comment.getLayout().getLineTop(comment.getLayout().getLineForOffset(position));
                    comment.requestRectangleOnScreen(new Rect(0, y, 1, y + 100), true);
                    return null;
                });
                await(() -> onMain(() -> animation(previews(comment).get(index)).isRunning()),
                        "Visible GIF/WebP preview did not animate");
            }
            onMain(() -> {
                ((ScrollView) rows.getParent()).scrollTo(0, 0);
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
                Object gifDrawable = field(previews.get(1), "drawable");
                bindModel(field(holder, "b"));
                check(comment.getText() == rendered && field(previews.get(1), "drawable") == gifDrawable,
                        "Rebinding the same comment reset native media");
                View row = rows.getChildAt(0);
                rows.removeView(row);
                rows.addView(row);
                check(comment.getText() == rendered && field(previews.get(1), "drawable") == gifDrawable,
                        "A brief layout detach discarded the preview");
                Class<?> listener = type("reddit.news.listings.common.views.ActiveTextView2$OnLinkClickedListener");
                String[] clicked = {null};
                Object capture = Proxy.newProxyInstance(loader, new Class<?>[]{listener}, (proxy, method, args) -> {
                    if (method.getName().equals("toString")) return "Inline tap fixture";
                    clicked[0] = (String) args[0]; return null;
                });
                comment.getClass().getMethod("setLinkClickedListener", listener).invoke(comment, capture);
                Rect imageBounds = previewBounds(comment, previews.get(0));
                Field highlight = Arrays.stream(type("reddit.news.listings.common.views.ActiveTextView2").getDeclaredFields())
                        .filter(field -> field.getType() == ArrayList.class).findFirst().orElseThrow();
                highlight.setAccessible(true);
                long now = SystemClock.uptimeMillis();
                touch(comment, now, MotionEvent.ACTION_DOWN, imageBounds.centerX(), imageBounds.centerY());
                check(clicked[0] == null && ((List<?>) highlight.get(comment)).isEmpty(),
                        "Starting a scroll on the preview opened or highlighted it");
                touch(comment, now, MotionEvent.ACTION_MOVE, imageBounds.centerX(), imageBounds.top);
                touch(comment, now, MotionEvent.ACTION_UP, imageBounds.centerX(), imageBounds.top);
                check(clicked[0] == null, "Dragging across a preview opened the viewer");
                tap(comment, imageBounds.left + imageBounds.width() / 8, imageBounds.centerY());
                check(clicked[0] == null, "Tapping the image edge opened the viewer");
                tap(comment, imageBounds.centerX(), imageBounds.centerY());
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
            String embeddedGif = "https://media2.giphy.com/media/PatchedditFixture/giphy.gif" + QUERY;
            onMain(() -> {
                bind("<p>" + link("https://giphy.com/gifs/fixture-PatchedditFixture",
                        "<img src=\"" + embeddedGif.replace("&", "&amp;") + "\">") + "</p>");
                return null;
            });
            await(() -> onMain(() -> loaded(comment) == 1 && animation(previews(comment).get(0)).isRunning()),
                    "Relay's rewritten Giphy embed did not animate");
            String corrupt = URL + "corrupt.png" + QUERY;
            onMain(() -> { bind("<p>" + link(corrupt, corrupt) + "</p>"); return null; });
            await(() -> onMain(() -> {
                Spanned text = (Spanned) comment.getText();
                return previews(comment).isEmpty() && Arrays.stream(text.getSpans(0, text.length(), URLSpan.class))
                        .anyMatch(link -> link.getURL().equals(corrupt));
            }), "Failed preview did not become a regular link");
            onMain(() -> { bind("<p>" + link(gif, gif) + "</p>"); return null; });
            await(() -> onMain(() -> loaded(comment) == 1), "Animated preview did not reload");
            Object preview = onMain(() -> previews(comment).get(0));
            Animatable animation = animation(preview);
            onMain(() -> {
                rows.removeAllViews();
                check(!animation.isRunning(), "Detached preview kept animating");
                return null;
            });
            String threadImage = URL + "thread.png" + QUERY + "&width=911&format=png&auto=webp";
            ListView thread = onMain(() -> thread(threadImage, prefs, context));
            int bodyId = resource(context, "body", "id");
            try {
                await(() -> onMain(() -> {
                    TextView body = thread.findViewById(bodyId);
                    return body != null && loaded(body) == 1;
                }), "Native comment thread did not render a signed Reddit image");
            } catch (AssertionError failure) {
                String state = onMain(() -> describeBody(thread.findViewById(bodyId)));
                throw new AssertionError(failure.getMessage() + ": " + state, failure);
            }
            verifyStableThread(prefs, context, bodyId);
            TextView post = onMain(() -> {
                TextView view = (TextView) type("reddit.news.listings.common.views.ActiveTextView2")
                        .getConstructor(Context.class, android.util.AttributeSet.class).newInstance(activity, null);
                activity.setContentView((View) rows.getParent());
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
            RelayMediaLinksDeviceTest.STABLE_IMAGE.countDown();
            onMain(() -> { rows.removeAllViews(); return null; });
            restore(prefs, before);
            check(backup.edit().clear().commit(), "Could not clear inline preference backup");
        }
    }

    private static ListView thread(String url, SharedPreferences prefs, Context context) throws Exception {
        return thread(url, prefs, context, 0);
    }

    private static ListView thread(String url, SharedPreferences prefs, Context context, int paddingRows)
            throws Exception {
        Object fragment = type("reddit.news.WebAndCommentsFragment").getConstructor().newInstance();
        Object manager = activity.getClass().getMethod("getSupportFragmentManager").invoke(activity);
        set(fragment, "mHost", field(manager, "mHost"));
        ArrayList<Object> comments = new ArrayList<>();
        for (int i = 0; i <= 2 * paddingRows; i++) {
            comments.add(threadComment(i, i == paddingRows ? url : null));
        }
        var constructor = type("reddit.news.adapters.CommentsAdapter").getConstructors()[0];
        var adapter = (ArrayAdapter<?>) constructor.newInstance(fragment, null, null,
                resource(context, "commentsrow", "layout"),
                comments, prefs, false, null);
        Class<?> master = type("reddit.news.views.MasterDetailView");
        Object detail = master.getConstructor(Context.class, android.util.AttributeSet.class).newInstance(activity, null);
        ListView list = (ListView) type("reddit.news.views.MyCommentsListView")
                .getConstructor(Context.class, android.util.AttributeSet.class).newInstance(activity, null);
        list.getClass().getMethod("setMasterDetailView", master).invoke(list, detail);
        list.setAdapter(adapter);
        activity.setContentView(list);
        return list;
    }

    private static Object threadComment(int index, String url) throws Exception {
        Class<?> commentType = type("reddit.news.oauth.reddit.model.RedditComment");
        Object model = commentType.getConstructor().newInstance();
        String text = "Fixture comment " + index;
        set(model, "body", url == null ? text : url);
        set(model, "bodyHtml", "<div class=\"md\"><p>" + (url == null ? text : link(url, url)) + "</p></div>");
        set(model, "author", "fixture");
        set(model, "id", "fixture" + index);
        set(model, "name", "t1_fixture" + index);
        set(model, "subreddit", "fixture");
        set(model, "edited", 0L);
        set(model, "linkId", "t3_fixture");
        set(model, "parentId", "t3_fixture");
        return type("reddit.news.data.DataComment").getConstructor(commentType, int.class).newInstance(model, 0);
    }

    private static void verifyStableThread(SharedPreferences prefs, Context context, int bodyId) throws Exception {
        int imageRow = 18, offset = 80;
        ListView list = onMain(() -> {
            ListView thread = thread(URL + "stable.jpeg" + QUERY, prefs, context, imageRow);
            // Start below the image, then scroll back up to its cold preview.
            thread.setSelectionFromTop(imageRow + 4, offset);
            return thread;
        });
        await(() -> onMain(() -> list.getFirstVisiblePosition() > imageRow), "Thread did not scroll past the image");
        onMain(() -> { list.setSelectionFromTop(imageRow, offset); return null; });
        await(() -> onMain(() -> {
            RowLayout row = rowLayout(list, bodyId, imageRow);
            return row != null && !previews(row.body()).isEmpty()
                    && (Boolean) field(previews(row.body()).get(0), "active");
        }), "Scrolling back up did not start the delayed image");
        RowLayout before = onMain(() -> rowLayout(list, bodyId, imageRow));
        check(before != null && onMain(() -> loaded(before.body()) == 0), "Delayed image was already decoded");
        RelayMediaLinksDeviceTest.STABLE_IMAGE.countDown();
        await(() -> onMain(() -> {
            RowLayout after = rowLayout(list, bodyId, imageRow);
            return after != null && after.height() < before.height() && loaded(after.body()) == 1;
        }), "A loaded landscape image did not remove its extra space");
        RowLayout fitted = onMain(() -> {
            RowLayout after = rowLayout(list, bodyId, imageRow);
            check(after.top() == before.top(), "Fitting a landscape preview moved the thread anchor");
            Object preview = previews(after.body()).get(0);
            Drawable drawable = (Drawable) field(preview, "drawable");
            Rect bounds = previewBounds(after.body(), preview);
            check(bounds.width() > 0 && Math.abs(bounds.width() - 2 * bounds.height()) <= 2
                            && bounds.height() == drawable.getBounds().height(),
                    "The landscape preview line was taller than the actual image");
            return after;
        });
        int[] rowTaps = {0};
        onMain(() -> {
            list.setOnItemClickListener((parent, row, position, id) -> {
                if (position == imageRow) rowTaps[0]++;
            });
            Rect bounds = previewBounds(fitted.body(), previews(fitted.body()).get(0));
            list.offsetDescendantRectToMyCoords(fitted.body(), bounds);
            tap(list, bounds.left + bounds.width() / 8, bounds.centerY());
            return null;
        });
        await(() -> onMain(() -> rowTaps[0] == 1), "Tapping the image edge did not reach the comment row");

        ListView rebound = onMain(() -> {
            ListView result = thread(URL + "stable.jpeg" + QUERY, prefs, context, imageRow);
            result.setSelectionFromTop(imageRow, offset);
            return result;
        });
        await(() -> onMain(() -> rowLayout(rebound, bodyId, imageRow) != null), "Image row was not rebound");
        RowLayout reboundRow = onMain(() -> rowLayout(rebound, bodyId, imageRow));
        check(reboundRow.height() == fitted.height() && reboundRow.top() == fitted.top(),
                "A rebound image forgot its fitted dimensions");
    }

    private static Rect previewBounds(TextView view, Object preview) throws Exception {
        var layout = view.getLayout();
        var text = (Spanned) view.getText();
        int line = layout.getLineForOffset(text.getSpanStart(preview));
        int left = view.getTotalPaddingLeft() + (int) layout.getLineLeft(line);
        int top = view.getTotalPaddingTop() + layout.getLineTop(line);
        int bottom = view.getTotalPaddingTop() + layout.getLineBaseline(line);
        Drawable drawable = (Drawable) field(preview, "drawable");
        return new Rect(left, top, left + drawable.getBounds().width(), bottom);
    }

    private static void tap(View view, float x, float y) {
        long down = SystemClock.uptimeMillis();
        touch(view, down, MotionEvent.ACTION_DOWN, x, y);
        touch(view, down, MotionEvent.ACTION_UP, x, y);
    }

    private static void touch(View view, long down, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0);
        try {
            view.dispatchTouchEvent(event);
        } finally {
            event.recycle();
        }
    }

    private static RowLayout rowLayout(ListView list, int bodyId, int position) {
        int index = position - list.getFirstVisiblePosition();
        if (index < 0 || index >= list.getChildCount()) return null;
        View row = list.getChildAt(index);
        TextView body = row.findViewById(bodyId);
        return body == null || body.getHeight() == 0 ? null
                : new RowLayout(body, row.getHeight(), row.getTop());
    }

    private static String describeBody(TextView body) throws Exception {
        if (body == null) return "no body";
        Rect bounds = new Rect();
        boolean shown = body.getLocalVisibleRect(bounds);
        List<Object> all = previews(body);
        return "length=" + body.length() + " size=" + body.getWidth() + "x" + body.getHeight()
                + " layout=" + (body.getLayout() != null) + " shown=" + shown + " visible=" + bounds
                + " previews=" + all.size() + (all.isEmpty() ? "" :
                " active=" + field(all.get(0), "active") + " failed=" + field(all.get(0), "failed"));
    }

    private static int resource(Context context, String name, String kind) {
        int id = context.getResources().getIdentifier(name, kind, context.getPackageName());
        if (id == 0) id = context.getResources().getIdentifier(name, kind, "free.reddit.news");
        if (id == 0) id = context.getResources().getIdentifier(name, kind, "reddit.news");
        check(id != 0, "Missing Relay resource " + kind + "/" + name);
        return id;
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
        bindModel(model);
    }
    private static void bindModel(Object model) throws Exception {
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
