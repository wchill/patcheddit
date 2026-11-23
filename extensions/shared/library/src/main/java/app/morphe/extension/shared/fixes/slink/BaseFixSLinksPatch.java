package app.morphe.extension.shared.fixes.slink;


import static app.morphe.extension.shared.Utils.getContext;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;


/**
 * Base class to implement /s/ link resolution in 3rd party Reddit apps.
 * <br>
 * <br>
 * Usage:
 * <br>
 * <br>
 * An implementation of this class must have two static methods that are called by the app:
 * <ul>
 *     <li>public static boolean patchResolveSLink(String link)</li>
 *     <li>public static void patchSetAccessToken(String accessToken)</li>
 * </ul>
 * The static methods must call the instance methods of the base class.
 * <br>
 * The singleton pattern can be used to access the instance of the class:
 * <pre>
 * {@code
 * {
 *     INSTANCE = new FixSLinksPatch();
 * }
 * }
 * </pre>
 * Set the app's web view activity class as a fallback to open /s/ links if the resolution fails:
 * <pre>
 * {@code
 * private FixSLinksPatch() {
 *     webViewActivityClass = WebViewActivity.class;
 * }
 * }
 * </pre>
 * Hook the app's navigation handler to call this method before doing any of its own resolution:
 * <pre>
 * {@code
 * public static boolean patchResolveSLink(Context context, String link) {
 *     return INSTANCE.resolveSLink(context, link);
 * }
 * }
 * </pre>
 * If this method returns true, the app should early return and not do any of its own resolution.
 * <br>
 * <br>
 * Hook the app's access token so that this class can use it to resolve /s/ links:
 * <pre>
 * {@code
 * public static void patchSetAccessToken(String accessToken) {
 *     INSTANCE.setAccessToken(access_token);
 * }
 * }
 * </pre>
 */
public abstract class BaseFixSLinksPatch {
    /**
     * The class of the activity used to open links in a web view if resolving them fails.
     */
    protected Class<? extends Activity> webViewActivityClass;

    /**
     * The intent extra key that {@link #webViewActivityClass} reads its url from.
     * Apps that use a different key must set this in their constructor.
     */
    protected String webViewActivityExtraKey = "url";

    /**
     * The access token used to resolve the /s/ link.
     */
    protected volatile String accessToken;

    /**
     * The URL that was trying to be resolved before the access token was set.
     * If this is not null, the URL will be resolved right after the access token is set.
     */
    protected String pendingUrl;

    /**
     * The singleton instance of the class.
     */
    protected static BaseFixSLinksPatch INSTANCE;

    public boolean resolveSLink(String link) {
        switch (resolveLink(link)) {
            case ACCESS_TOKEN_START: {
                pendingUrl = link;
                return true;
            }
            case DO_NOTHING:
                return true;
            default:
                return false;
        }
    }

    private ResolveResult resolveLink(String link) {
        if (ShareLinkResolver.shareUri(link) != null) {
            Context context = getContext();
            // A link ends with #bypass if it failed to resolve below.
            // resolveLink is called with the same link again but this time with #bypass
            // so that the link is opened in the app browser instead of trying to resolve it again.
            if (link.endsWith("#bypass")) {
                openInAppBrowser(context, link);

                return ResolveResult.DO_NOTHING;
            }

            if (accessToken == null) {
                // This is not optimal.
                // However, an accessToken is necessary to make an authenticated request to Reddit.
                // in case Reddit has banned the IP - e.g. VPN.
                Intent startIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
                if (startIntent == null) return ResolveResult.CONTINUE;
                startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(startIntent);

                return ResolveResult.ACCESS_TOKEN_START;
            }


            Utils.runOnBackgroundThread(() -> {
                String bypassLink = Uri.parse(link).buildUpon().fragment("bypass").build().toString();
                String location = null;
                try {
                    location = ShareLinkResolver.resolve(link, accessToken);
                } catch (Exception e) {
                    Logger.printDebug(() -> "Could not resolve Reddit share link");
                }
                Intent startIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(location == null ? bypassLink : location));
                startIntent.setPackage(context.getPackageName());
                startIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(startIntent);
            });

            return ResolveResult.DO_NOTHING;
        }

        return ResolveResult.CONTINUE;
    }

    public void setAccessToken(String accessToken) {
        Logger.printDebug(() -> "Setting access token");

        this.accessToken = accessToken;

        // In case a link was trying to be resolved before access token was set.
        // The link is resolved now, after the access token is set.
        if (pendingUrl != null) {
            String link = pendingUrl;
            pendingUrl = null;

            Logger.printDebug(() -> "Opening pending URL");

            resolveLink(link);
        }
    }

    private void openInAppBrowser(Context context, String link) {
        Intent intent = new Intent(context, webViewActivityClass);
        intent.putExtra(webViewActivityExtraKey, link);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
