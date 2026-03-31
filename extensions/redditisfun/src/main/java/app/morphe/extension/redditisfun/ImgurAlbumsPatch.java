/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.redditisfun;

import android.net.Uri;

public class ImgurAlbumsPatch {
    public static Uri buildImgurUri(String albumId, boolean isGallery) {
        Uri.Builder builder = new Uri.Builder().scheme("https")
                .authority("api.imgur.com")
                .appendPath("3");
        if (isGallery) {
            builder.appendPath("gallery");
        }
        return builder.appendPath("album")
                .appendPath(albumId)
                .build();
    }
}
