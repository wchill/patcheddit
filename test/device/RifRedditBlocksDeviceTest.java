/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.patcheddit.fixture;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.FutureTask;

public final class RifRedditBlocksDeviceTest {
    private static final String HTTP = "app.morphe.extension.shared.http.libraries.okhttp3.";
    private static final String HTML = "<!doctype html><html><title>Blocked</title>"
            + "<body>You've been blocked by network security.</body></html>";
    private static final Uri URI = Uri.parse("https://oauth.reddit.com/r/test/.json");

    public static void run(Context context, Bundle arguments) throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> {
            run(context);
            return null;
        });
        PatchedditInstrumentation.instance.runOnMainSync(task);
        task.get();
    }

    private static void run(Context context) throws Exception {
        ClassLoader classes = context.getClassLoader();
        boolean paid = context.getPackageName().endsWith("donation");
        String prefix = paid ? "i3." : "h3.";
        Class<?> hook = classes.loadClass("app.morphe.extension.redditisfun.feed.RedditBlocks");
        Method take = hook.getDeclaredMethod("take", Object.class);
        take.setAccessible(true);
        Method show = hook.getMethod("show", Object.class, Context.class);
        Class<?> feedType = classes.loadClass(paid ? "c5.y0" : "b5.y0");
        Class<?> loaderType = classes.loadClass(prefix + "a");
        Class<?> workerType = classes.loadClass(prefix + "a$a");
        Constructor<?> workerConstructor = workerType.getDeclaredConstructor(Uri.class, Context.class,
                boolean.class, loaderType);
        workerConstructor.setAccessible(true);
        Field current = loaderType.getDeclaredField("v");
        current.setAccessible(true);
        Field reason = feedType.getDeclaredField("y");
        reason.setAccessible(true);
        Activity activity = new FixtureActivity(context);

        Object feed = feedType.getConstructor(Activity.class, Uri.class).newInstance(activity, URI);
        Object worker = workerConstructor.newInstance(URI, context, false, feed);
        current.set(feed, worker);
        check(loaderType.getMethod("morpheRequest").invoke(feed) == worker, "Wrong native current-request bridge");
        Method relevant = workerType.getMethod("morpheIsFeed");
        relevant.setAccessible(true);
        check(Boolean.TRUE.equals(relevant.invoke(worker)), "Native feed worker was not recognized");
        error(classes, prefix, worker, HTML, 403, true);
        check(Boolean.FALSE.equals(show.invoke(null, feed, null)), "Missing context consumed the native fallback");
        check(Boolean.TRUE.equals(take.invoke(null, feed)), "Signed-in native 403 was not classified");
        check(Boolean.FALSE.equals(take.invoke(null, feed)), "One request was classified twice");

        error(classes, prefix, worker, HTML, 403, false);
        check(Boolean.FALSE.equals(take.invoke(null, feed)), "Guest HTML was reclassified");
        for (String value : new String[]{"private", "quarantined", "banned", "gated"}) {
            error(classes, prefix, worker, "{\"reason\":\"" + value + "\"}", 403, true);
            check(value.equals(reason.get(feed)), "Native structured error reason was changed: " + value);
            check(Boolean.FALSE.equals(take.invoke(null, feed)), "Structured reason was replaced: " + value);
        }
        error(classes, prefix, worker, "{\"message\":\"You've been blocked by network security\"}", 403, true);
        check(Boolean.FALSE.equals(take.invoke(null, feed)), "JSON wording was mistaken for HTML");
        error(classes, prefix, worker, "<html><title>Maintenance</title></html>", 403, true);
        check(Boolean.FALSE.equals(take.invoke(null, feed)), "Unrelated HTML was classified");
        error(classes, prefix, worker, HTML, 404, true);
        check(Boolean.FALSE.equals(take.invoke(null, feed)), "Non-403 HTML was classified");

        Object other = feedType.getConstructor(Activity.class, Uri.class).newInstance(activity, URI);
        Object otherWorker = workerConstructor.newInstance(URI, context, false, other);
        current.set(other, otherWorker);
        error(classes, prefix, worker, HTML, 403, true);
        error(classes, prefix, otherWorker, "{\"reason\":\"private\"}", 403, true);
        check(Boolean.FALSE.equals(take.invoke(null, other)), "Concurrent loader stole a block classification");
        current.set(feed, otherWorker);
        check(Boolean.FALSE.equals(take.invoke(null, feed)), "A replacement request inherited a stale block");
        current.set(feed, worker);
        check(Boolean.TRUE.equals(take.invoke(null, feed)), "Out-of-order loader lost its own finding");
        error(classes, prefix, worker, HTML, 403, true);
        error(classes, prefix, worker, "{\"reason\":\"private\"}", 403, true);
        check(Boolean.FALSE.equals(take.invoke(null, feed)), "Retry retained stale classification");

        Class<?> mailType = classes.loadClass("com.andrewshu.android.reddit.mail.newmodmail.l");
        Object mail = mailType.getConstructor(Activity.class, Uri.class).newInstance(activity, URI);
        Object mailWorker = workerConstructor.newInstance(URI, context, false, mail);
        current.set(mail, mailWorker);
        check(Boolean.FALSE.equals(relevant.invoke(mailWorker)), "Non-feed worker was treated as a feed");
        error(classes, prefix, mailWorker, HTML, 403, true);
        check(Boolean.FALSE.equals(take.invoke(null, mail)), "Inbox failure gained a feed explanation");

        error(classes, prefix, worker, HTML, 403, true);
        check(Boolean.TRUE.equals(show.invoke(null, feed, context)), "Localized native block toast was not shown");
        check(Boolean.FALSE.equals(show.invoke(null, feed, context)), "Generic error would be suppressed twice");
        int message = context.getResources().getIdentifier("patcheddit_reddit_blocked", "string", context.getPackageName());
        check(message != 0 && context.getString(message).contains("Reddit"), "Missing localized block explanation");
    }

    private static void error(ClassLoader classes, String prefix, Object worker, String text, int code,
                              boolean signedIn) throws Exception {
        Class<?> requestType = classes.loadClass(HTTP + "Request");
        Class<?> requestBuilderType = classes.loadClass(HTTP + "Request$Builder");
        Object requestBuilder = requestBuilderType.getConstructor().newInstance();
        requestBuilderType.getMethod("url", String.class).invoke(requestBuilder, URI.toString());
        Object request = requestBuilderType.getMethod("build").invoke(requestBuilder);
        Class<?> bodyType = classes.loadClass(HTTP + "ResponseBody");
        Class<?> mediaType = classes.loadClass(HTTP + "MediaType");
        Object body = bodyType.getMethod("create", mediaType, String.class).invoke(null, null, text);
        Class<?> responseType = classes.loadClass(HTTP + "Response");
        Class<?> builderType = classes.loadClass(HTTP + "Response$Builder");
        Class<?> protocolType = classes.loadClass(HTTP + "Protocol");
        Object builder = builderType.getConstructor().newInstance();
        builderType.getMethod("request", requestType).invoke(builder, request);
        builderType.getMethod("protocol", protocolType).invoke(builder, protocolType.getField("HTTP_1_1").get(null));
        builderType.getMethod("code", int.class).invoke(builder, code);
        builderType.getMethod("message", String.class).invoke(builder, "Fixture error");
        builderType.getMethod("body", bodyType).invoke(builder, body);
        Object response = builderType.getMethod("build").invoke(builder);
        Class<?> workerBase = classes.loadClass(prefix + "c");
        Field gatedRetry = workerBase.getDeclaredField("p");
        gatedRetry.setAccessible(true);
        gatedRetry.setBoolean(worker, true);
        Method error = workerBase.getDeclaredMethod("Y", responseType, bodyType,
                boolean.class, String.class);
        error.setAccessible(true);
        try {
            error.invoke(worker, response, body, signedIn, null);
            throw new AssertionError("Native error handler unexpectedly succeeded");
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (!(cause instanceof IOException) && !cause.getClass().getName().equals(prefix + "f")
                    && !cause.getClass().getName().equals(prefix.equals("h3.") ? "u2.a" : "v2.a")) throw failure;
        } finally {
            ((Closeable) body).close();
        }
    }

    private static final class FixtureActivity extends Activity {
        FixtureActivity(Context context) { attachBaseContext(context); }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
