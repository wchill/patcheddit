/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.patcheddit.fixture;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.json.JSONArray;
import org.json.JSONObject;

public final class BoostHistoryDeviceTest {
    public static void run(Context context, Bundle args) throws Exception {
        check(context.getPackageName().startsWith("app.patcheddit.rollout."),
                "Only replace Boost transport in a disposable app");
        ClassLoader loader = context.getClassLoader();
        Class<?> sessionType = loader.loadClass("xd2");
        onMain(() -> sessionType.getMethod("D").invoke(null));
        Object session = field(sessionType, "r").get(null);
        Object client = field(sessionType, "e").get(session);
        Class<?> reddit = loader.loadClass("net.dean.jraw.RedditClient");
        Class<?> authMethod = loader.loadClass("net.dean.jraw.http.AuthenticationMethod");
        Object guest = authMethod.getField("USERLESS_APP").get(null);
        Class<?> mapper = loader.loadClass("com.fasterxml.jackson.databind.ObjectMapper");
        Object json = mapper.getMethod("readTree", String.class).invoke(mapper.getConstructor().newInstance(),
                "{\"access_token\":\"history-fixture\"}");
        Object auth = loader.loadClass("net.dean.jraw.http.oauth.OAuthData")
                .getConstructor(authMethod, loader.loadClass("com.fasterxml.jackson.databind.JsonNode"))
                .newInstance(guest, json);
        field(reddit, "authData").set(client, auth);
        field(reddit, "authMethod").set(client, guest);
        field(sessionType, "f").set(session, new Date(Long.MAX_VALUE));

        var historyEntered = new CountDownLatch(1);
        var releaseHistory = new CountDownLatch(1);
        Object adapter = reddit.getMethod("getHttpAdapter").invoke(client);
        Field http = field(adapter.getClass(), "http");
        Object nativeClient = http.get(adapter);
        Object builder = call(nativeClient, "newBuilder");
        Class<?> interceptor = loader.loadClass("okhttp3.Interceptor");
        Class<?> chain = loader.loadClass("okhttp3.Interceptor$Chain");
        List<String> batches = java.util.Collections.synchronizedList(new ArrayList<>());
        Object transport = Proxy.newProxyInstance(loader, new Class<?>[]{interceptor}, (proxy, method, values) -> {
            Object request = chain.getMethod("request").invoke(values[0]);
            Object url = call(request, "url");
            String path = (String) call(url, "encodedPath");
            if (path.startsWith("/by_id/")) {
                check(path.split(",").length == 10, "Native History sent more than one page");
                return response(loader, request, 403, "text/html", "blocked");
            }
            if (path.equals("/api/info.json")) {
                String ids = (String) url.getClass().getMethod("queryParameter", String.class).invoke(url, "id");
                batches.add(ids);
                if (batches.size() == 1) {
                    historyEntered.countDown();
                    check(releaseHistory.await(5, TimeUnit.SECONDS), "Other requests waited for History");
                }
                String slug = ids.split(",")[0].substring(3);
                return response(loader, request, 200, "application/json", listing(slug));
            }
            check(path.equals("/r/Android/hot"), "Unexpected native request: " + path);
            return response(loader, request, 200, "application/json", listing(null));
        });
        builder.getClass().getMethod("addInterceptor", interceptor).invoke(builder, transport);
        http.set(adapter, call(builder, "build"));

        Class<?> presenterType = loader.loadClass("s23");
        Object presenter = loader.loadClass("r43").getConstructor().newInstance();
        Class<?> paginator = loader.loadClass("net.dean.jraw.paginators.Paginator");
        String[] ids = IntStream.range(0, 200).mapToObj(index -> "t3_fixture" + index).toArray(String[]::new);
        Object history = loader.loadClass("net.dean.jraw.paginators.SpecificPaginator")
                .getConstructor(reddit, String[].class).newInstance(client, ids);
        Method load = presenterType.getMethod("g", paginator, boolean.class);
        Field worker = field(presenterType, "e");
        try {
            AsyncTask<?, ?, ?> first = (AsyncTask<?, ?, ?>) onMain(() -> {
                load.invoke(presenter, history, false);
                Object owner = worker.get(presenter);
                return field(owner.getClass(), "a").get(owner);
            });
            check(historyEntered.await(5, TimeUnit.SECONDS), "History did not reach the batched lookup");

            var other = new AsyncTask<Void, Void, Object>() {
                @Override protected Object doInBackground(Void... unused) {
                    try {
                        Object feed = loader.loadClass("net.dean.jraw.paginators.SubredditPaginator")
                                .getConstructor(reddit, String.class, String[].class)
                                .newInstance(client, "Android", new String[0]);
                        return paginator.getMethod("next").invoke(feed);
                    } catch (ReflectiveOperationException failure) {
                        throw new AssertionError(failure);
                    }
                }
            };
            onMain(other::execute);
            check(other.get(3, TimeUnit.SECONDS) != null, "Another Reddit request failed while History was blocked");

            releaseHistory.countDown();
            first.get(5, TimeUnit.SECONDS);
            check((Boolean) paginator.getMethod("hasNext").invoke(history), "History lost its next page");
            Object page = paginator.getMethod("next").invoke(history);
            check(((Iterable<?>) page).iterator().hasNext(), "History's next page returned no posts");
            check(batches.size() == 2 && batches.get(0).equals(String.join(",", List.of(ids).subList(0, 10)))
                    && batches.get(1).equals(String.join(",", List.of(ids).subList(10, 20))),
                    "History skipped or repeated IDs");
        } finally {
            releaseHistory.countDown();
            http.set(adapter, nativeClient);
        }
    }

