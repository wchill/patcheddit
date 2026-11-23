/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.patcheddit.fixture;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.util.TypedValue;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

@SuppressWarnings("deprecation")
public final class InfinitySettingsThemeDeviceTest {
    private static final String NATIVE_SETTINGS = "ml.docilealligator.infinityforreddit.activities.SettingsActivity";
    private static final String PATCH_SETTINGS = "app.morphe.extension.shared.settings.MorpheSettingsActivity";

    public static void run(Context context, Bundle arguments) throws Exception {
        int theme = context.getResources().getIdentifier("Theme.Normal.AmoledDark", "style", context.getPackageName());
        check(theme != 0, "Missing Infinity's AMOLED theme");

        Activity nativeSettings = launch(new Intent().setClassName(context, NATIVE_SETTINGS));
        try {
            // The pause callback captures the host theme without changing the saved theme.
            onMain(() -> { nativeSettings.getTheme().applyStyle(theme, true); return null; });
            PreferenceActivity settings = (PreferenceActivity) launch(new Intent().setClassName(context, PATCH_SETTINGS));
            try {
                Intent section = onMain(() -> {
                    checkAmoledBackground(settings);
                    android.preference.Preference login = settings.findPreference("morphe_category_login_api");
                    check(login != null && login.getIntent() != null, "Missing Patcheddit Login & API section");
                    return new Intent(login.getIntent());
                });
                PreferenceActivity child = (PreferenceActivity) launch(section);
                try {
                    onMain(() -> { checkAmoledBackground(child); return null; });
                } finally {
                    finish(child);
                }
            } finally {
                finish(settings);
            }
        } finally {
            finish(nativeSettings);
        }
    }

    private static Activity launch(Intent intent) {
        return PatchedditInstrumentation.instance.startActivitySync(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private static void finish(Activity activity) throws Exception {
        onMain(() -> { activity.finish(); return null; });
    }

    private static void checkAmoledBackground(PreferenceActivity activity) {
        TypedValue background = new TypedValue();
        check(activity.getTheme().resolveAttribute(android.R.attr.windowBackground, background, true)
                        && background.type >= TypedValue.TYPE_FIRST_COLOR_INT && background.type <= TypedValue.TYPE_LAST_COLOR_INT
                        && background.data == Color.BLACK,
                "Patcheddit did not inherit Infinity's black AMOLED window background");
        Drawable listBackground = activity.getListView().getBackground();
        check(listBackground instanceof ColorDrawable && ((ColorDrawable) listBackground).getColor() == Color.BLACK,
                "Patcheddit preferences painted over the AMOLED background");
        check(activity.getListView().getCount() > 0, "Patcheddit preferences did not render");
        check(activity.getActionBar() != null, "Patcheddit lost its action bar");
    }

    private static <T> T onMain(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        PatchedditInstrumentation.instance.runOnMainSync(task);
        return task.get();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
