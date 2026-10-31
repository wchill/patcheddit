/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.redditisfun;

import android.net.Uri;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class ImgurAlbumsPatch {
    public static Uri buildImgurUri(String albumId, boolean isGallery) {
        Uri.Builder builder = new Uri.Builder().scheme("https")
                .authority("api.imgur.com")
                .appendPath("3");
        if (isGallery) {
            builder.appendPath("gallery");
        } else {
            builder.appendPath("album");
        }
        return builder
                // Links may be /a/album-title-id.
                .appendPath(albumId.substring(albumId.lastIndexOf('-') + 1))
                .build();
    }

    // RIF only authenticates album requests for its own proxy.
    public static Response addClientId(Interceptor.Chain chain) throws IOException {
        Request request = chain.request();
        if (!"api.imgur.com".equals(request.url().host())) {
            return chain.proceed(request);
        }
        return chain.proceed(request.newBuilder()
                .removeHeader("x-redditisfun-key")
                .header("Authorization", "Client-ID 4d7e2f74f1a519c")
                .build());
    }
}
