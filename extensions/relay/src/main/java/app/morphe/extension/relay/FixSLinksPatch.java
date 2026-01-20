package app.morphe.extension.relay;

import android.app.Activity;

import app.morphe.extension.shared.fixes.slink.BaseFixSLinksPatch;
import reddit.news.WebAndComments;

/**
 * @noinspection unused
 */
public class FixSLinksPatch extends BaseFixSLinksPatch {
    static {
        INSTANCE = new FixSLinksPatch();
    }

    private FixSLinksPatch() {
        webViewActivityClass = WebAndComments.class;
    }

    public static boolean patchResolveSLink(String link) {
        return INSTANCE.resolveSLink(link);
    }

    public static boolean patchResolveSLink(Activity activity) {
        String data = activity.getIntent().getDataString();

        if (data == null) {
            return false;
        }

        return INSTANCE.resolveSLink(data);
    }

    public static void patchSetAccessToken(String accessToken) {
        INSTANCE.setAccessToken(accessToken);
    }
}
