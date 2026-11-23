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
import android.graphics.RectF;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ReplacementSpan;
import android.text.style.URLSpan;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public final class RifInlineMediaDeviceTest {
    private static final String PREFIX = "app.morphe.extension.redditisfun.media.";
    private static final String HTTP = "app.morphe.extension.shared.http.libraries.okhttp3.";
    private static final String IMAGE = "https://i.imgur.com/fixture.png";
    private static final String VIDEO = "https://i.imgur.com/fixture.mp4";
    private static ClassLoader loader;
    private static Activity activity;
    private static LinearLayout rows;
    private static SharedPreferences preferences;
    private static final Map<String, byte[]> PAYLOADS = new ConcurrentHashMap<>();
    private static final Map<String, Integer> REQUESTS = new ConcurrentHashMap<>();
    private static final Map<String, Integer> STATUS = new ConcurrentHashMap<>();
    private static final CountDownLatch SLOW = new CountDownLatch(1);
    private static int opens, selections;

    public static void run(Context context, Bundle arguments) throws Exception {
        loader = context.getClassLoader();
        preferences = (SharedPreferences) loader.loadClass("app.morphe.extension.shared.settings.PatchSettings")
                .getMethod("preferences", Context.class).invoke(null, context);
        Map<String, ?> before = new HashMap<>(preferences.getAll());
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        activity = PatchedditInstrumentation.instance.startActivitySync(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            set("morphe_patch_inline_media", true);
            set("morphe_inline_autoplay", false);
            check(preferences.edit().remove("morphe_inline_keep_urls").commit(), "Could not reset URL visibility default");
            onMain(() -> {
                ScrollView scroll = new ScrollView(activity);
                rows = new LinearLayout(activity);
                rows.setOrientation(LinearLayout.VERTICAL);
                scroll.addView(rows);
                activity.setContentView(scroll);
                return null;
            });
            transport(context);
            videoRowDetach();
            contracts();
            nativeBodies();
            linksAndLayout();
            asynchronousBodies();
            failures();
            albums();
            video();
            settings(context);
            System.out.println("INL: rendering, loading, recycling, geometry, animation, album gestures, "
                    + "native video/audio, player limit, handoff, settings and failure fixtures passed");
        } finally {
            SLOW.countDown();
            onMain(() -> { rows.removeAllViews(); activity.finish(); return null; });
            SharedPreferences.Editor restore = preferences.edit();
            for (String key : preferences.getAll().keySet()) {
                if (!key.startsWith("morphe_inline_") && !key.equals("morphe_patch_inline_media")) continue;
                Object value = before.get(key);
                if (value == null) restore.remove(key);
                else if (value instanceof Boolean) restore.putBoolean(key, (Boolean) value);
                else restore.putInt(key, (Integer) value);
            }
            check(restore.commit(), "Could not restore inline-media preferences");
        }
    }

    private static void transport(Context context) throws Exception {
        byte[] wide = png(80, 40, Color.RED), tall = png(40, 80, Color.BLUE);
        PAYLOADS.put(IMAGE, wide);
        PAYLOADS.put("https://i.imgur.com/tall.png", tall);
        PAYLOADS.put("https://i.imgur.com/corrupt.png", new byte[]{1, 2, 3});
        PAYLOADS.put("https://i.imgur.com/slow.png", wide);
        PAYLOADS.put("https://i.imgur.com/fixtureh.jpg", wide);
        PAYLOADS.put("https://i.imgur.com/animated.gif", asset("inline-animation.gif"));
        PAYLOADS.put(VIDEO, asset("inline-video.mp4"));
        PAYLOADS.put("https://v.redd.it/fixture/DASH_240.mp4", PAYLOADS.get(VIDEO));
        PAYLOADS.put("https://v.redd.it/fixture/audio.mp4", asset("inline-audio.m4a"));
        PAYLOADS.put("https://v.redd.it/fixture/DASHPlaylist.mpd", dash().getBytes(StandardCharsets.UTF_8));
        PAYLOADS.put("https://api.imgur.com/3/album/fixture", ("{\"success\":true,\"data\":{\"images\":["
                + "{\"id\":\"fixture\",\"link\":\"" + IMAGE + "\"},"
                + "{\"id\":\"tall\",\"link\":\"https://i.imgur.com/tall.png\"}]}}").getBytes(StandardCharsets.UTF_8));
        Object client = callStatic("MediaLoader", "client", new Class<?>[]{Context.class}, context);
        Object builder = client.getClass().getMethod("newBuilder").invoke(client);
        Class<?> interceptor = loader.loadClass(HTTP + "Interceptor");
        Object fake = Proxy.newProxyInstance(loader, new Class<?>[]{interceptor}, (proxy, method, args) -> {
            if (method.getName().equals("toString")) return "inline fixture transport";
            if (!method.getName().equals("intercept")) throw new AssertionError(method);
            Object chain = args[0];
            Method requestMethod = chain.getClass().getMethod("request");
            Object request = requestMethod.invoke(chain);
            String url = request.getClass().getMethod("url").invoke(request).toString();
            String authorization = (String) request.getClass().getMethod("header", String.class).invoke(request, "Authorization");
            check(url.startsWith("https://api.imgur.com/") ? authorization != null && authorization.startsWith("Client-ID ")
                    : authorization == null, "Credentials escaped the Imgur API boundary");
            REQUESTS.merge(url, 1, Integer::sum);
            if (url.contains("slow")) check(SLOW.await(15, TimeUnit.SECONDS), "Slow fixture was not released");
            byte[] bytes = PAYLOADS.get(url);
            int status = STATUS.getOrDefault(url, bytes == null ? 404 : 200);
            if (bytes == null) bytes = new byte[0];
            String range = (String) request.getClass().getMethod("header", String.class).invoke(request, "Range");
            String contentRange = null;
            if (range != null && bytes.length > 0) {
                String[] limits = range.substring(6).split("-", -1);
                int start = Integer.parseInt(limits[0]);
                int end = limits[1].isEmpty() ? bytes.length - 1 : Math.min(bytes.length - 1, Integer.parseInt(limits[1]));
                contentRange = "bytes " + start + "-" + end + "/" + bytes.length;
                bytes = Arrays.copyOfRange(bytes, start, end + 1);
                status = 206;
            }
            Class<?> mediaType = loader.loadClass(HTTP + "MediaType");
            Object body = loader.loadClass(HTTP + "ResponseBody").getMethod("create", byte[].class, mediaType)
                    .invoke(null, bytes, null);
            Class<?> responseBuilder = loader.loadClass(HTTP + "Response$Builder");
            Object response = responseBuilder.getConstructor().newInstance();
            responseBuilder.getMethod("request", loader.loadClass(HTTP + "Request")).invoke(response, request);
            responseBuilder.getMethod("protocol", loader.loadClass(HTTP + "Protocol"))
                    .invoke(response, loader.loadClass(HTTP + "Protocol").getField("HTTP_1_1").get(null));
            responseBuilder.getMethod("code", int.class).invoke(response, status);
            responseBuilder.getMethod("message", String.class).invoke(response, "fixture");
            responseBuilder.getMethod("body", loader.loadClass(HTTP + "ResponseBody")).invoke(response, body);
            responseBuilder.getMethod("header", String.class, String.class).invoke(response, "Content-Length", "" + bytes.length);
            if (contentRange != null) responseBuilder.getMethod("header", String.class, String.class)
                    .invoke(response, "Content-Range", contentRange);
            return responseBuilder.getMethod("build").invoke(response);
        });
        builder.getClass().getMethod("addInterceptor", interceptor).invoke(builder, fake);
        Field field = loader.loadClass(PREFIX + "MediaLoader").getDeclaredField("client");
        field.setAccessible(true);
        field.set(null, builder.getClass().getMethod("build").invoke(builder));
    }

    private static void contracts() throws Exception {
        String wrapped = "http://old.reddit.com/media?url=https%3A%2F%2Fi.redd.it%2Ffixture.png";
        check("https://i.redd.it/fixture.png".equals(callStatic("MediaLoader", "normalize",
                new Class<?>[]{String.class}, wrapped)), "Wrapped Reddit media was not normalized once");
        for (String invalid : new String[]{
                "https://reddit.com/media?url=https%3A%2F%2Fexample.com%2Fimage.png",
                "https://reddit.com/media?url=https%3A%2F%2Freddit.com%2Fmedia%3Furl%3Danything"}) {
            check(callStatic("MediaLoader", "normalize", new Class<?>[]{String.class}, invalid) == null,
                    "Unrelated or recursive wrapper was accepted");
        }
        for (String path : new String[]{"thumbnail/reddit.com/fixture", "download/other.example/fixture"}) {
            check(!(Boolean) callStatic("MediaLoader", "supported", new Class<?>[]{String.class, boolean.class},
                    "https://matrix.redditspace.com/_matrix/media/v3/" + path, true), "Unsupported Matrix route was accepted");
        }
        Object manifest = callStatic("RedditDash", "parse", new Class<?>[]{String.class, String.class},
                "https://v.redd.it/fixture", dash());
        check((Integer) get(get(manifest, "video"), "width") == 720
                        && (Integer) get(get(manifest, "poster"), "width") == 240,
                "DASH did not select the largest track with a short edge at most 480");
        try {
            callStatic("RedditDash", "parse", new Class<?>[]{String.class, String.class},
                    "https://v.redd.it/fixture", dash().replace("DASH_240.mp4", "https://example.com/video.mp4"));
            throw new AssertionError("Off-host DASH destination was accepted");
        } catch (java.lang.reflect.InvocationTargetException expected) {
            check(expected.getCause() instanceof java.io.IOException, "Unexpected DASH rejection");
        }
    }

    private static void nativeBodies() throws Exception {
        List<TextView> bodies = onMain(() -> {
            boolean paid = activity.getPackageName().endsWith("donation");
            String feed = paid ? "f5." : "e5.", comment = paid ? "o2." : "n2.";
            Object manager = Arrays.stream(activity.getClass().getMethods()).filter(method ->
                    method.getParameterCount() == 0 && method.getReturnType().getName()
                            .equals("androidx.fragment.app.FragmentManager")).findFirst().get().invoke(activity);
            Method fragments = Arrays.stream(manager.getClass().getMethods()).filter(method ->
                    method.getParameterCount() == 0 && method.getReturnType() == List.class).findFirst().get();
            Object fragment = ((List<?>) fragments.invoke(manager)).stream().filter(value ->
                    loaderClass(paid ? "c5.r0" : "b5.r0").isInstance(value)).findFirst().get();
            Class<?> thread = loader.loadClass("com.andrewshu.android.reddit.things.objects.ThreadThing");
            Class<?> thing = loader.loadClass("com.andrewshu.android.reddit.things.objects.CommentThing");
            Class<?> json = loader.loadClass("com.bluelinelabs.logansquare.LoganSquare");
            String html = "<p><a href=\"" + IMAGE + "\">" + IMAGE + "</a></p>";
            Object post = json.getMethod("parse", String.class, Class.class).invoke(null,
                    "{\"id\":\"fixture\",\"name\":\"t3_fixture\",\"title\":\"fixture\",\"author\":\"fixture\","
                            + "\"subreddit\":\"test\",\"is_self\":true,\"selftext\":\"" + IMAGE
                            + "\",\"selftext_html\":" + org.json.JSONObject.quote(html) + "}", thread);
            Object reply = json.getMethod("parse", String.class, Class.class).invoke(null,
                    "{\"id\":\"fixture\",\"name\":\"t1_fixture\",\"author\":\"fixture\",\"subreddit\":\"test\","
                            + "\"body\":\"" + IMAGE + "\",\"body_html\":" + org.json.JSONObject.quote(html)
                            + ",\"parent_id\":\"t3_fixture\",\"link_id\":\"t3_fixture\",\"score\":1,\"replies\":\"\"}", thing);
            Spanned rendered = (Spanned) loader.loadClass(paid ? "p5.z" : "o5.z")
                    .getMethod("l", String.class).invoke(null, html);
            thread.getMethod(paid ? "w2" : "v2", CharSequence.class).invoke(post, rendered);
            thing.getMethod(paid ? "d" : "e", android.text.SpannableStringBuilder.class)
                    .invoke(reply, new android.text.SpannableStringBuilder(rendered));
            Object nativePreferences = loader.loadClass(paid ? "l4.v" : "k4.v").getMethod("C").invoke(null);
            Field maxLines = nativePreferences.getClass().getDeclaredField("d0");
            maxLines.setAccessible(true);
            int previous = maxLines.getInt(nativePreferences);
            List<TextView> views = new ArrayList<>();
            try {
                maxLines.setInt(nativePreferences, 5);
                for (String[] target : new String[][]{
                        {feed + "g", feed + "h", "p", "threads_card_item", "feed"},
                        {feed + "u", feed + "z", "o", "thread_op_selftext_layout", "header"},
                        {comment + "c", comment + "d", "e", "comments_card_item", "comment-card"},
                        {comment + "o", comment + "p", "h", "comments_list_item", "comment-list"}}) {
                    int resource = activity.getResources().getIdentifier(target[3], "layout", activity.getPackageName());
                    View root = activity.getLayoutInflater().inflate(resource, rows, false);
                    rows.addView(root);
                    Object holder = loader.loadClass(target[1]).getConstructor(View.class).newInstance(root);
                    java.lang.reflect.Constructor<?> constructor = loader.loadClass(target[0]).getDeclaredConstructor();
                    constructor.setAccessible(true);
                    Object binder = constructor.newInstance();
                    Method bind = Arrays.stream(binder.getClass().getDeclaredMethods()).filter(method ->
                            method.getName().equals(target[2]) && method.getParameterCount() >= 2
                                    && method.getParameterTypes()[0].isInstance(holder)).findFirst().get();
                    Object[] parameters = bind.getParameterCount() == 2 ? new Object[]{holder, post}
                            : new Object[]{holder, target[4].equals("feed") ? post : reply, fragment};
                    bind.invoke(binder, parameters);
                    TextView body = findMedia(root);
                    check(body != null, "Native " + target[4] + " did not use inline media");
                    views.add(body);
                }
            } finally { maxLines.setInt(nativePreferences, previous); }
            return views;
        });
        for (TextView body : bodies) await(() -> decoded(body) == 1, "Native body did not receive its async image");
        onMain(() -> { rows.removeAllViews(); return null; });
    }

    private static Class<?> loaderClass(String name) {
        try { return loader.loadClass(name); }
        catch (ClassNotFoundException failure) { throw new AssertionError(failure); }
    }

    private static TextView findMedia(View view) {
        if (view instanceof TextView && slots((TextView) view).length != 0) return (TextView) view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView body = findMedia(group.getChildAt(i));
                if (body != null) return body;
            }
        }
        return null;
    }

    private static void linksAndLayout() throws Exception {
        check(!(Boolean) callStatic("MediaLoader", "supported", new Class<?>[]{String.class, boolean.class},
                "https://imgur.com.evil.example/a/fixture", true), "Host suffix spoofing was accepted");
        check(callStatic("MediaLoader", "normalize", new Class<?>[]{String.class}, "https://user:pass@i.imgur.com/a.png") == null,
                "URL credentials were accepted");
        String text = IMAGE + " label gif unrelated";
        SpannableString body = new SpannableString(text);
        body.setSpan(new Link(IMAGE), 0, IMAGE.length(), 33);
        body.setSpan(new Link("https://i.imgur.com/tall.png"), IMAGE.length() + 1, IMAGE.length() + 6, 33);
        body.setSpan(new Link("https://i.imgur.com/animated.gif"), IMAGE.length() + 7, IMAGE.length() + 10, 33);
        body.setSpan(new Link("https://example.com/"), text.length() - 9, text.length(), 33);
        TextView view = row(body, 400, false);
        await(() -> decoded(view) == 3, "Images did not fill their placeholders");
        onMain(() -> {
            check(view.getText().toString().contains(IMAGE), "Bare image URL was not kept by default");
            Object decoded = get(firstSlot(view), "decoded");
            set("morphe_inline_keep_urls", false);
            bind(view, body);
            check(view.getText().toString().contains("label") && view.getText().toString().contains("unrelated")
                    && !view.getText().toString().contains(IMAGE), "Link labels were not preserved/replaced correctly");
            check(get(firstSlot(view), "decoded") == decoded, "Hiding the URL reloaded the image");
            return null;
        });
        await(() -> onMain(() -> view.getLayout() != null
                && view.getLayout().getText().toString().equals(view.getText().toString())), "Hidden URL did not reflow");
        onMain(() -> {
            Object[] spans = slots(view);
            Object first = spans[0];
            RectF box = bounds(view, first);
            check(box.width() <= 400 && Math.abs(box.width() / box.height() - 2) < .02, "Wrong image geometry");
            int initial = opens;
            tap(view, box.centerX(), box.centerY());
            check(opens == initial + 1 && selections == 0, "Image tap also selected the comment or opened twice");
            tap(view, box.right + 5, box.centerY());
            check(opens == initial + 1, "Nearby text opened the image");
            return null;
        });
        PatchedditInstrumentation.instance.waitForIdleSync();
        int selectedBeforeHold = onMain(() -> {
            RectF box = bounds(view, firstSlot(view));
            motion(view, MotionEvent.ACTION_DOWN, box.centerX(), box.centerY());
            return selections;
        });
        Thread.sleep(350);
        onMain(() -> {
            RectF box = bounds(view, slots(view)[0]);
            int initial = opens;
            motion(view, MotionEvent.ACTION_UP, box.centerX(), box.centerY());
            check(selections == selectedBeforeHold + 1 && opens == initial, "Image hold did not select exclusively: "
                    + selections + " vs " + selectedBeforeHold + ", opens " + opens + "/" + initial
                    + ", held " + get(get(firstSlot(view), "binding"), "held")
                    + ", cancelled " + get(get(firstSlot(view), "binding"), "cancelled"));
            motion(view, MotionEvent.ACTION_DOWN, box.centerX(), box.centerY());
            motion(view, MotionEvent.ACTION_CANCEL, box.centerX(), box.centerY());
            tap(view, box.centerX(), box.centerY());
            check(opens == initial + 1, "Cancelled hold suppressed the next tap");
            Object animation = get(slots(view)[2], "decoded");
            DrawableCheck.running(get(animation, "drawable"), true);
            Object before = get(firstSlot(view), "decoded");
            bind(view, body);
            check(get(firstSlot(view), "decoded") == before, "Rebinding restarted the same image");
            set("morphe_inline_limit_width", true);
            preferences.edit().putInt("morphe_inline_max_width", 10).commit();
            view.requestLayout();
            return null;
        });
        await(() -> onMain(() -> bounds(view, firstSlot(view)).width() <= 110), "Width percentage was not applied");
        onMain(() -> {
            Object decoded = get(firstSlot(view), "decoded");
            set("morphe_inline_keep_urls", true);
            bind(view, body);
            check(view.getText().toString().contains(IMAGE) && get(firstSlot(view), "decoded") == decoded,
                    "Keeping the URL did not restore it without reloading media");
            rows.removeView(view);
            return null;
        });
        set("morphe_inline_limit_width", false);
    }

    private static void asynchronousBodies() throws Exception {
        long start = SystemClock.elapsedRealtime();
        TextView slow = row(link("https://i.imgur.com/slow.png"), 400, false);
        TextView copy = row(link(IMAGE), 220, true);
        TextView corrupt = row(link("https://i.imgur.com/corrupt.png"), 220, false);
        check(SystemClock.elapsedRealtime() - start < 1000, "Media blocked body binding");
        onMain(() -> { bind(slow, new SpannableString("recycled body")); return null; });
        SLOW.countDown();
        await(() -> decoded(copy) == 1, "Selectable body copy did not update");
        await(() -> onMain(() -> slots(corrupt).length == 0 && corrupt.getText().toString().contains("corrupt.png")),
                "Failed image lost its original link");
        Thread.sleep(200);
        onMain(() -> {
            check(slow.getText().toString().equals("recycled body"), "Old media reached a recycled row");
            check(copy.isTextSelectable(), "Async completion removed native selection");
            set("morphe_patch_inline_media", false);
            bind(copy, link(IMAGE));
            check(slots(copy).length == 0 && copy.getText().toString().equals(IMAGE), "Master switch did not restore native links");
            rows.removeAllViews();
            return null;
        });
        set("morphe_patch_inline_media", true);
        check(REQUESTS.get(IMAGE) == 1, "Warm media was downloaded again");
    }

    @SuppressWarnings("unchecked")
    private static void failures() throws Exception {
        String retry = "https://i.imgur.com/retry.png";
        STATUS.put(retry, 503);
        TextView view = row(link(retry), 320, false);
        await(() -> onMain(() -> view.getText().toString().equals(retry)), "Transient failure lost its link");
        PAYLOADS.put(retry, PAYLOADS.get(IMAGE));
        STATUS.remove(retry);
        onMain(() -> { bind(view, link(retry)); return null; });
        check(REQUESTS.get(retry) == 1, "Transient failure retried before its expiry");
        onMain(() -> {
            ((android.util.LruCache<String, Long>) getStatic("MediaLoader", "FAILURES"))
                    .put("bytes:" + retry, SystemClock.elapsedRealtime() - 121_000);
            Object slot = ((Map<?, ?>) getStatic("InlineMedia", "BINDINGS")).get(view);
            Object media = ((List<?>) get(slot, "slots")).get(0);
            Field failureTime = media.getClass().getDeclaredField("failedAt");
            failureTime.setAccessible(true);
            failureTime.setLong(media, SystemClock.elapsedRealtime() - 121_000);
            bind(view, link(retry));
            return null;
        });
        await(() -> decoded(view) == 1, "Expired transient failure never recovered");
        check(REQUESTS.get(retry) == 2, "Expired failure did not make exactly one new request");
        String large = "https://i.imgur.com/oversized.png";
        PAYLOADS.put(large, new byte[32 * 1024 * 1024 + 1]);
        TextView oversized = row(link(large), 320, false);
        await(() -> onMain(() -> oversized.getText().toString().equals(large)), "Oversized image kept a blank placeholder");
        PAYLOADS.remove(large);
        onMain(() -> { rows.removeAllViews(); return null; });
    }

    private static void albums() throws Exception {
        TextView view = row(link("https://imgur.com/a/fixture"), 320, false);
        await(() -> decoded(view) == 1, "Album did not load");
        RectF initial = onMain(() -> new RectF(bounds(view, firstSlot(view))));
        onMain(() -> { RectF box = bounds(view, firstSlot(view)); tap(view, box.right - 4, box.centerY()); return null; });
        await(() -> onMain(() -> (Integer) get(firstSlot(view), "index") == 1), "Album did not step forward");
        onMain(() -> {
            RectF box = bounds(view, firstSlot(view));
            check(box.equals(initial), "Differently shaped album image reflowed the body");
            tap(view, box.right - 4, box.centerY());
            return null;
        });
        await(() -> onMain(() -> (Integer) get(firstSlot(view), "index") == 0), "Album did not wrap");
        onMain(() -> {
            set("morphe_inline_album_navigation", false);
            RectF box = bounds(view, firstSlot(view));
            int initialOpens = opens;
            tap(view, box.left + 4, box.centerY());
            check(opens == initialOpens + 1 && (Integer) get(firstSlot(view), "index") == 0, "Navigation-off tap did not open the album");
            rows.removeAllViews();
            return null;
        });
        set("morphe_inline_album_navigation", true);
    }

    private static void video() throws Exception {
        TextView view = row(link(VIDEO), 320, false);
        await(() -> onMain(() -> get(firstSlot(view), "video") != null), "Inline video poster was not bound");
        onMain(() -> {
            check(view.getText().toString().contains(VIDEO), "Bare video URL was not kept by default");
            set("morphe_inline_keep_urls", false);
            bind(view, link(VIDEO));
            check(!view.getText().toString().contains(VIDEO), "Bare video URL was not hidden");
            return null;
        });
        await(() -> onMain(() -> view.getLayout() != null
                && view.getLayout().getText().toString().equals(view.getText().toString())), "Hidden video URL did not reflow");
        onMain(() -> {
            Object video = get(firstSlot(view), "video");
            check(get(video, "player") == null, "Autoplay-off allocated a player");
            invoke(video, "tap");
            return null;
        });
        await(() -> onMain(() -> {
            Object player = get(get(firstSlot(view), "video"), "player");
            return player != null && (Integer) nativeCall("state", player) == 3 && (Long) nativeCall("position", player) > 100;
        }), "Native inline MP4 did not play");
        onMain(() -> {
            Object video = get(firstSlot(view), "video"), player = get(video, "player");
            nativeCall("play", player, false);
            invoke(video, "tap");
            check(!(Boolean) nativeCall("playing", player), "Tapping a paused video silently resumed it");
            nativeCall("seek", player, 500L);
            nativeCall("repeat", player, 0);
            invoke(video, "fullscreen");
            Object handoff = getStatic("InlineVideo", "handoff");
            check(handoff != null && (Long) get(handoff, "position") == 500, "Fullscreen lost the inline position");
            callStatic("InlineVideo", "handoff", new Class<?>[]{Object.class, android.net.Uri.class},
                    player, android.net.Uri.parse("https://i.imgur.com/unrelated.mp4"));
            check(getStatic("InlineVideo", "handoff") == handoff, "Unrelated player consumed the handoff");
            callStatic("InlineVideo", "handoff", new Class<?>[]{Object.class, android.net.Uri.class},
                    player, android.net.Uri.parse(VIDEO));
            check(getStatic("InlineVideo", "handoff") == null, "Handoff was not consumed once");
            rows.removeAllViews();
            return null;
        });
        set("morphe_inline_autoplay", true);
        List<TextView> videos = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String url = "https://i.imgur.com/player" + i + ".mp4";
            PAYLOADS.put(url, PAYLOADS.get(VIDEO));
            PAYLOADS.put("https://i.imgur.com/player" + i + "h.jpg", PAYLOADS.get(IMAGE));
            videos.add(row(link(url), 180, false));
        }
        await(() -> onMain(() -> ((java.util.Set<?>) getStatic("InlineVideo", "PLAYERS")).size() == 4), "Four-player cap was not reached");
        onMain(() -> {
            check(((java.util.Set<?>) getStatic("InlineVideo", "PLAYERS")).size() == 4, "Too many native decoders");
            Object first = get(firstSlot(videos.get(0)), "video"), second = get(firstSlot(videos.get(1)), "video");
            invoke(first, "unmute");
            check(!(Boolean) get(first, "muted"), "First player could not acquire audio focus");
            invoke(second, "unmute");
            check((Boolean) get(first, "muted") && !(Boolean) get(second, "muted"), "Two videos own sound");
            invoke(second, "onAudioFocusChange", android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);
            check((Boolean) get(second, "muted"), "Transient focus loss did not mute");
            rows.removeAllViews();
            check(((java.util.Set<?>) getStatic("InlineVideo", "PLAYERS")).isEmpty(), "Detached native players leaked");
            return null;
        });
        TextView reddit = row(link("https://v.redd.it/fixture"), 320, false);
        await(() -> onMain(() -> {
            Object video = get(firstSlot(reddit), "video");
            Object player = video == null ? null : get(video, "player");
            return player != null && (Integer) nativeCall("state", player) == 3 && (Long) nativeCall("position", player) > 100;
        }), "Reddit DASH did not play with the native synchronized audio/video player");
        onMain(() -> { rows.removeAllViews(); return null; });
        set("morphe_inline_keep_urls", true);
    }

    private static void videoRowDetach() throws Exception {
        LinearLayout host = onMain(() -> {
            LinearLayout container = new LinearLayout(activity);
            container.setOrientation(LinearLayout.VERTICAL);
            rows.addView(container);
            return container;
        });
        TextView body = row(host, link("https://v.redd.it/fixture"), 320, false);
        await(() -> onMain(() -> get(firstSlot(body), "video") != null), "Video row did not bind");
        onMain(() -> { invoke(get(firstSlot(body), "video"), "tap"); return null; });
        await(() -> onMain(() -> {
            Object player = get(get(firstSlot(body), "video"), "player");
            return player != null && (Integer) nativeCall("state", player) == 3
                    && (Long) nativeCall("position", player) > 100;
        }), "Video row did not play before detaching");

        onMain(() -> {
            Object video = get(firstSlot(body), "video");
            check(host.getChildCount() == 2, "Video overlay was not attached alongside its body");
            // RecyclerView detaches the containing row, not the body on its own.
            rows.removeView(host);
            check(get(video, "player") == null, "Row detach did not release playback immediately");
            check(!((java.util.Set<?>) getStatic("InlineVideo", "PLAYERS")).contains(video),
                    "Detached player still counts towards the decoder limit");
            rows.addView(host);
            return null;
        });
        await(() -> onMain(() -> host.getChildCount() == 1), "Reattached row retained a stale overlay");
        onMain(() -> { rows.removeView(host); return null; });
    }

    private static void settings(Context context) throws Exception {
        Activity settings = PatchedditInstrumentation.instance.startActivitySync(new Intent()
                .setClassName(context, "app.morphe.extension.shared.settings.MorpheSettingsActivity")
                .putExtra("morphe_screen", "morphe_category_media").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        onMain(() -> {
            android.preference.PreferenceActivity screen = (android.preference.PreferenceActivity) settings;
            check(screen.findPreference("morphe_inline_max_width") != null, "Inline sizing settings were not included");
            check(((android.preference.CheckBoxPreference) screen.findPreference("morphe_inline_keep_urls")).isChecked(),
                    "Keep inline media URLs option was not enabled");
            check("morphe_inline_videos".equals(screen.findPreference("morphe_inline_autoplay").getDependency()),
                    "Autoplay lost its video dependency");
            settings.finish();
            return null;
        });
    }

    private static String dash() {
        StringBuilder text = new StringBuilder("<MPD mediaPresentationDuration=\"PT3S\"><Period>");
        for (int width : new int[]{240, 480, 720, 1080}) text.append("<AdaptationSet mimeType=\"video/mp4\"><Representation width=\"")
                .append(width).append("\" height=\"").append(width * 9 / 16).append("\" bandwidth=\"").append(width * 1000)
                .append("\" codecs=\"avc1.42c00b\"><BaseURL>DASH_240.mp4</BaseURL></Representation></AdaptationSet>");
        return text + "<AdaptationSet mimeType=\"audio/mp4\"><Representation bandwidth=\"24000\" codecs=\"mp4a.40.2\">"
                + "<BaseURL>audio.mp4</BaseURL></Representation></AdaptationSet></Period></MPD>";
    }

    private static byte[] asset(String name) throws Exception {
        try (InputStream input = PatchedditInstrumentation.instance.getContext().getAssets().open(name)) {
            return input.readAllBytes();
        }
    }
    private static byte[] png(int width, int height, int color) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        bitmap.recycle();
        return output.toByteArray();
    }
    private static final class Link extends URLSpan {
        Link(String url) { super(url); }
        @Override public void onClick(View view) { opens++; }
    }
    private static SpannableString link(String url) {
        SpannableString text = new SpannableString(url);
        text.setSpan(new Link(url), 0, text.length(), 33);
        return text;
    }
    private static TextView row(CharSequence text, int width, boolean selectable) throws Exception {
        return row(rows, text, width, selectable);
    }
    private static TextView row(LinearLayout container, CharSequence text, int width, boolean selectable) throws Exception {
        return onMain(() -> {
            TextView view = new TextView(activity);
            view.setTextSize(16);
            view.setTextIsSelectable(selectable);
            view.setOnClickListener(ignored -> selections++);
            container.addView(view, new LinearLayout.LayoutParams(width, -2));
            bind(view, text);
            view.setMovementMethod((android.text.method.MovementMethod) loader.loadClass(
                    activity.getPackageName().endsWith("donation") ? "o2.j0" : "n2.j0")
                    .getMethod("getInstance").invoke(null));
            return view;
        });
    }
    private static void bind(TextView view, CharSequence text) throws Exception {
        callStatic("InlineMedia", "setText", new Class<?>[]{TextView.class, CharSequence.class}, view, text);
    }
    private static Object[] slots(TextView view) {
        CharSequence text = view.getText();
        if (!(text instanceof Spanned)) return new Object[0];
        Object[] spans = ((Spanned) text).getSpans(0, text.length(), ReplacementSpan.class);
        Arrays.sort(spans, java.util.Comparator.comparingInt(((Spanned) text)::getSpanStart));
        return spans;
    }
    private static Object firstSlot(TextView view) { check(slots(view).length > 0, "No media span: " + view.getText()); return slots(view)[0]; }
    private static int decoded(TextView view) throws Exception {
        return onMain(() -> {
            int count = 0;
            for (Object span : slots(view)) if (get(span, "decoded") != null) count++;
            return count;
        });
    }
    private static RectF bounds(TextView view, Object slot) throws Exception {
        check((Boolean) invoke(slot, "bounds", view), "No laid-out media bounds");
        return (RectF) get(slot, "box");
    }
    private static void tap(TextView view, float x, float y) {
        motion(view, MotionEvent.ACTION_DOWN, x, y);
        motion(view, MotionEvent.ACTION_UP, x, y);
    }
    private static void motion(TextView view, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), action, x, y, 0);
        view.dispatchTouchEvent(event);
        event.recycle();
    }
    private static Object callStatic(String type, String name, Class<?>[] parameters, Object... args) throws Exception {
        Method method = loader.loadClass(PREFIX + type).getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method.invoke(null, args);
    }
    private static Object nativeCall(String name, Object... args) throws Exception { return invokeType(loader.loadClass(PREFIX + "NativePlayer"), null, name, args); }
    private static Object invoke(Object target, String name, Object... args) throws Exception { return invokeType(target.getClass(), target, name, args); }
    private static Object invokeType(Class<?> type, Object target, String name, Object... args) throws Exception {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                method.setAccessible(true);
                return method.invoke(target, args);
            }
        }
        throw new NoSuchMethodException(type + "." + name);
    }
    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
    private static Object getStatic(String type, String name) throws Exception {
        Field field = loader.loadClass(PREFIX + type).getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }
    private static void set(String key, boolean value) { check(preferences.edit().putBoolean(key, value).commit(), "Could not set " + key); }
    private static <T> T onMain(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        PatchedditInstrumentation.instance.runOnMainSync(task);
        return task.get(10, TimeUnit.SECONDS);
    }
    private static void await(Callable<Boolean> condition, String message) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 15_000;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition.call()) return;
            Thread.sleep(30);
        }
        throw new AssertionError(message);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static final class DrawableCheck {
        static void running(Object drawable, boolean expected) {
            check(drawable instanceof android.graphics.drawable.Animatable
                    && ((android.graphics.drawable.Animatable) drawable).isRunning() == expected, "GIF animation state is wrong");
        }
    }
}