    private static String listing(String slug) throws Exception {
        JSONArray posts = new JSONArray();
        if (slug != null) {
            posts.put(new JSONObject().put("kind", "t3").put("data", new JSONObject()
                    .put("name", "t3_" + slug).put("id", slug).put("subreddit", "Android")
                    .put("title", "Fixture").put("author", "fixture")));
        }
        return new JSONObject().put("kind", "Listing").put("data", new JSONObject()
                .put("children", posts).put("before", JSONObject.NULL).put("after", JSONObject.NULL)).toString();
    }

    private static Object response(ClassLoader loader, Object request, int code, String mime, String text)
            throws ReflectiveOperationException {
        Class<?> type = loader.loadClass("okhttp3.MediaType");
        Class<?> body = loader.loadClass("okhttp3.ResponseBody");
        Class<?> protocol = loader.loadClass("okhttp3.Protocol");
        Object builder = loader.loadClass("okhttp3.Response$Builder").getConstructor().newInstance();
        Class<?> c = builder.getClass();
        c.getMethod("request", loader.loadClass("okhttp3.Request")).invoke(builder, request);
        c.getMethod("protocol", protocol).invoke(builder, protocol.getField("HTTP_1_1").get(null));
        c.getMethod("code", int.class).invoke(builder, code);
        c.getMethod("message", String.class).invoke(builder, "fixture");
        c.getMethod("header", String.class, String.class).invoke(builder, "Content-Type", mime);
        c.getMethod("body", body).invoke(builder, body.getMethod("create", String.class, type)
                .invoke(null, text, type.getMethod("get", String.class).invoke(null, mime)));
        return call(builder, "build");
    }

    private static Object call(Object target, String method) throws ReflectiveOperationException {
        return target.getClass().getMethod(method).invoke(target);
    }

    private static Field field(Class<?> owner, String name) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Object onMain(java.util.concurrent.Callable<Object> work) throws Exception {
        var task = new FutureTask<>(work);
        PatchedditInstrumentation.instance.runOnMainSync(task);
        return task.get(5, TimeUnit.SECONDS);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
