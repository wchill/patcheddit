/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.boostforreddit.http;

import java.util.List;

import app.morphe.extension.boostforreddit.http.arcticshift.ArcticShiftThrottlingInterceptor;
import app.morphe.extension.boostforreddit.http.imgur.ImgurUndeleteInterceptor;
import app.morphe.extension.boostforreddit.http.reddit.RedditFixAudioInDownloadsInterceptor;
import app.morphe.extension.boostforreddit.http.reddit.RedditMediaUndeleteInterceptor;
import app.morphe.extension.boostforreddit.http.reddit.RedditSubmissionUndeleteInterceptor;
import app.morphe.extension.boostforreddit.http.reddit.RedditSubredditUndeleteInterceptor;
import app.morphe.extension.boostforreddit.http.wayback.WaybackThrottlingInterceptor;
import app.morphe.extension.shared.fixes.feed.RAllPatch;
import app.morphe.extension.shared.requests.BaseOkHttpRequestHook;
import okhttp3.Interceptor;


/**
 * @noinspection unused
 */
public class OkHttpRequestHook extends BaseOkHttpRequestHook {
    private OkHttpRequestHook() {}

    public static void init() {
        if (INSTANCE == null) {
            INSTANCE = new OkHttpRequestHook();
        }
    }


    @Override
    protected List<Interceptor> getInterceptors() {
        return List.of(
                new RedditMediaUndeleteInterceptor(),
                new RedditSubmissionUndeleteInterceptor(),
                new RedditSubredditUndeleteInterceptor(),
                new RedditFixAudioInDownloadsInterceptor(),
                new ImgurUndeleteInterceptor(),
                new RAllPatch()
        );
    }

    @Override
    protected List<Interceptor> getNetworkInterceptors() {
        return List.of(
                new ArcticShiftThrottlingInterceptor(),
                new WaybackThrottlingInterceptor()
        );
    }
}
