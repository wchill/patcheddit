/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (82d70f7).
 */

package app.morphe.extension.boost.utils;

import android.app.Activity;
import android.app.Application;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import app.morphe.extension.boost.navigation.BoostNativeNavigation;

import java.util.WeakHashMap;

/**
 * @noinspection unused
 */
public final class BoostSystemBarInsetsFix {
    private static final int TARGET_SDK_EDGE_TO_EDGE = 35;
    private static final String MARKER = "MORPHE_BOOST_NAV_BAR_INSETS_FIX_V6";
    private static final String COMMENTS_SYSTEM_BAR_MARKER = "MORPHE_BOOST_COMMENTS_SYSTEM_BAR_SURFACE_V1";
    private static final String COMMENTS_TOOLBAR_FOREGROUND_MARKER = "MORPHE_BOOST_COMMENTS_TOOLBAR_FOREGROUND_V1";
    private static final String TOOLBAR_SURFACE_FOREGROUND_MARKER_V2 = "MORPHE_BOOST_TOOLBAR_SURFACE_FOREGROUND_V2";
    private static final String MAIN_NAV_BAR_SURFACE_MARKER_V3 = "MORPHE_BOOST_MAIN_NAV_BAR_SURFACE_V3";
    private static final String MAIN_NAV_BAR_SURFACE_OVERLAY_MARKER_V4 = "MORPHE_BOOST_MAIN_NAV_BAR_SURFACE_OVERLAY_V4";
    // Tag of BoostSearchBottomNavigation's decor underlay.
    private static final String NAVIGATION_DECOR_UNDERLAY_TAG = "morphe_boost_bottom_navigation_decor_underlay";
    private static final String EXTENDED_ACTIVITY_SURFACE_SCOPE_MARKER_V5 = "MORPHE_BOOST_EXTENDED_ACTIVITY_SURFACE_SCOPE_V5";
    private static final String DRAWER_STICKY_FOOTER_CLEARANCE_MARKER =
            "MORPHE_BOOST_DRAWER_STICKY_FOOTER_CLEARANCE_ISSUE150_V5_SYSTEM_INSET";
    private static final String DECOR_NAVIGATION_CONTAINER_TAG =
            "morphe_boost_decor_owned_navigation_container";
    private static final String COMMENTS_ACTIVITY_NAME = "com.rubenmayayo.reddit.ui.comments.CommentsActivity";
    private static final String IMAGE_VIEWER_LOADING_INSET_MARKER =
            "MORPHE_BOOST_IMAGE_VIEWER_LOADING_INSET_ISSUE170_V1";
    private static final String IMAGE_VIEWER_SAFE_AREA_MARKER =
            "MORPHE_BOOST_IMAGE_VIEWER_SAFE_AREA_ISSUE189_V2";
    private static final String TAG = "MorpheInsetsFix";

    private static final WeakHashMap<Application, Boolean> INSTALLED = new WeakHashMap<>();
    private static final WeakHashMap<View, Padding> ORIGINAL_PADDING = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> WATCHERS = new WeakHashMap<>();
    private static final WeakHashMap<View, Integer> STICKY_FOOTER_CLEARANCE =
            new WeakHashMap<>();
    private static final WeakHashMap<View, Float>
            STICKY_FOOTER_ORIGINAL_TRANSLATION_Y = new WeakHashMap<>();

    private BoostSystemBarInsetsFix() {
    }

