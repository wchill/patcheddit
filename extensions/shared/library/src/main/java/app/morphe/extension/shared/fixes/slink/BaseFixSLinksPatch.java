package app.morphe.extension.shared.fixes.slink;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import app.morphe.extension.shared.Utils;

/** Replays the resolved permalink through the app's normal intent handler. */
public abstract class BaseFixSLinksPatch {
    protected Class<? extends Activity> webViewActivityClass;
    protected String webViewActivityExtraKey = "url";
    protected static BaseFixSLinksPatch INSTANCE;

    public boolean resolveSLink(String link) {
        if (!ShareLinkResolver.canResolve(link) || ShareLinkNavigation.isFallback(link)) return false;
        Context context = Utils.getContext();
        if (context == null) return false;
        if (link.endsWith("#bypass")) {
            Intent intent = new Intent(context, webViewActivityClass);
            intent.putExtra(webViewActivityExtraKey, link);
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        }

        Utils.runOnBackgroundThread(() -> {
            String destination = ShareLinkNavigation.resolve(context, link, false);
            String route = destination != null ? destination
                    : Uri.parse(link).buildUpon().fragment("bypass").build().toString();
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(route))
                    .setPackage(context.getPackageName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Utils.runOnMainThread(() -> context.startActivity(intent));
        });
        return true;
    }
}
