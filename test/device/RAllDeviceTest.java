/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.patcheddit.fixture;

import android.content.Context;
import android.os.Bundle;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.List;

public final class RAllDeviceTest {
    private static final String PREFIX = "app.morphe.extension.shared.http.libraries.okhttp3.";
    private static final String PATCH = "app.morphe.extension.shared.fixes.feed.RAllPatch";

    public static void run(Context context, Bundle arguments) throws Exception {
        ClassLoader loader = context.getClassLoader();
        Class<?> builder = loader.loadClass(PREFIX + "OkHttpClient$Builder");
        Class<?> interceptor = loader.loadClass(PREFIX + "Interceptor");
        Class<?> chain = loader.loadClass(PREFIX + "Interceptor$Chain");
        Class<?> request = loader.loadClass(PREFIX + "Request");
        Class<?> response = loader.loadClass(PREFIX + "Response");
        Object original = loader.loadClass(PREFIX + "OkHttpClient").getConstructor().newInstance();
        var hooks = (List<?>) original.getClass().getMethod("interceptors").invoke(original);
        check(hooks.stream().filter(hook -> hook.getClass().getName().equals(PATCH)).count() == 1,
                "The patched HTTP client did not register exactly one r/all interceptor: " + hooks);
        Object clientBuilder = original.getClass().getMethod("newBuilder").invoke(original);
        var received = new java.util.ArrayList<String>();
        Class<?> responseBuilder = loader.loadClass(PREFIX + "Response$Builder");
        Class<?> protocol = loader.loadClass(PREFIX + "Protocol");
        Class<?> body = loader.loadClass(PREFIX + "ResponseBody");
        Class<?> mediaType = loader.loadClass(PREFIX + "MediaType");
        Object mime = mediaType.getMethod("get", String.class).invoke(null, "application/json");
        Object transport = Proxy.newProxyInstance(loader, new Class<?>[]{interceptor}, (proxy, method, args) -> {
            if (!method.getName().equals("intercept")) throw new UnsupportedOperationException(method.getName());
            Object incoming = chain.getMethod("request").invoke(args[0]);
            received.add(request.getMethod("url").invoke(incoming).toString());
            check("test-header".equals(request.getMethod("header", String.class).invoke(incoming, "X-Fixture")),
                    "Lost the request headers");
            Object output = responseBuilder.getConstructor().newInstance();
            responseBuilder.getMethod("request", request).invoke(output, incoming);
            responseBuilder.getMethod("protocol", protocol).invoke(output, protocol.getField("HTTP_1_1").get(null));
            responseBuilder.getMethod("code", int.class).invoke(output, 200);
            responseBuilder.getMethod("message", String.class).invoke(output, "OK");
            responseBuilder.getMethod("body", body).invoke(output, body.getMethod("create", String.class, mediaType)
                    .invoke(null, "{\"kind\":\"Listing\",\"data\":{\"children\":[]}}", mime));
            return responseBuilder.getMethod("build").invoke(output);
        });
        builder.getMethod("addInterceptor", interceptor).invoke(clientBuilder, transport);
        Object client = builder.getMethod("build").invoke(clientBuilder);
        Class<?> requestBuilder = loader.loadClass(PREFIX + "Request$Builder");
        for (String url : new String[]{"https://oauth.reddit.com/r/all/top/?after=t3_page&t=week",
                "https://oauth.reddit.com/r/all/.json", "https://oauth.reddit.com/r/allthings/hot",
                "https://oauth.reddit.com/r/all/comments/abc/example", "https://example.com/r/all"}) {
            Object input = requestBuilder.getConstructor().newInstance();
            requestBuilder.getMethod("url", String.class).invoke(input, url);
            requestBuilder.getMethod("header", String.class, String.class).invoke(input, "X-Fixture", "test-header");
            Object call = client.getClass().getMethod("newCall", request)
                    .invoke(client, requestBuilder.getMethod("build").invoke(input));
            try (var result = (AutoCloseable) loader.loadClass(PREFIX + "Call").getMethod("execute").invoke(call)) {
                check((Integer) response.getMethod("code").invoke(result) == 200, "Changed the listing status");
            }
        }
        check(received.equals(List.of("https://oauth.reddit.com/r/all/top/.json?after=t3_page&t=week",
                "https://oauth.reddit.com/r/all/.json", "https://oauth.reddit.com/r/allthings/hot",
                "https://oauth.reddit.com/r/all/comments/abc/example", "https://example.com/r/all")),
                "Unexpected routes: " + received);

        if ("baconreader".equals(arguments.getString("stock"))) verifyApache(context, loader);
    }

    private static void verifyApache(Context context, ClassLoader loader) throws Exception {
        var rest = loader.loadClass("com.onelouder.baconreader.connectivity.RestClient");
        var uriRequest = loader.loadClass("org.apache.http.client.methods.HttpUriRequest");
        var get = loader.loadClass("org.apache.http.client.methods.HttpGet");
        var execute = rest.getDeclaredMethod("executeRequest", Context.class, uriRequest);
        execute.setAccessible(true);
        var urlField = rest.getDeclaredField("url");
        urlField.setAccessible(true);
        Object instance = rest.getConstructor().newInstance();
        String url = "https://oauth.reddit.com/r/all/new?after=t3_page";
        urlField.set(instance, url);
        Object nativeGet = get.getConstructor(String.class).newInstance(url);
        // The native worker reaches the real pre-request hook but the aborted request cannot hit Reddit.
        get.getMethod("abort").invoke(nativeGet);
        try {
            execute.invoke(instance, context, nativeGet);
            throw new AssertionError("An aborted Apache request was accepted");
        } catch (InvocationTargetException failure) {
            if (!(failure.getCause() instanceof java.io.IOException aborted)
                    || !"Request already aborted".equals(aborted.getMessage())) throw failure;
        }
        check(URI.create("https://oauth.reddit.com/r/all/new/.json?after=t3_page")
                .equals(get.getMethod("getURI").invoke(nativeGet)), "BaconReader's Apache request was not rewritten");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
