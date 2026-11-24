package app.morphe.extension.shared.fixes.slink;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.io.IOException;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

public final class ShareLinkNavigation {
    private static final String FALLBACK = "morphe-share-fallback";
    private static final String BACON_DISPATCHER = "com.onelouder.baconreader.LinkDispatcher";
    private static final String BACON_FORCE_BROWSER = "EXTRA_FORCE_BROWSER";
    private static final String JOEY_OPEN = "o.o.joey.Activities.OpenInApp";
    private static final String JOEY_COMMENT = "o.o.joey.Activities.CommentActivity";

    private ShareLinkNavigation() {}

    public static Intent baconReaderIntent(Context context, String rawLink) {
        return context != null && isShareLink(rawLink)
                ? viewIntent(context, rawLink, BACON_DISPATCHER) : null;
    }

    public static boolean openBaconReader(Activity activity, Intent intent) {
        if (intent == null || intent.getBooleanExtra(BACON_FORCE_BROWSER, false)) return false;
        String link = intent.getDataString();
        // BaconReader marks HTTPS URLs in its own rendered URLSpans with this exact scheme.
        if (link != null && link.startsWith("browser-activity-https://")) {
            link = "https:" + link.substring("browser-activity-https:".length());
        }
        return open(activity, link, BACON_DISPATCHER, activity, true);
    }

    public static boolean openActivity(Activity activity) {
        Intent intent = activity.getIntent();
        if (intent == null || !(Intent.ACTION_VIEW.equals(intent.getAction())
                || "android.nfc.action.NDEF_DISCOVERED".equals(intent.getAction()))) return false;
        return open(activity, intent.getDataString(), activity.getClass().getName(), activity, false);
    }

    public static boolean open(Context context, String link, String targetClass) {
        return open(context, link, targetClass, null, false);
    }

    public static boolean openWrapperActivity(Activity activity, String targetClass) {
        Intent intent = activity.getIntent();
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) return false;
        String link = intent.getDataString();
        if (!ShareLinkResolver.isWrapper(link) || link.endsWith("#bypass")
                || !open(activity, link, targetClass)) return false;
        activity.finish();
        return true;
    }

    public static boolean openInfinity(Activity activity, Uri uri) {
        if (uri == null) return false;
        String link = uri.toString();
        if (!ShareLinkResolver.isWrapper(link) || !open(activity, link, activity.getClass().getName())) return false;
        activity.finish();
        return true;
    }

    public static boolean openJoey(Context context, String link) {
        if (context == null || link == null || isFallback(link)) return false;
        if (context instanceof Activity && JOEY_OPEN.equals(context.getClass().getName())) {
            Intent incoming = ((Activity) context).getIntent();
            if (incoming != null && link.equals(incoming.getDataString())
                    && !Intent.ACTION_VIEW.equals(incoming.getAction())) return false;
        }
        Context app = context.getApplicationContext();
        if (app == null) return false;
        if (ShareLinkResolver.canResolve(link)) {
            Utils.runOnBackgroundThread(() -> {
                String resolved = resolve(app, link, true);
                Utils.runOnMainThread(() -> {
                    if (resolved == null) start(app, viewIntent(app, fallback(link), JOEY_OPEN));
                    else openJoeyDestination(app, resolved);
                });
            });
            return true;
        }
        String normalized = ShareLinkResolver.joeyNormalization(link);
        if (normalized == null || normalized.equals(link)) return false;
        openJoeyDestination(app, normalized);
        return true;
    }

    private static void openJoeyDestination(Context app, String link) {
        ShareLinkResolver.CommentRoute comment = ShareLinkResolver.joeyComment(link);
        if (comment == null) {
            start(app, viewIntent(app, link, JOEY_OPEN));
            return;
        }
        Intent intent = new Intent().setClassName(app.getPackageName(), JOEY_COMMENT)
                .putExtra("submission", comment.submission)
                .putExtra("highlight_context", comment.comment)
                .putExtra("np", comment.nonParticipation);
        String subreddit = comment.subreddit();
        if (subreddit != null) intent.putExtra("subreddit", subreddit);
        if (comment.context >= 0) intent.putExtra("EXTRA_HIGHLIGHT_CONTEXT_COUNT", comment.context);
        start(app, intent);
    }

    static boolean isShareLink(String link) {
        return ShareLinkResolver.canResolve(link) && !isFallback(link);
    }

    static boolean isFallback(String link) {
        return link != null && FALLBACK.equals(Uri.parse(link).getFragment());
    }

    private static String fallback(String link) {
        return Uri.parse(link).buildUpon().fragment(FALLBACK).build().toString();
    }

    private static void start(Context app, Intent intent) {
        try {
            app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException ignored) {
            Logger.printDebug(() -> "Reddit share link activity unavailable");
        }
    }

    static String resolve(Context app, String link, boolean joey) {
        String token = null;
        try {
            token = getAccessToken(app);
        } catch (RuntimeException ignored) {
            Logger.printDebug(() -> "Reddit share link session unavailable");
        }
        try {
            return ShareLinkResolver.resolve(link, token, joey, null);
        } catch (IOException ignored) {
            Logger.printDebug(() -> "Could not resolve Reddit share link");
            return null;
        }
    }

    private static boolean open(Context context, String link, String targetClass,
                                Activity dispatcher, boolean baconReader) {
        if (!isShareLink(link)) return false;
        Context app = context == null ? Utils.getContext() : context.getApplicationContext();
        if (app == null) return false;
        Utils.runOnBackgroundThread(() -> {
            String destination = resolve(app, link, false);
            boolean failed = destination == null;
            String route;
            if (failed) {
                route = fallback(link);
            } else {
                route = baconReader ? ShareLinkResolver.baconReaderPermalink(link, destination) : destination;
            }
            Intent replay = viewIntent(app, route, failed && dispatcher != null
                    ? dispatcher.getClass().getName() : targetClass);
            if (failed && baconReader) replay.putExtra(BACON_FORCE_BROWSER, true);
            Utils.runOnMainThread(() -> {
                if (dispatcher != null && dispatcher.isFinishing()) return;
                try {
                    if (dispatcher == null) {
                        app.startActivity(replay.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } else {
                        dispatcher.startActivity(replay);
                        // BaconReader's singleTask LinkDispatcher receives the replay as onNewIntent.
                        // Its original processing method finishes it after starting the target.
                        if (!baconReader) dispatcher.finish();
                    }
                } catch (ActivityNotFoundException ignored) {
                    Logger.printDebug(() -> "Reddit share link activity unavailable");
                }
            });
        });
        return true;
    }

    private static Intent viewIntent(Context context, String link, String targetClass) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
        return targetClass == null ? intent.setPackage(context.getPackageName())
                : intent.setClassName(context.getPackageName(), targetClass);
    }

    // Substituted by each app's patch. Read the current session on demand instead of retaining tokens.
    private static String getAccessToken(Context context) {
        return null;
    }

    private static String getRifCachedToken(Context context, Account account) {
        return AccountManager.get(context).peekAuthToken(account, "redditisfun_oauth2");
    }
}
