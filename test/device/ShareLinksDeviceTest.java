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
import android.net.Uri;
import android.os.Bundle;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLStreamHandler;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class ShareLinksDeviceTest {
    private static final String ROOT = "https://www.reddit.com/r/Example/";
    private static final String POST = ROOT + "comments/abc123/example/";
    private static final String COMMENT = POST + "def456/";
    private static final String BACON = "com.onelouder.baconreader.LinkDispatcher";

    public static void run(Context context, Bundle arguments) throws Exception {
        if (!context.getPackageName().startsWith("app.patcheddit.rollout.")) {
            throw new AssertionError("This fixture must replace HTTPS transport only inside a disposable app");
        }
        String flavor = arguments.getString("stock");
        String activityName = switch (flavor) {
            case "baconreader" -> BACON;
            case "joey" -> "o.o.joey.Activities.OpenInApp";
            case "rif", "platinum" -> "com.andrewshu.android.reddit.MainActivity";
            default -> throw new AssertionError("Unknown target: " + flavor);
        };
        var requests = new LinkedBlockingQueue<String>();
        var count = new AtomicInteger();
        URL.setURLStreamHandlerFactory(protocol -> !"https".equals(protocol) ? null : new URLStreamHandler() {
            @Override protected HttpURLConnection openConnection(URL url) throws IOException {
                String path = url.getPath();
                if (!"www.reddit.com".equals(url.getHost()) || !path.startsWith("/r/Example/s/Fixture")) {
                    throw new IOException("Not an offline fixture URL");
                }
                return new HttpURLConnection(url) {
                    @Override public int getResponseCode() {
                        check("HEAD".equals(getRequestMethod()) && !getInstanceFollowRedirects(),
                                "Unexpected share redirect request");
                        count.incrementAndGet();
                        requests.add(url.toString());
                        return path.endsWith("Missing") ? 404 : 302;
                    }
                    @Override public String getHeaderField(String header) {
                        if (!"Location".equalsIgnoreCase(header)) return null;
                        return (path.endsWith("Comment") ? COMMENT : POST) + "?tracking=discarded";
                    }
                    @Override public void connect() {}
                    @Override public void disconnect() {}
                    @Override public boolean usingProxy() { return false; }
                };
            }
        });

        var instrumentation = PatchedditInstrumentation.instance;
        var replayed = new LinkedBlockingQueue<Intent>();
        var monitor = new Instrumentation.ActivityMonitor() {
            @Override public Instrumentation.ActivityResult onStartActivity(Intent intent) {
                String data = intent.getDataString();
                if (data != null && (data.startsWith(POST) || data.endsWith("#morphe-share-fallback"))) {
                    replayed.add(new Intent(intent));
                    return new Instrumentation.ActivityResult(Activity.RESULT_OK, null);
                }
                return null;
            }
        };
        instrumentation.addMonitor(monitor);
        try {
            for (String suffix : new String[]{"Post", "Comment", "Missing"}) {
                String share = ROOT + "s/Fixture" + suffix;
                Intent input = new Intent(Intent.ACTION_VIEW, Uri.parse(share + "?tracking=ignored"))
                        .setClassName(context.getPackageName(), activityName);
                if ("baconreader".equals(flavor) && "Comment".equals(suffix)) {
                    Class<?> type = context.getClassLoader().loadClass("com.onelouder.baconreader.LinkProcessor");
                    Object processor = type.getConstructor(Context.class, String.class).newInstance(context, share);
                    input = (Intent) type.getMethod("getIntent").invoke(processor);
                    check(activityName.equals(input.getComponent().getClassName()), "In-app link bypassed the dispatcher");
                }
                Activity original = null;
                if ("rif".equals(flavor) || "platinum".equals(flavor)) {
                    // The renamed RIF APK still points at the stock app's unexported provider
                    // during startup. Exercise its native in-app router without starting the stock app.
                    openRifInApp(context, flavor, input.getDataString());
                } else {
                    original = instrumentation.startActivitySync(input.addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
                }
                try {
                    check(share.equals(requests.poll(8, TimeUnit.SECONDS)), "Share request was not sent: " + suffix);
                    Intent replay = replayed.poll(8, TimeUnit.SECONDS);
                    check(replay != null && replay.getComponent() != null
                                    && context.getPackageName().equals(replay.getComponent().getPackageName())
                                    && activityName.equals(replay.getComponent().getClassName()),
                            "Link was not replayed through the app's native handler: " + suffix);
                    String expected = switch (suffix) {
                        case "Post" -> POST;
                        case "Comment" -> COMMENT;
                        default -> share + "?tracking=ignored#morphe-share-fallback";
                    };
                    check(expected.equals(replay.getDataString()), "Wrong native replay: " + replay.getDataString());
                    if ("Missing".equals(suffix) && "baconreader".equals(flavor)) {
                        check(replay.getBooleanExtra("EXTRA_FORCE_BROWSER", false), "BaconReader lost its browser fallback");
                    }
                } finally {
                    if (original != null) instrumentation.runOnMainSync(original::finish);
                }
            }
            check(count.get() == 3, "A fixture link was resolved more than once");
        } finally {
            instrumentation.removeMonitor(monitor);
        }
    }

    private static void openRifInApp(Context context, String flavor, String link) throws Exception {
        Class<?> owner = context.getClassLoader().loadClass("rif".equals(flavor) ? "l3.g" : "m3.g");
        for (Method method : owner.getDeclaredMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (!"q".equals(method.getName()) || parameters.length != 10
                    || parameters[0] != String.class || parameters[8] != Context.class) continue;
            method.setAccessible(true);
            Object[] args = new Object[10];
            args[0] = link;
            args[5] = false;
            args[8] = context;
            method.invoke(null, args);
            return;
        }
        throw new AssertionError("Missing native RIF link router");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
