/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.settings;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import app.morphe.extension.shared.Utils;

@SuppressWarnings("deprecation")
public final class PatchSettings {
    private static boolean initializationWarning;

    private PatchSettings() {}

    public static void initialize(Application application) {
        if (!Utils.isContextSet()) Utils.setContext(application);
    }

    public static boolean isEnabled(String key, boolean defaultValue) {
        if (!Utils.isContextSet()) {
            warnBeforeInitialization();
            return defaultValue;
        }
        return preferences(Utils.getContext()).getBoolean(key, defaultValue);
    }

    public static SharedPreferences preferences(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    private static synchronized void warnBeforeInitialization() {
        if (!initializationWarning) {
            initializationWarning = true;
            Log.w("MorpheSettings", "Application context is not initialized; using the included patch's startup default.");
        }
    }
}