    public static void install(final Application application) {
        try {
            if (application == null || INSTALLED.containsKey(application)) return;
            INSTALLED.put(application, Boolean.TRUE);

            application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(final Activity activity, Bundle savedInstanceState) {
                    scheduleMainInsets(activity);
                    scheduleCommentsSystemBars(activity);
                }

                @Override
                public void onActivityResumed(final Activity activity) {
                    scheduleMainInsets(activity);
                    scheduleCommentsSystemBars(activity);
                }

                @Override public void onActivityStarted(Activity activity) {}
                @Override public void onActivityPaused(Activity activity) {}
                @Override public void onActivityStopped(Activity activity) {}
                @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
                @Override public void onActivityDestroyed(Activity activity) {}
            });
        } catch (Throwable ignored) {
        }
    }

    public static void applyMediaInsets(Activity activity) {
        try {
            if (!shouldApply(activity)) return;

            String className = activity.getClass().getName();
            if (className.endsWith(".ui.activities.MediaImageActivity")) {
                View content = activity.findViewById(android.R.id.content);
                if (content != null) {
                    applySafeAreaPadding(content);
                    Log.i(
                            TAG,
                            "image viewer safe area marker="
                                    + IMAGE_VIEWER_SAFE_AREA_MARKER
                                    + " activity="
                                    + className
                    );
                }
                return;
            }

            View bottomBar = findViewByName(activity, "bottom_bar");
            if (bottomBar != null) {
                applyBottomInsetPadding(bottomBar, false);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void applyMainInsets(Activity activity) {
        scheduleMainInsets(activity);
    }

    private static void scheduleMainInsets(final Activity activity) {
        try {
            if (!shouldApply(activity)) return;

            final View decor = activity.getWindow().getDecorView();
            if (decor == null) return;

            installLayoutWatcher(activity, decor);

            Runnable apply = new Runnable() {
                @Override
                public void run() {
                    try {
                        applyMainInsetsNow(activity);
                    } catch (Throwable ignored) {
                    }
                }
            };

            decor.post(apply);
            decor.postDelayed(apply, 100L);
            decor.postDelayed(apply, 350L);
            decor.postDelayed(apply, 1000L);
            decor.postDelayed(apply, 2000L);
        } catch (Throwable ignored) {
        }
    }

    private static void installLayoutWatcher(final Activity activity, View decor) {
        if (WATCHERS.containsKey(decor)) return;
        WATCHERS.put(decor, Boolean.TRUE);

        decor.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                try {
                    applyMainInsetsNow(activity);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private static void applyMainInsetsNow(Activity activity) {
        View drawerRecycler = findViewByName(activity, "material_drawer_recycler_view");
        if (drawerRecycler != null) {
            applyBottomInsetPadding(drawerRecycler, true);
            nudgeBottomVisibleChildAboveNavigationBar(drawerRecycler);
        }

        View stickyFooter = findViewByName(activity, "material_drawer_sticky_footer");
        if (stickyFooter != null) {
            applyDrawerStickyFooterClearance(activity, stickyFooter);
        }
    }

    private static void applyDrawerStickyFooterClearance(
            Activity activity,
            View stickyFooter
    ) {
        if (activity == null || stickyFooter == null) return;

        Window window = activity.getWindow();
        View decor = window == null ? null : window.getDecorView();
        if (decor == null) return;

        if (stickyFooter.getVisibility() != View.VISIBLE) {
            restoreDrawerStickyFooterClearance(stickyFooter);
            return;
        }

        if (
                !stickyFooter.isAttachedToWindow()
                        || stickyFooter.getHeight() <= 0
        ) {
            return;
        }

        int systemInset = Math.max(
                0,
                getEffectiveNavigationBottomInset(
                        stickyFooter,
                        getBestCurrentInsets(stickyFooter)
                )
        );

        View navigationContainer = decor.findViewWithTag(
                DECOR_NAVIGATION_CONTAINER_TAG
        );
        boolean navigationAvailable =
                navigationContainer != null
                        && navigationContainer.getVisibility() == View.VISIBLE
                        && navigationContainer.getHeight() > 0;

        Integer previousValue =
                STICKY_FOOTER_CLEARANCE.get(stickyFooter);
        int previousClearance =
                previousValue == null
                        ? 0
                        : Math.max(0, previousValue);

        Float originalValue =
                STICKY_FOOTER_ORIGINAL_TRANSLATION_Y.get(stickyFooter);
        float originalTranslationY;

        if (originalValue == null) {
            originalTranslationY =
                    stickyFooter.getTranslationY() + previousClearance;
            STICKY_FOOTER_ORIGINAL_TRANSLATION_Y.put(
                    stickyFooter,
                    originalTranslationY
            );
        } else {
            originalTranslationY = originalValue;
        }

        int[] footerLocation = new int[2];
        stickyFooter.getLocationOnScreen(footerLocation);

        int footerBottom =
                footerLocation[1] + stickyFooter.getHeight();
        int navigationTop = -1;
        int signedNavigationOverlap = 0;
        int navigationClearance = 0;

        if (navigationAvailable) {
            int[] navigationLocation = new int[2];
            navigationContainer.getLocationOnScreen(navigationLocation);
            navigationTop = navigationLocation[1];
            signedNavigationOverlap = footerBottom - navigationTop;
            navigationClearance = Math.max(
                    0,
                    Math.min(
                            navigationContainer.getHeight(),
                            previousClearance + signedNavigationOverlap
                    )
            );
        }

        int targetClearance = Math.max(
                systemInset,
                navigationClearance
        );

        if (targetClearance <= 0) {
            restoreDrawerStickyFooterClearance(stickyFooter);
            return;
        }

        float targetTranslationY =
                originalTranslationY - targetClearance;

        if (
                Float.compare(
                        stickyFooter.getTranslationY(),
                        targetTranslationY
                ) == 0
                        && previousClearance == targetClearance
        ) {
            return;
        }

        stickyFooter.setTranslationY(targetTranslationY);
        stickyFooter.invalidate();
        STICKY_FOOTER_CLEARANCE.put(
                stickyFooter,
                targetClearance
        );

        Log.i(
                TAG,
                "drawer sticky footer clearance marker="
                        + DRAWER_STICKY_FOOTER_CLEARANCE_MARKER
                        + " mode=translation-system-inset"
                        + " systemInset="
                        + systemInset
                        + " navigationAvailable="
                        + navigationAvailable
                        + " navigationClearance="
                        + navigationClearance
                        + " signedNavigationOverlap="
                        + signedNavigationOverlap
                        + " previousClearance="
                        + previousClearance
                        + " targetClearance="
                        + targetClearance
                        + " originalTranslationY="
                        + originalTranslationY
                        + " targetTranslationY="
                        + targetTranslationY
                        + " footerTopBefore="
                        + footerLocation[1]
                        + " footerBottomBefore="
                        + footerBottom
                        + " expectedFooterBottomAfter="
                        + (footerBottom - targetClearance)
                        + " navigationTop="
                        + navigationTop
                        + " activity="
                        + activity.getClass().getName()
        );
    }

    private static void restoreDrawerStickyFooterClearance(
            View stickyFooter
    ) {
        if (stickyFooter == null) return;

        Float originalTranslationY =
                STICKY_FOOTER_ORIGINAL_TRANSLATION_Y.get(stickyFooter);
        if (originalTranslationY == null) return;

        if (
                Float.compare(
                        stickyFooter.getTranslationY(),
                        originalTranslationY
                ) != 0
        ) {
            stickyFooter.setTranslationY(originalTranslationY);
            stickyFooter.invalidate();
        }

        STICKY_FOOTER_CLEARANCE.remove(stickyFooter);
        STICKY_FOOTER_ORIGINAL_TRANSLATION_Y.remove(stickyFooter);
    }

    private static boolean shouldApply(Activity activity) {
        return activity != null
                && Build.VERSION.SDK_INT >= 23
                && activity.getApplicationInfo() != null
                && activity.getApplicationInfo().targetSdkVersion >= TARGET_SDK_EDGE_TO_EDGE;
    }

    private static View findViewByName(Activity activity, String name) {
        int id = activity.getResources().getIdentifier(name, "id", activity.getPackageName());
        if (id == 0) return null;
        return activity.findViewById(id);
    }

    private static void applyBottomInsetPadding(final View view, final boolean allowScrollIntoPadding) {
        if (view == null) return;

        saveOriginalPadding(view);

        if (allowScrollIntoPadding && view instanceof ViewGroup) {
            ((ViewGroup) view).setClipToPadding(false);
        }

        view.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                try {
                    applyBottomInsetPaddingNow(v, insets);
                } catch (Throwable ignored) {
                }
                return insets;
            }
        });

        view.post(new Runnable() {
            @Override
            public void run() {
                try {
                    applyBottomInsetPaddingNow(view, getBestCurrentInsets(view));
                    view.requestApplyInsets();
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private static void applySafeAreaPadding(final View view) {
        if (view == null) return;

        saveOriginalPadding(view);

        view.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                try {
                    applySafeAreaPaddingNow(v, insets);
                    // The padded root already clears the system bars. Consume
                    // the insets so fitsSystemWindows children, such as the
                    // image viewer toolbar, do not add the same top inset again.
                    return consumeSafeAreaInsets(insets);
                } catch (Throwable ignored) {
                }
                return insets;
            }
        });

        view.post(new Runnable() {
            @Override
            public void run() {
                try {
                    applySafeAreaPaddingNow(view, getBestCurrentInsets(view));
                    view.requestApplyInsets();
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private static WindowInsets consumeSafeAreaInsets(WindowInsets insets) {
        if (insets == null) return null;
        if (Build.VERSION.SDK_INT >= 30) {
            return WindowInsets.CONSUMED;
        }
        WindowInsets consumed = insets.consumeSystemWindowInsets();
        if (Build.VERSION.SDK_INT >= 28) {
            consumed = consumed.consumeDisplayCutout();
        }
        return consumed;
    }

    private static void applySafeAreaPaddingNow(View view, WindowInsets insets) {
        if (view == null) return;

        saveOriginalPadding(view);

        Padding original = ORIGINAL_PADDING.get(view);
        if (original == null) return;

        WindowInsets effectiveInsets =
                insets != null ? insets : getBestCurrentInsets(view);

        int leftInset = 0;
        int topInset = 0;
        int rightInset = 0;
        int bottomInset = 0;

        if (effectiveInsets != null) {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets safeInsets = effectiveInsets.getInsets(
                        WindowInsets.Type.systemBars()
                                | WindowInsets.Type.displayCutout()
                );
                if (safeInsets != null) {
                    leftInset = safeInsets.left;
                    topInset = safeInsets.top;
                    rightInset = safeInsets.right;
                    bottomInset = safeInsets.bottom;
                }
            } else {
                leftInset = effectiveInsets.getSystemWindowInsetLeft();
                topInset = effectiveInsets.getSystemWindowInsetTop();
                rightInset = effectiveInsets.getSystemWindowInsetRight();
                bottomInset = effectiveInsets.getSystemWindowInsetBottom();

                if (Build.VERSION.SDK_INT >= 28) {
                    android.view.DisplayCutout cutout =
                            effectiveInsets.getDisplayCutout();
                    if (cutout != null) {
                        leftInset = Math.max(
                                leftInset,
                                cutout.getSafeInsetLeft()
                        );
                        topInset = Math.max(
                                topInset,
                                cutout.getSafeInsetTop()
                        );
                        rightInset = Math.max(
                                rightInset,
                                cutout.getSafeInsetRight()
                        );
                        bottomInset = Math.max(
                                bottomInset,
                                cutout.getSafeInsetBottom()
                        );
                    }
                }
            }
        }

        int targetLeft = original.left + Math.max(0, leftInset);
        int targetTop = original.top + Math.max(0, topInset);
        int targetRight = original.right + Math.max(0, rightInset);
        int targetBottom = original.bottom + Math.max(0, bottomInset);

        if (
                view.getPaddingLeft() == targetLeft
                        && view.getPaddingTop() == targetTop
                        && view.getPaddingRight() == targetRight
                        && view.getPaddingBottom() == targetBottom
        ) {
            return;
        }

        view.setPadding(
                targetLeft,
                targetTop,
                targetRight,
                targetBottom
        );
        view.requestLayout();
        view.invalidate();
    }

    private static void saveOriginalPadding(View view) {
        if (!ORIGINAL_PADDING.containsKey(view)) {
            ORIGINAL_PADDING.put(view, new Padding(
                    view.getPaddingLeft(),
                    view.getPaddingTop(),
                    view.getPaddingRight(),
                    view.getPaddingBottom()
            ));
        }
    }

    private static void applyBottomInsetPaddingNow(View view, WindowInsets insets) {
        if (view == null) return;

        saveOriginalPadding(view);

        Padding original = ORIGINAL_PADDING.get(view);
        if (original == null) return;

        int bottomInset = getEffectiveNavigationBottomInset(view, insets);

        int targetBottomPadding =
                original.bottom + Math.max(0, bottomInset);

        if (
                view.getPaddingLeft() == original.left
                        && view.getPaddingTop() == original.top
                        && view.getPaddingRight() == original.right
                        && view.getPaddingBottom() == targetBottomPadding
        ) {
            return;
        }

        view.setPadding(
                original.left,
                original.top,
                original.right,
                targetBottomPadding
        );

        view.requestLayout();
        view.invalidate();
    }

    private static void nudgeBottomVisibleChildAboveNavigationBar(final View view) {
        if (!(view instanceof ViewGroup)) return;

        view.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (view.canScrollVertically(1)) return;

                    ViewGroup group = (ViewGroup) view;
                    int childCount = group.getChildCount();
                    if (childCount <= 0) return;

                    View lastChild = group.getChildAt(childCount - 1);
                    if (lastChild == null) return;

                    int bottomInset = getEffectiveNavigationBottomInset(view, getBestCurrentInsets(view));
                    if (bottomInset <= 0) return;

                    int targetBottom = view.getHeight() - bottomInset;
                    int overlap = lastChild.getBottom() - targetBottom;

                    if (overlap > 0) {
                        view.scrollBy(0, overlap);
                    }
                } catch (Throwable ignored) {
                }
            }
        });
    }

    static int navigationBottomInset(View view) {
        return getEffectiveNavigationBottomInset(view, getBestCurrentInsets(view));
    }

    private static int getEffectiveNavigationBottomInset(View view, WindowInsets insets) {
        int bottomInset = getNavigationBottomInset(insets);
        if (bottomInset <= 0) {
            bottomInset = getNavigationBottomInset(getBestCurrentInsets(view));
        }

        if (bottomInset <= 0 && isThreeButtonNavigation(view)) {
            bottomInset = getNavigationBarHeight(view);
        }

        return bottomInset;
    }

    private static WindowInsets getBestCurrentInsets(View view) {
        if (view == null || Build.VERSION.SDK_INT < 23) return null;

        WindowInsets insets = view.getRootWindowInsets();
        if (insets != null) return insets;

        View root = view.getRootView();
        if (root != null) {
            return root.getRootWindowInsets();
        }

        return null;
    }

    private static int getNavigationBottomInset(WindowInsets insets) {
        if (insets == null) return 0;

        if (Build.VERSION.SDK_INT >= 30) {
            Insets navigationInsets = insets.getInsets(WindowInsets.Type.navigationBars());
            return navigationInsets == null ? 0 : navigationInsets.bottom;
        }

        return insets.getSystemWindowInsetBottom();
    }


    public static void applyCommentsSystemBars(Activity activity) {
        scheduleCommentsSystemBars(activity);
    }

    private static void scheduleCommentsSystemBars(final Activity activity) {
        try {
            if (!shouldApply(activity) || !isToolbarSurfaceActivity(activity)) return;

            final View decor = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
            if (decor == null) return;

            Runnable apply = new Runnable() {
                @Override
                public void run() {
                    try {
                        applyCommentsSystemBarsNow(activity);
                    } catch (Throwable ignored) {
                    }
                }
            };

            decor.post(apply);
            decor.postDelayed(apply, 100L);
            decor.postDelayed(apply, 350L);
            decor.postDelayed(apply, 1000L);
        } catch (Throwable ignored) {
        }
    }

    private static boolean isCommentsActivity(Activity activity) {
        if (activity == null) return false;

        String className = activity.getClass().getName();
        return COMMENTS_ACTIVITY_NAME.equals(className)
                || className.endsWith(".ui.comments.CommentsActivity");
    }

    private static boolean isToolbarSurfaceActivity(Activity activity) {
        if (activity == null) return false;

        String className = activity.getClass().getName();
        if (isCommentsActivity(activity)) return true;

        // Issue #37 also reproduces on the main feed/subreddit listing activity.
        // Keep scope explicit instead of applying to all Boost activities.
        if (isMainListingActivity(activity)) return true;

        // Settings was part of the original report. Keep it scoped to Boost settings
        // activities and let the toolbar detector decide whether there is a top toolbar.
        if (className.endsWith(".ui.settings.SettingsActivity")) return true;
        if (className.contains(".ui.settings.")) return true;

        // Issue #37 post-release validation after 1.4.70 found the same
        // light-theme systembar/header foreground mismatch on these explicit
        // non-media Boost surfaces. Keep this allowlist narrow; do not apply
        // globally and do not change media/fullscreen behavior.
        if (isExtendedToolbarSurfaceActivity(className)) return true;

        // Do not touch media/fullscreen surfaces where white controls over content
        // are expected and where a forced dark foreground would be wrong.
        if (className.contains(".ui.activities.Media")) return false;
        if (className.contains(".ui.media.")) return false;
        if (className.contains(".ui.gallery.")) return false;
        if (className.contains(".ui.video.")) return false;
        if (className.contains(".ui.images.")) return false;

        return false;
    }

    private static boolean isMainListingActivity(Activity activity) {
        if (activity == null) return false;

        String className = activity.getClass().getName();
        return className.endsWith(".ui.submissions.subreddit.MainActivity");
    }

    private static boolean isExtendedToolbarSurfaceActivity(String className) {
        if (className == null) return false;

        return className.endsWith(".ui.search.SearchGenericActivity")
                || className.endsWith(".ui.search.GoToGenericActivity")
                || className.endsWith(".ui.messages.MessagesActivity")
                || className.endsWith(".ui.profile.UserActivity");
    }

    private static void applyCommentsSystemBarsNow(Activity activity) {
        try {
            if (!shouldApply(activity) || !isToolbarSurfaceActivity(activity)) return;

            Window window = activity.getWindow();
            if (window == null) return;

            View decor = window.getDecorView();
            if (decor == null) return;

            int statusHeight = getStatusBarTopInset(decor);
            if (statusHeight <= 0) {
                statusHeight = getStatusBarHeight(activity);
            }
            if (statusHeight <= 0) return;

            int color = findCommentsToolbarSurfaceColor(activity, decor, statusHeight);

            installStatusBarSurface(decor, statusHeight, color);

            if (Build.VERSION.SDK_INT >= 21) {
                window.setStatusBarColor(color);
            }
            applyLightStatusBarFlag(decor, color);
            applyMainActivityNavigationBarSurface(activity, window, decor, color);
            applyCommentsToolbarForeground(activity, decor, statusHeight, color);
        } catch (Throwable ignored) {
        }
    }

    private static void installStatusBarSurface(View decor, int statusHeight, int color) {
        if (!(decor instanceof ViewGroup)) return;

        ViewGroup group = (ViewGroup) decor;
        View surface = decor.findViewWithTag(COMMENTS_SYSTEM_BAR_MARKER);

        if (surface == null) {
            surface = new View(decor.getContext());
            surface.setTag(COMMENTS_SYSTEM_BAR_MARKER);
            surface.setClickable(false);
            surface.setFocusable(false);
            if (Build.VERSION.SDK_INT >= 16) {
                surface.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            }

            if (group instanceof FrameLayout) {
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        statusHeight,
                        Gravity.TOP
                );
                ((FrameLayout) group).addView(surface, params);
            } else {
                group.addView(surface, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        statusHeight
                ));
            }
        }

        ViewGroup.LayoutParams params = surface.getLayoutParams();
        if (params != null && params.height != statusHeight) {
            params.height = statusHeight;
            surface.setLayoutParams(params);
        }

        surface.setBackgroundColor(color);
        surface.bringToFront();
        surface.invalidate();
    }

    private static int findCommentsToolbarSurfaceColor(Activity activity, View decor, int statusHeight) {
        int color = findNamedToolbarColor(activity);
        if (isOpaque(color)) return color;

        color = findTopSurfaceColor(decor, statusHeight);
        if (isOpaque(color)) return color;

        color = resolveThemeColor(activity, "colorSurface", 0);
        if (isOpaque(color)) return color;

        color = resolveThemeColor(activity, "colorBackground", 0);
        if (isOpaque(color)) return color;

        color = resolveAndroidThemeColor(activity, android.R.attr.colorBackground, 0);
        if (isOpaque(color)) return color;

        return isNightMode(activity) ? Color.BLACK : Color.WHITE;
    }

    private static int findNamedToolbarColor(Activity activity) {
        String[] names = new String[] {
                "toolbar",
                "action_bar",
                "actionbar",
                "appbar",
                "app_bar",
                "comments_toolbar",
                "toolbar_comments"
        };

        for (String name : names) {
            View view = findViewByName(activity, name);
            int color = findSolidBackgroundColor(view);
            if (isOpaque(color)) return color;

            View parent = view == null ? null : view.getParent() instanceof View ? (View) view.getParent() : null;
            color = findSolidBackgroundColor(parent);
            if (isOpaque(color)) return color;
        }

        return 0;
    }

    private static int findTopSurfaceColor(View root, int statusHeight) {
        if (root == null) return 0;

        int[] best = new int[] {0, Integer.MAX_VALUE};
        collectTopSurfaceColor(root, statusHeight, best);
        return best[0];
    }

    private static void collectTopSurfaceColor(View view, int statusHeight, int[] best) {
        if (view == null || view.getVisibility() != View.VISIBLE) return;

        int width = view.getWidth();
        int height = view.getHeight();

        if (width > 0 && height > 0) {
            int[] location = new int[2];
            view.getLocationOnScreen(location);

            int top = location[1];
            int bottom = top + height;
            int screenWidth = view.getRootView() == null ? width : view.getRootView().getWidth();

            boolean nearToolbar =
                    screenWidth > 0
                            && width >= (screenWidth * 2 / 3)
                            && top <= statusHeight + 260
                            && bottom >= statusHeight + 20;

            if (nearToolbar) {
                int color = findSolidBackgroundColor(view);
                if (isOpaque(color)) {
                    int score = Math.abs(top - statusHeight);
                    if (score < best[1]) {
                        best[0] = color;
                        best[1] = score;
                    }
                }
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectTopSurfaceColor(group.getChildAt(i), statusHeight, best);
            }
        }
    }

    private static int findSolidBackgroundColor(View view) {
        if (view == null) return 0;

        Drawable background = view.getBackground();
        if (background instanceof ColorDrawable) {
            int color = ((ColorDrawable) background).getColor();
            if (isOpaque(color)) return color;
        }

        return 0;
    }

    private static int resolveThemeColor(Activity activity, String attrName, int fallback) {
        try {
            int attr = activity.getResources().getIdentifier(attrName, "attr", activity.getPackageName());
            if (attr == 0) return fallback;
            return resolveAndroidThemeColor(activity, attr, fallback);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static int resolveAndroidThemeColor(Activity activity, int attr, int fallback) {
        try {
            TypedValue value = new TypedValue();
            if (!activity.getTheme().resolveAttribute(attr, value, true)) return fallback;

            if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return value.data;
            }

            if (value.resourceId != 0) {
                if (Build.VERSION.SDK_INT >= 23) {
                    return activity.getResources().getColor(value.resourceId, activity.getTheme());
                }
                return activity.getResources().getColor(value.resourceId);
            }
        } catch (Throwable ignored) {
        }

        return fallback;
    }

    private static boolean isOpaque(int color) {
        return Color.alpha(color) >= 200;
    }

    private static boolean isNightMode(Activity activity) {
        try {
            int mode = activity.getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void applyLightStatusBarFlag(View decor, int color) {
        if (decor == null || Build.VERSION.SDK_INT < 23) return;

        int flags = decor.getSystemUiVisibility();
        if (isLightColor(color)) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        } else {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        decor.setSystemUiVisibility(flags);
    }

    private static void applyMainActivityNavigationBarSurface(Activity activity, Window window, View decor, int fallbackColor) {
        try {
            if (!shouldApply(activity) || !isMainListingActivity(activity)) return;
            if (window == null || decor == null || Build.VERSION.SDK_INT < 21) return;

            int color = findMainNavigationSurfaceColor(activity, decor);
            if (!isOpaque(color)) color = fallbackColor;
            if (!isOpaque(color)) return;

            int bottomInset = getEffectiveNavigationBottomInset(decor, getBestCurrentInsets(decor));
            installMainNavigationBarSurface(decor, bottomInset, color);

            // Android edge-to-edge/gesture navigation may ignore opaque nav-bar colors.
            // Keep the system area transparent and provide the visible surface ourselves.
            window.setNavigationBarColor(Color.TRANSPARENT);
            if (Build.VERSION.SDK_INT >= 28) {
                window.setNavigationBarDividerColor(Color.TRANSPARENT);
            }
            if (Build.VERSION.SDK_INT >= 29) {
                window.setNavigationBarContrastEnforced(false);
            }
            applyLightNavigationBarFlag(decor, color);

            // Keep markers reachable in the extension artifact without adding extra UI.
            if (MAIN_NAV_BAR_SURFACE_MARKER_V3.length() == 0
                    || MAIN_NAV_BAR_SURFACE_OVERLAY_MARKER_V4.length() == 0
                    || EXTENDED_ACTIVITY_SURFACE_SCOPE_MARKER_V5.length() == 0) {
                decor.invalidate();
            }
        } catch (Throwable ignored) {
        }
    }

    private static void installMainNavigationBarSurface(View decor, int bottomInset, int color) {
        try {
            if (!(decor instanceof FrameLayout)) return;
            if (bottomInset <= 0) return;

            FrameLayout group = (FrameLayout) decor;
            View surface = decor.findViewWithTag(MAIN_NAV_BAR_SURFACE_OVERLAY_MARKER_V4);

            // The canonical bottom navigation paints this strip with its own
            // surface color. Only act as the fallback when it is not shown,
            // instead of both overlays racing with bringToFront().
            View navigationUnderlay = decor.findViewWithTag(NAVIGATION_DECOR_UNDERLAY_TAG);
            if (navigationUnderlay != null && navigationUnderlay.getVisibility() == View.VISIBLE) {
                if (surface != null) {
                    surface.setVisibility(View.GONE);
                }
                return;
            }

            if (surface == null) {
                surface = new View(decor.getContext());
                surface.setTag(MAIN_NAV_BAR_SURFACE_OVERLAY_MARKER_V4);
                surface.setClickable(false);
                surface.setFocusable(false);
                if (Build.VERSION.SDK_INT >= 16) {
                    surface.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                }

                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        bottomInset,
                        Gravity.BOTTOM
                );
                group.addView(surface, params);
            }

            ViewGroup.LayoutParams params = surface.getLayoutParams();
            if (params != null && params.height != bottomInset) {
                params.height = bottomInset;
                surface.setLayoutParams(params);
            }

            surface.setBackgroundColor(color);
            surface.bringToFront();
            surface.invalidate();
        } catch (Throwable ignored) {
        }
    }

    private static int findMainNavigationSurfaceColor(Activity activity, View decor) {
        int color = findNamedBottomSurfaceColor(activity);
        if (isOpaque(color)) return color;

        color = findBottomSurfaceColor(decor);
        if (isOpaque(color)) return color;

        color = resolveThemeColor(activity, "colorSurface", 0);
        if (isOpaque(color)) return color;

        color = resolveThemeColor(activity, "colorBackground", 0);
        if (isOpaque(color)) return color;

        color = resolveAndroidThemeColor(activity, android.R.attr.colorBackground, 0);
        if (isOpaque(color)) return color;

        return isNightMode(activity) ? Color.BLACK : Color.WHITE;
    }

    private static int findNamedBottomSurfaceColor(Activity activity) {
        String[] names = new String[] {
                "bottom_bar",
                "bottomBar",
                "bottom_navigation",
                "bottom_navigation_view",
                "bottom_nav",
                "navigation_bar",
                "navigation",
                "navigation_view",
                "tab_layout",
                "tabs"
        };

        for (String name : names) {
            View view = findViewByName(activity, name);
            int color = findSolidBackgroundColor(view);
            if (isOpaque(color)) return color;

            View parent = view == null ? null : view.getParent() instanceof View ? (View) view.getParent() : null;
            color = findSolidBackgroundColor(parent);
            if (isOpaque(color)) return color;
        }

        return 0;
    }

    private static int findBottomSurfaceColor(View root) {
        if (root == null) return 0;

        int[] best = new int[] {0, Integer.MAX_VALUE};
        collectBottomSurfaceColor(root, root.getHeight(), root.getWidth(), best);
        return best[0];
    }

    private static void collectBottomSurfaceColor(View view, int rootHeight, int rootWidth, int[] best) {
        if (view == null || view.getVisibility() != View.VISIBLE) return;

        try {
            int color = findSolidBackgroundColor(view);
            if (isOpaque(color) && view.getWidth() >= Math.max(1, rootWidth / 2)) {
                int top = view.getTop();
                int bottom = view.getBottom();
                boolean nearBottom = bottom >= rootHeight - Math.max(96, rootHeight / 12);
                boolean plausibleHeight = view.getHeight() >= 24 && view.getHeight() <= Math.max(320, rootHeight / 4);
                if (nearBottom && plausibleHeight) {
                    int score = Math.abs(rootHeight - bottom) + Math.abs(view.getHeight() - 112);
                    if (score < best[1]) {
                        best[0] = color;
                        best[1] = score;
                    }
                }
            }

            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    collectBottomSurfaceColor(group.getChildAt(i), rootHeight, rootWidth, best);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void applyLightNavigationBarFlag(View decor, int color) {
        if (decor == null || Build.VERSION.SDK_INT < 26) return;

        int flags = decor.getSystemUiVisibility();
        if (isLightColor(color)) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        } else {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        decor.setSystemUiVisibility(flags);
    }

    private static boolean isLightColor(int color) {
        double r = Color.red(color) / 255.0;
        double g = Color.green(color) / 255.0;
        double b = Color.blue(color) / 255.0;

        r = r <= 0.03928 ? r / 12.92 : Math.pow((r + 0.055) / 1.055, 2.4);
        g = g <= 0.03928 ? g / 12.92 : Math.pow((g + 0.055) / 1.055, 2.4);
        b = b <= 0.03928 ? b / 12.92 : Math.pow((b + 0.055) / 1.055, 2.4);

        return (0.2126 * r + 0.7152 * g + 0.0722 * b) > 0.5;
    }

    private static int getStatusBarTopInset(View view) {
        try {
            WindowInsets insets = getBestCurrentInsets(view);
            if (insets == null) return 0;

            if (Build.VERSION.SDK_INT >= 30) {
                Insets statusInsets = insets.getInsets(WindowInsets.Type.statusBars());
                return statusInsets == null ? 0 : statusInsets.top;
            }

            return insets.getSystemWindowInsetTop();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static int getStatusBarHeight(Activity activity) {
        try {
            int id = activity.getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (id > 0) {
                return activity.getResources().getDimensionPixelSize(id);
            }
        } catch (Throwable ignored) {
        }

        return 0;
    }


    private static void applyCommentsToolbarForeground(Activity activity, View decor, int statusHeight, int surfaceColor) {
        try {
            if (!shouldApply(activity) || !isToolbarSurfaceActivity(activity)) return;

            int foreground = isLightColor(surfaceColor) ? Color.rgb(32, 33, 36) : Color.WHITE;
            View toolbar = findCommentsToolbarView(activity, decor, statusHeight);

            if (toolbar != null) {
                toolbar.setTag(isCommentsActivity(activity) ? COMMENTS_TOOLBAR_FOREGROUND_MARKER : TOOLBAR_SURFACE_FOREGROUND_MARKER_V2);
                tintToolbarObject(toolbar, foreground);
                tintToolbarTree(toolbar, foreground);
            }
        } catch (Throwable ignored) {
        }
    }

    private static View findCommentsToolbarView(Activity activity, View decor, int statusHeight) {
        View named = findNamedToolbarView(activity);
        if (named != null) return named;

        return findTopToolbarCandidate(decor, statusHeight);
    }

    private static View findNamedToolbarView(Activity activity) {
        String[] names = new String[] {
                "toolbar",
                "action_bar",
                "actionbar",
                "appbar",
                "app_bar",
                "comments_toolbar",
                "toolbar_comments"
        };

        for (String name : names) {
            View view = findViewByName(activity, name);
            if (isToolbarSized(view)) return view;

            View parent = view == null ? null : view.getParent() instanceof View ? (View) view.getParent() : null;
            if (isToolbarSized(parent)) return parent;
        }

        return null;
    }

    private static View findTopToolbarCandidate(View root, int statusHeight) {
        if (root == null) return null;

        View[] best = new View[] {null};
        int[] bestScore = new int[] {Integer.MAX_VALUE};
        collectTopToolbarCandidate(root, statusHeight, best, bestScore);
        return best[0];
    }

    private static void collectTopToolbarCandidate(View view, int statusHeight, View[] best, int[] bestScore) {
        if (view == null || view.getVisibility() != View.VISIBLE) return;

        if (isToolbarSized(view)) {
            int[] location = new int[2];
            view.getLocationOnScreen(location);

            int top = location[1];
            int bottom = top + view.getHeight();
            int score = Math.abs(top - statusHeight) + Math.abs(view.getHeight() - 112);

            boolean nearTop = top <= statusHeight + 120 && bottom >= statusHeight + 24;
            if (nearTop && score < bestScore[0]) {
                best[0] = view;
                bestScore[0] = score;
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectTopToolbarCandidate(group.getChildAt(i), statusHeight, best, bestScore);
            }
        }
    }

    private static boolean isToolbarSized(View view) {
        if (view == null) return false;

        int width = view.getWidth();
        int height = view.getHeight();
        if (width <= 0 || height <= 0) return false;

        View root = view.getRootView();
        int rootWidth = root == null ? width : root.getWidth();
        int rootHeight = root == null ? height : root.getHeight();

        if (rootWidth <= 0 || rootHeight <= 0) return false;
        if (width < rootWidth * 2 / 3) return false;

        return height >= 40 && height <= 260 && height < rootHeight / 3;
    }

    private static void tintToolbarObject(View view, int color) {
        if (view == null) return;
        BoostNativeNavigation.tintToolbar(view, color);
    }

    private static void tintToolbarTree(View view, int color) {
        if (view == null || view.getVisibility() != View.VISIBLE) return;

        try {
            if (view instanceof TextView) {
                TextView textView = (TextView) view;
                textView.setTextColor(color);
                textView.setHintTextColor(color);
                tintTextViewDrawables(textView, color);
            } else if (view instanceof ImageView) {
                ((ImageView) view).setColorFilter(color, PorterDuff.Mode.SRC_IN);
            }

            tintToolbarObject(view, color);
        } catch (Throwable ignored) {
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                tintToolbarTree(group.getChildAt(i), color);
            }
        }
    }

    private static void tintTextViewDrawables(TextView textView, int color) {
        if (textView == null) return;

        try {
            Drawable[] drawables;
            if (Build.VERSION.SDK_INT >= 17) {
                drawables = textView.getCompoundDrawablesRelative();
            } else {
                drawables = textView.getCompoundDrawables();
            }

            for (Drawable drawable : drawables) {
                tintDrawable(drawable, color);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void tintDrawable(Drawable drawable, int color) {
        if (drawable == null) return;

        try {
            Drawable mutable = drawable.mutate();
            if (Build.VERSION.SDK_INT >= 21) {
                mutable.setTint(color);
            } else {
                mutable.setColorFilter(color, PorterDuff.Mode.SRC_IN);
            }
        } catch (Throwable ignored) {
        }
    }



    private static boolean isThreeButtonNavigation(View view) {
        try {
            return android.provider.Settings.Secure.getInt(
                    view.getContext().getContentResolver(),
                    "navigation_mode",
                    -1
            ) == 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int getNavigationBarHeight(View view) {
        try {
            int id = view.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
            if (id > 0) {
                return view.getResources().getDimensionPixelSize(id);
            }
        } catch (Throwable ignored) {
        }

        return 0;
    }

    private static final class Padding {
        final int left;
        final int top;
        final int right;
        final int bottom;

        Padding(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }
}
