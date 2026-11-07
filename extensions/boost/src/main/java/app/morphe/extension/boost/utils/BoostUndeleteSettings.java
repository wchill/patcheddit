/*
 * Copyright 2026 brealorg.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.boost.utils;

import android.content.Context;
import app.morphe.extension.shared.settings.PatchSettings;

/**
 * Runtime feature flags for Boost undelete behavior.
 */
public final class BoostUndeleteSettings {
    public static final String PREF_REDDIT_UNDELETE_ENABLED =
            "morphe_boost_reddit_undelete_enabled";
    public static final String PREF_IMGUR_UNDELETE_ENABLED =
            "morphe_boost_imgur_undelete_enabled";

    private static final boolean DEFAULT_REDDIT_UNDELETE_ENABLED = false;
    private static final boolean DEFAULT_IMGUR_UNDELETE_ENABLED = false;

    private BoostUndeleteSettings() {
    }

    public static boolean isRedditUndeleteEnabled() {
        return getBoolean(PREF_REDDIT_UNDELETE_ENABLED, DEFAULT_REDDIT_UNDELETE_ENABLED);
    }

    public static boolean isImgurUndeleteEnabled() {
        return getBoolean(PREF_IMGUR_UNDELETE_ENABLED, DEFAULT_IMGUR_UNDELETE_ENABLED);
    }

    /**
     * Legacy inclusion markers for native integrations. Selected patches now register their
     * own menu entries. Unknown keys count as applied.
     */
    public static boolean isTogglePatchApplied(Context context, String key) {
        String marker;
        if (PREF_REDDIT_UNDELETE_ENABLED.equals(key)) {
            marker = "morphe_boost_undelete_reddit_applied";
        } else if (PREF_IMGUR_UNDELETE_ENABLED.equals(key)) {
            marker = "morphe_boost_undelete_imgur_applied";
        } else {
            return true;
        }
        try {
            return context.getResources().getIdentifier(marker, "string", context.getPackageName()) != 0;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static boolean getBoolean(String key, boolean fallback) {
        return PatchSettings.isEnabled(key, fallback);
    }
}
