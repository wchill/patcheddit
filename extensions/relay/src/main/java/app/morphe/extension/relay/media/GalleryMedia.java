/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.relay.media;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.regex.Pattern;

import reddit.news.oauth.reddit.model.links.MediaPreview;

public final class GalleryMedia {
    private static final Pattern REDDIT_GIF = Pattern.compile("https://i\\.redd\\.it/[a-zA-Z0-9]+\\.gif");

    private GalleryMedia() {}

    public static String animationSource(JSONObject source, String mp4Key) throws JSONException {
        String mp4 = source.optString(mp4Key, "");
        if (!mp4.isEmpty() && !source.isNull(mp4Key)) return mp4;
        String gif = source.optString("gif", "");
        return REDDIT_GIF.matcher(gif).matches() ? gif : source.getString(mp4Key);
    }

    public static void useGifViewer(MediaPreview preview) {
        // Relay's gallery parser initially labels all animations as video.
        if (preview.type == MediaLinks.VIDEO && preview.mediaUrl != null
                && preview.mediaUrl.equals(preview.gifUrl) && REDDIT_GIF.matcher(preview.mediaUrl).matches()) {
            preview.type = MediaLinks.ANIMATED;
        }
    }
}
