/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.settings;

import android.app.Activity;
import android.app.Application;
import android.app.ActionBar;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.graphics.Typeface;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.preference.PreferenceActivity;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsetsController;
import android.widget.ListView;
import android.widget.TextView;

@SuppressWarnings("deprecation")
final class MorpheSettingsStyle {
    private static boolean installed;
    private static Resources.Theme nativeTheme;
    private static Drawable.ConstantState toolbarBackground;
    private static Typeface titleTypeface;
    private static float titleSize;
    private static int titleColor;
    private static float toolbarElevation;
    private static boolean listDividers;
    private static int statusBarColor;
    private static int navigationBarColor;

    private MorpheSettingsStyle() {}

    static void initialize(Application application) {
        if (installed) return;
        installed = true;
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityPaused(Activity activity) {
                if (activity.getClass().getName().startsWith("app.morphe.extension.")) return;
                Resources.Theme snapshot = activity.getResources().newTheme();
                snapshot.setTo(activity.getTheme());
                nativeTheme = snapshot;
                View decor = activity.getWindow().getDecorView();
                View toolbar = findToolbar(decor);
                Drawable background = null;
                for (View view = toolbar; view != null && background == null;
                        view = view.getParent() instanceof View ? (View) view.getParent() : null) {
                    background = view.getBackground();
                }
                toolbarBackground = background == null ? null : background.getConstantState();
                toolbarElevation = toolbar == null ? 0 : toolbar.getElevation();
                TextView title = toolbar == null ? null : findTitle(toolbar);
                titleTypeface = title == null ? null : title.getTypeface();
                titleSize = title == null ? 0 : title.getTextSize();
                titleColor = title == null ? 0 : title.getCurrentTextColor();
                ListView list = findList(decor);
                listDividers = list != null && list.getDivider() != null;
                statusBarColor = activity.getWindow().getStatusBarColor();
                navigationBarColor = activity.getWindow().getNavigationBarColor();
            }

            @Override public void onActivityCreated(Activity activity, Bundle state) {}
            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityResumed(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });
    }

    static void applyTheme(Activity activity) {
        if (nativeTheme != null) activity.getTheme().setTo(nativeTheme);
        // Keep the host's colors and typography, with a framework action bar for shared preferences.
        activity.getTheme().applyStyle(MorpheSettings.resourceId(
                activity, "style", "MorpheSettingsFrameworkOverlay"), true);
    }

    static void applyWindow(PreferenceActivity activity) {
        if (nativeTheme == null) return;
        ActionBar bar = activity.getActionBar();
        if (bar != null) {
            if (toolbarBackground != null) {
                bar.setBackgroundDrawable(toolbarBackground.newDrawable(activity.getResources()).mutate());
            }
            bar.setElevation(toolbarElevation);
            if (titleTypeface != null) {
                TypedArray up = activity.obtainStyledAttributes(new int[]{android.R.attr.homeAsUpIndicator});
                try {
                    Drawable arrow = up.getDrawable(0);
                    if (arrow != null) {
                        arrow = arrow.mutate();
                        arrow.setTint(lightBar(titleColor, Color.BLACK) ? Color.WHITE : Color.BLACK);
                        bar.setHomeAsUpIndicator(arrow);
                    }
                } finally {
                    up.recycle();
                }
            }
        }
        View decor = activity.getWindow().getDecorView();
        View toolbar = findToolbar(decor);
        TextView title = toolbar == null ? null : findTitle(toolbar);
        if (title != null && titleTypeface != null) {
            title.setTypeface(titleTypeface);
            title.setTextSize(TypedValue.COMPLEX_UNIT_PX, titleSize);
            title.setTextColor(titleColor);
        }
        if (!listDividers) activity.getListView().setDivider(null);
        TypedValue surface = new TypedValue();
        int colorSurface = activity.getResources().getIdentifier("colorSurface", "attr", activity.getPackageName());
        if (colorSurface == 0 || !activity.getTheme().resolveAttribute(colorSurface, surface, true)) {
            activity.getTheme().resolveAttribute(android.R.attr.colorBackgroundFloating, surface, true);
        }
        if (surface.type >= TypedValue.TYPE_FIRST_COLOR_INT && surface.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            activity.getListView().setBackgroundColor(surface.data);
        }
        activity.getWindow().setStatusBarColor(statusBarColor);
        activity.getWindow().setNavigationBarColor(navigationBarColor);
        TypedValue optOut = new TypedValue();
        boolean optedOut = Build.VERSION.SDK_INT >= 35 && activity.getTheme().resolveAttribute(
                android.R.attr.windowOptOutEdgeToEdgeEnforcement, optOut, true) && optOut.data != 0;
        boolean transparent = transparentBars(Build.VERSION.SDK_INT,
                activity.getApplicationInfo().targetSdkVersion, optedOut);
        int lightBars = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        int visibility = lightBar(transparent ? 0 : statusBarColor, surface.data)
                ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : 0;
        if (Build.VERSION.SDK_INT >= 26 && lightBar(transparent ? 0 : navigationBarColor, surface.data)) {
            visibility |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        final int nativeVisibility = visibility;
        decor.setSystemUiVisibility((decor.getSystemUiVisibility() & ~lightBars)
                | (nativeVisibility & lightBars));
        if (Build.VERSION.SDK_INT >= 30) {
            decor.post(() -> {
                WindowInsetsController controller = activity.getWindow().getInsetsController();
                if (controller != null) {
                    int appearance = 0;
                    if ((nativeVisibility & View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR) != 0) {
                        appearance |= WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;
                    }
                    if ((nativeVisibility & View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR) != 0) {
                        appearance |= WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                    }
                    controller.setSystemBarsAppearance(appearance,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
                }
            });
        }
    }

    static boolean transparentBars(int sdk, int target, boolean optedOut) {
        return sdk >= 35 && target >= 35 && (!optedOut || sdk >= 36 && target >= 36);
    }

    static boolean lightBar(int color, int surface) {
        if ((color >>> 24) == 0) color = surface;
        return 0.2126 * channel((color >> 16) & 255)
                + 0.7152 * channel((color >> 8) & 255)
                + 0.0722 * channel(color & 255) > 0.5;
    }

    private static double channel(int value) {
        double normalized = value / 255.0;
        return normalized <= 0.03928 ? normalized / 12.92
                : Math.pow((normalized + 0.055) / 1.055, 2.4);
    }

    private static View findToolbar(View view) {
        for (Class<?> type = view.getClass(); type != null; type = type.getSuperclass()) {
            String name = type.getName();
            if (name.endsWith(".Toolbar") || name.endsWith(".MaterialToolbar")
                    || name.endsWith(".ActionBarView")) return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                View found = findToolbar(group.getChildAt(index));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static TextView findTitle(View view) {
        return find(view, TextView.class);
    }

    private static ListView findList(View view) {
        return find(view, ListView.class);
    }

    private static <T extends View> T find(View view, Class<T> type) {
        if (type.isInstance(view)) return type.cast(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                T found = find(group.getChildAt(index), type);
                if (found != null) return found;
            }
        }
        return null;
    }

    static void addSectionIcons(Activity activity, PreferenceGroup group) {
        TypedArray colors = activity.obtainStyledAttributes(new int[]{android.R.attr.textColorSecondary});
        ColorStateList tint;
        try {
            tint = colors.getColorStateList(0);
        } finally {
            colors.recycle();
        }
        for (int index = 0; index < group.getPreferenceCount(); index++) {
            Preference entry = group.getPreference(index);
            if (!(entry instanceof PreferenceScreen)
                    && !MorpheSettingsActivity.ABOUT.equals(entry.getKey())) continue;
            Drawable icon = icon(activity, sectionIconName(entry.getKey()));
            icon.setTintList(tint);
            entry.setIcon(icon);
        }
    }

    static Drawable icon(Activity activity, String name) {
        return activity.getResources().getDrawable(
                MorpheSettings.resourceId(activity, "drawable", "patcheddit_icon_" + name),
                activity.getTheme()).mutate();
    }

    static String sectionIconName(String key) {
        switch (key == null ? "" : key) {
            case "morphe_category_navigation":
                return "explore";
            case "morphe_category_links":
                return "link";
            case "morphe_category_media":
                return "media";
            case "morphe_category_feed":
                return "feed";
            case "morphe_category_comments_posting":
                return "comment";
            case "morphe_category_undelete":
                return "restore";
            case "morphe_category_login_api":
                return "key";
            case "morphe_category_ads_tracking":
                return "security";
            case "morphe_category_diagnostics":
                return "bug";
            case MorpheSettingsActivity.ABOUT:
                return "info";
            case "morphe_category_interface":
                return "palette";
            case "morphe_category_advanced":
                return "code";
            default:
                return "settings";
        }
    }
}
