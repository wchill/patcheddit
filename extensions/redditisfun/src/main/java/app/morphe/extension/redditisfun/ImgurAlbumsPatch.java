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
