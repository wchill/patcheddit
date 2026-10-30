/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

import dalvik.system.PathClassLoader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class HttpHookProbe {
    private static final String LIBRARY = "app.morphe.extension.shared.http.libraries.";
    private static ClassLoader loader;

    public static void main(String[] args) throws Exception {
        loader = new PathClassLoader(args[0], HttpHookProbe.class.getClassLoader());
        String clientId = args[1];
        Class<?> clientType = type("okhttp3.OkHttpClient");
        Class<?> builderType = type("okhttp3.OkHttpClient$Builder");
        Class<?> interceptorType = type("okhttp3.Interceptor");
        Class<?> requestType = type("okhttp3.Request");
        AtomicInteger requests = new AtomicInteger();
        Object interceptor = Proxy.newProxyInstance(loader, new Class<?>[]{interceptorType},
                (proxy, method, parameters) -> {
                    if (method.getName().equals("intercept")) {
                        requests.incrementAndGet();
                        Object request = type("okhttp3.Interceptor$Chain")
                                .getMethod("request").invoke(parameters[0]);
                        return response(request);
                    }
                    if (method.getName().equals("toString")) return "fixture interceptor";
                    if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                    if (method.getName().equals("equals")) return proxy == parameters[0];
                    throw new AssertionError(method);
                });
        Object builder = builderType.getConstructor().newInstance();
        Object client = builderType.getMethod("build").invoke(builder);
        Field installed = Class.forName(
                "app.morphe.extension.shared.requests.BaseOkHttpRequestHook", true, loader)
                .getDeclaredField("INSTANCE");
        installed.setAccessible(true);
        check(installed.get(null).getClass().getName().equals(
                "app.morphe.extension." + clientId + ".http.OkHttpRequestHook"), "wrong hook");
        check(((List<?>) clientType.getMethod("interceptors").invoke(client)).size() == 2,
                "missing shared Reddit interceptors");
        Class<?> info = Class.forName(
                "app.morphe.extension.shared.fixes.api.RedditInfoResponsePatch", true, loader);
        check((Boolean) info.getMethod("isPatchIncluded").invoke(
                info.getConstructor().newInstance()), "Reddit lookup patch is disabled");
        Class<?> moreChildren = Class.forName(
                "app.morphe.extension.shared.fixes.comments.MoreChildrenResponsePatch", true, loader);
        check((Boolean) moreChildren.getMethod("isPatchIncluded").invoke(
                moreChildren.getConstructor().newInstance()), "morechildren patch is disabled");
        builder = clientType.getMethod("newBuilder").invoke(client);
        builderType.getMethod("addInterceptor", interceptorType).invoke(builder, interceptor);
        client = builderType.getMethod("build").invoke(builder);
        check(((List<?>) clientType.getMethod("interceptors").invoke(client)).size() == 3,
                "changed application interceptors");

        Object requestBuilder = type("okhttp3.Request$Builder").getConstructor().newInstance();
        requestBuilder.getClass().getMethod("url", String.class)
                .invoke(requestBuilder, "https://example.invalid/fixture");
        Object request = requestBuilder.getClass().getMethod("build").invoke(requestBuilder);
        Object call = clientType.getMethod("newCall", requestType).invoke(client, request);
        Object response = type("okhttp3.Call").getMethod("execute").invoke(call);
        Object body = type("okhttp3.Response").getMethod("body").invoke(response);
        check(type("okhttp3.ResponseBody").getMethod("string").invoke(body).equals("fixture"),
                "response changed");
        type("okhttp3.Response").getMethod("close").invoke(response);
        check(requests.get() == 1, "request did not pass through the original interceptor");

        requestBuilder = type("okhttp3.Request$Builder").getConstructor().newInstance();
        requestBuilder.getClass().getMethod("url", String.class).invoke(requestBuilder,
                "https://oauth.reddit.com/r/test/api/morechildren.json"
                        + "?link_id=t3_post&children=child&sort=new");
        requestBuilder.getClass().getMethod("header", String.class, String.class)
                .invoke(requestBuilder, "Authorization", "fixture-authorization");
        request = requestBuilder.getClass().getMethod("build").invoke(requestBuilder);
        call = clientType.getMethod("newCall", requestType).invoke(client, request);
        response = type("okhttp3.Call").getMethod("execute").invoke(call);
        body = type("okhttp3.Response").getMethod("body").invoke(response);
        String expanded = (String) type("okhttp3.ResponseBody").getMethod("string").invoke(body);
        JSONArray things = new JSONObject(expanded).getJSONObject("json")
                .getJSONObject("data").getJSONArray("things");
        check(things.length() == 2, "morechildren dropped descendants");
        check(things.getJSONObject(1).getJSONObject("data").getString("parent_id")
                .equals("t1_child"), "morechildren changed the descendant's parent");
        type("okhttp3.Response").getMethod("close").invoke(response);
        check(requests.get() == 2, "morechildren did not use one focused request");

        Object clone = clientType.getMethod("newBuilder").invoke(client);
        Object clonedClient = builderType.getMethod("build").invoke(clone);
        check(((List<?>) clientType.getMethod("interceptors").invoke(clonedClient)).size() == 3,
                "clone duplicated interceptors");
        clientType.getConstructor().newInstance();

        Class<?> suffixType = type("okhttp3.internal.publicsuffix.PublicSuffixDatabase");
        Object companion = suffixType.getField("Companion").get(null);
        Object suffixes = companion.getClass().getMethod("get").invoke(companion);
        check(suffixType.getMethod("getEffectiveTldPlusOne", String.class)
                .invoke(suffixes, "a.example.co.uk").equals("example.co.uk"), "missing suffix database");

        Object buffer = type("okio.Buffer").getConstructor().newInstance();
        buffer.getClass().getMethod("writeUtf8", String.class).invoke(buffer, "okio");
        check(buffer.getClass().getMethod("readUtf8").invoke(buffer).equals("okio"), "incomplete Okio");
        int callers = 0;
        if (args.length > 2) {
            try (BufferedReader input = new BufferedReader(new InputStreamReader(
                    new FileInputStream(args[2]), StandardCharsets.UTF_8))) {
                for (String name; (name = input.readLine()) != null;) {
                    if (name.isEmpty()) continue;
                    Class<?> caller = Class.forName(name, false, loader);
                    caller.getDeclaredMethods();
                    caller.getDeclaredConstructors();
                    callers++;
                }
            }
        }
        System.out.println("PASS " + clientId
                + ": constructor hooks, request/response, morechildren descendants, clone, suffix database, Okio");
        System.out.println("Verified " + callers + " rewritten caller classes");
    }

    private static Class<?> type(String name) throws ClassNotFoundException {
        return Class.forName(LIBRARY + name, true, loader);
    }

    private static Object response(Object request) throws Exception {
        Object url = type("okhttp3.Request").getMethod("url").invoke(request);
        String path = (String) type("okhttp3.HttpUrl").getMethod("encodedPath").invoke(url);
        String content = "fixture";
        if (path.equals("/comments/post/comment/child.json")) {
            check(type("okhttp3.Request").getMethod("header", String.class)
                    .invoke(request, "Authorization").equals("fixture-authorization"),
                    "morechildren did not preserve authorization");
            content = "[{},{\"data\":{\"children\":[{\"kind\":\"t1\",\"data\":{"
                    + "\"id\":\"child\",\"name\":\"t1_child\",\"parent_id\":\"t3_post\","
                    + "\"replies\":{\"data\":{\"children\":[{\"kind\":\"t1\",\"data\":{"
                    + "\"id\":\"grandchild\",\"name\":\"t1_grandchild\","
                    + "\"parent_id\":\"t1_child\",\"replies\":\"\"}}]}}}}]}}]";
        } else {
            check(path.equals("/fixture"), "unexpected transport route: " + path);
        }
        Class<?> mediaType = type("okhttp3.MediaType");
        Object media = mediaType.getMethod("get", String.class).invoke(null, "text/plain");
        Object body = type("okhttp3.ResponseBody").getMethod("create", String.class, mediaType)
                .invoke(null, content, media);
        Class<?> protocolType = type("okhttp3.Protocol");
        Object protocol = protocolType.getField("HTTP_1_1").get(null);
        Class<?> builderType = type("okhttp3.Response$Builder");
        Object builder = builderType.getConstructor().newInstance();
        builderType.getMethod("request", type("okhttp3.Request")).invoke(builder, request);
        builderType.getMethod("protocol", protocolType).invoke(builder, protocol);
        builderType.getMethod("code", int.class).invoke(builder, 200);
        builderType.getMethod("message", String.class).invoke(builder, "OK");
        builderType.getMethod("body", type("okhttp3.ResponseBody")).invoke(builder, body);
        return builderType.getMethod("build").invoke(builder);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
