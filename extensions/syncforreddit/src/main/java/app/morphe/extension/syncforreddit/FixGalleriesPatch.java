/*
 * Copyright 2026 trulow.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 * Adapted from https://github.com/notBrian2/revanced-rif-fixes.
 */

package app.morphe.extension.syncforreddit;

import android.util.Log;

import com.android.volley.NetworkResponse;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reddit blocks /api/info for third-party clients, which is what Sync's OAuthGalleryRequest uses to load a
 * gallery's media_metadata and gallery_data. /comments/{id} returns the same post (as the first element of
 * its reply), so the request is sent there and the reply is unwrapped back into the Listing Sync parses.
 */
@SuppressWarnings("unused")
public final class FixGalleriesPatch {
    private static final Pattern INFO = Pattern.compile(
            "^(https://[a-z]+\\.reddit\\.com/)api/info(?:\\.json)?/?\\?(?:.*&)?id=t3_([a-z0-9]+)(?:&.*)?$",
            Pattern.CASE_INSENSITIVE);

    private FixGalleriesPatch() {
    }

    /** Hooked into OAuthGalleryRequest's URL builder, which is also its Volley cache key. */
    public static String galleryUrl(String url) {
        if (url == null) {
            return null;
        }
        Matcher m = INFO.matcher(url);
        if (!m.matches()) {
            return url;
        }
        return m.group(1) + "comments/" + m.group(2) + ".json?limit=1";
    }

    /** Hooked into OAuthGalleryRequest.parseNetworkResponse(). */
    public static NetworkResponse galleryResponse(NetworkResponse response) {
        if (response == null || response.data == null) {
            return response;
        }
        String body = Responses.body(response);
        if (!body.trim().startsWith("[")) {
            return response;  // Already a Listing (cached, or an /api/info reply).
        }
        try {
            return Responses.withBody(response, new JSONArray(body).getJSONObject(0).toString());
        } catch (JSONException e) {
            Log.w(Responses.TAG, "Could not convert gallery response", e);
            return response;
        }
    }
}
