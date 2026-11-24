package app.morphe.extension.syncforreddit;

import android.util.Base64;

import com.laurencedawson.reddit_sync.ui.activities.WebViewActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import app.morphe.extension.shared.fixes.slink.BaseFixSLinksPatch;

/** @noinspection unused*/
public class FixSLinksPatch extends BaseFixSLinksPatch {
    static {
        INSTANCE = new FixSLinksPatch();
    }

    private FixSLinksPatch() {
        webViewActivityClass = WebViewActivity.class;
    }

    public static boolean patchResolveSLink(String link) {
        return INSTANCE.resolveSLink(link);
    }

    public static String tokenCacheKey(String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty()) return "patcheddit-oauth-guest";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return "patcheddit-oauth-user-" + Base64.encodeToString(digest, Base64.NO_WRAP | Base64.URL_SAFE);
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    public static String readCachedToken(byte[] data) {
        if (data == null) return null;
        try {
            return new JSONObject(new String(data, StandardCharsets.UTF_8)).getString("access_token");
        } catch (JSONException ignored) {
            return null;
        }
    }
}
