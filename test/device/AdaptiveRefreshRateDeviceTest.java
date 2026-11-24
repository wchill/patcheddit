/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.patcheddit.fixture;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.view.View;

import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public final class AdaptiveRefreshRateDeviceTest {
    public static void run(Context context, Bundle arguments) throws Exception {
        String key = arguments.getString("preference", "morphe_patch_prefer_high_refresh_rate");
        var preferences = PreferenceManager.getDefaultSharedPreferences(context);
        Object original = preferences.getAll().get(key);
        try {
            check(preferences.edit().remove(key).commit(), "Could not clear the refresh preference");
            verifyActivity(context, key, true);
            check(preferences.edit().putBoolean(key, false).commit(), "Could not turn refresh off");
            verifyActivity(context, key, false);
            check(preferences.edit().putBoolean(key, true).commit(), "Could not turn refresh back on");
            verifyActivity(context, key, true);
        } finally {
            var editor = preferences.edit();
            if (original instanceof Boolean value) editor.putBoolean(key, value);
            else editor.remove(key);
            check(editor.commit(), "Could not restore the refresh preference");
        }
    }

    private static void verifyActivity(Context context, String key, boolean enabled) throws Exception {
        var instrumentation = PatchedditInstrumentation.instance;
        Activity activity = instrumentation.startActivitySync(new Intent()
                .setClassName(context, "app.morphe.extension.shared.settings.MorpheSettingsActivity")
                .putExtra("morphe_screen", "morphe_category_interface")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
        try {
            var result = new FutureTask<Void>(() -> {
                var preference = (CheckBoxPreference) ((PreferenceActivity) activity).findPreference(key);
                check(preference != null && preference.isChecked() == enabled, "Wrong refresh switch state");
                check(activity.getWindow().getAttributes().preferredRefreshRate == (enabled ? 120f : 0f),
                        "Wrong window refresh hint");
                if (Build.VERSION.SDK_INT >= 35) {
                    float expected = enabled ? View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
                            : View.REQUESTED_FRAME_RATE_CATEGORY_NO_PREFERENCE;
                    check(activity.getWindow().getDecorView().getRequestedFrameRate() == expected,
                            "Wrong View refresh category");
                }
                return null;
            });
            instrumentation.runOnMainSync(result);
            result.get(10, TimeUnit.SECONDS);
        } finally {
            instrumentation.runOnMainSync(activity::finish);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
