/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.infinity.http;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

public final class ShareLinkSession {
    private static volatile Object guestProvider;

    private ShareLinkSession() {}

    public static void setGuestProvider(Object provider) {
        guestProvider = provider;
    }

    public static String accessToken(Context context) {
        SharedPreferences account = context.getSharedPreferences(
                "ml.docilealligator.infinityforreddit.current_account", Context.MODE_PRIVATE);
        Object provider = guestProvider;
        if (provider == null || !isGuest(account)) return account.getString("access_token", null);
        String token;
        synchronized (refreshLock(provider)) {
            if (!isGuest(account)) return account.getString("access_token", null);
            token = cachedGuestToken(provider);
            if (TextUtils.isEmpty(token)) token = refreshGuestToken(provider);
        }
        return isGuest(account) ? token : account.getString("access_token", null);
    }

    private static boolean isGuest(SharedPreferences account) {
        return "-".equals(account.getString("account_name", "-"));
    }

    // Bound to Infinity's anonymous authenticator by the share-links patch.
    private static Object refreshLock(Object provider) { return provider; }
    private static String cachedGuestToken(Object provider) { return null; }
    private static String refreshGuestToken(Object provider) { return null; }
}
