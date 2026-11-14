/*
 * Copyright 2026 brealorg.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.boost.utils;

import app.morphe.extension.boost.api.BoostFieldNames;
import app.morphe.extension.boost.api.ProfileAvatarDrawable;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;
import android.widget.FrameLayout;
import android.util.Log;

import app.morphe.extension.boost.navigation.BoostNativeNavigation;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.WeakHashMap;

/**
 * Configures the BottomNavigationView inflated from activity_search_generic.
 * No Material view is constructed dynamically.
 */
public final class BoostSearchBottomNavigation {
    private static final String DECOR_UNDERLAY_MARKER =
            "MORPHE_BOOST_BOTTOM_NAV_DECOR_UNDERLAY_V73";
    private static final String INBOX_PROFILE_LIFECYCLE_MARKER =
            "MORPHE_BOOST_INBOX_PROFILE_UNDERLAY_LIFECYCLE_V74";
    private static final String INBOX_PROFILE_LATE_RETRY_MARKER =
            "MORPHE_BOOST_INBOX_PROFILE_UNDERLAY_LATE_RETRY_V74B";
    private static final String VISIBLE_NAV_FALLBACK_MARKER =
            "MORPHE_BOOST_VISIBLE_BOTTOM_NAV_FALLBACK_V751";
    private static volatile String SEARCH_ROUTE_MARKER =
            "MORPHE_BOOST_SEARCH_BACK_STACK_V1_PRESERVE_CALLER";
    private static final String SEARCH_CONTEXT_MARKER =
            "MORPHE_BOOST_SEARCH_CONTEXT_ISSUE94_SUBREDDIT_PARCELABLE_V3";
    private static final String SEARCH_SCOPE_HINT_MARKER =
            "MORPHE_BOOST_SEARCH_SCOPE_HINT_ISSUE94_POST_ONCREATE_V2";
    private static final String SEARCH_INITIAL_FOCUS_MARKER =
            "MORPHE_BOOST_SEARCH_INITIAL_FOCUS_PREFERENCE_ISSUE86_V2";
    private static final String SEARCH_INITIAL_FOCUS_PREFERENCE_KEY =
            "morphe_boost_search_open_keyboard_on_entry";
    private static final String SEARCH_FRESH_ENTRY_MARKER =
            "MORPHE_BOOST_SEARCH_FRESH_ACTIVITY_ISSUE86_V2";
    private static final String SEARCH_RESELECT_MARKER =
            "MORPHE_BOOST_SEARCH_RESELECT_ISSUE86_V1";
    private static final String RESELECT_RECOVERY_MARKER =
            "MORPHE_BOOST_BOTTOM_NAV_RESELECT_RECOVERY_ISSUE169_V2";
    private static final String FOREIGN_PROFILE_ROUTE_MARKER =
            "MORPHE_BOOST_FOREIGN_PROFILE_TO_OWN_PROFILE_ROUTE_V1";
    private static volatile String CANONICAL_NAV_MARKER =
            "MORPHE_BOOST_CANONICAL_BOTTOM_NAV_V10028_STABLE_ROOT_INSET";
    private static final String NATIVE_COORDINATOR_MARKER =
            "MORPHE_BOOST_NATIVE_COORDINATOR_BOTTOM_NAV_ISSUE97_V1";
    private static final String NATIVE_COORDINATOR_INSET_MARKER =
            "MORPHE_BOOST_NATIVE_COORDINATOR_SEARCH_GOTO_INSET_V2";
    private static final String NATIVE_BOTTOM_NAV_HIDE_PREFERENCE_MARKER =
            "MORPHE_BOOST_NATIVE_BOTTOM_NAV_HIDE_PREFERENCE_ACTIVE_PATH_ISSUE97_V7";
    private static final String NATIVE_PROFILE_LONG_PRESS_MARKER =
            "MORPHE_BOOST_NATIVE_PROFILE_LONGPRESS_ISSUE97_V2";
    private static final String NATIVE_SUBSCRIPTIONS_LONG_PRESS_MARKER =
            "MORPHE_BOOST_NATIVE_SUBSCRIPTIONS_LONGPRESS_ISSUE135_V1";
    private static final String SUBREDDIT_SELECTION_MARKER =
            "MORPHE_BOOST_SUBREDDIT_BOTTOM_NAV_SELECTION_ISSUE134_V1";
    private static final String CURRENT_SUBREDDIT_SUBSCRIPTIONS_MARKER =
            "MORPHE_BOOST_SUBSCRIPTIONS_CURRENT_SUBREDDIT_ISSUE141_V2_NATIVE_J6";
    private static final String ACTIVITY_STACK_STATE_MARKER =
            "MORPHE_BOOST_BOTTOM_NAV_ACTIVITY_STACK_V2_PRESERVE_CALLER";
    private static final String HOME_TASK_ROOT_MARKER =
            "MORPHE_BOOST_HOME_TASK_ROOT_ISSUE198_V1";
    private static final String HOME_RESELECT_TOP_MARKER =
            "MORPHE_BOOST_HOME_RESELECT_FAB_GO_TOP_V2";
    private static final java.util.WeakHashMap<View, Boolean>
            MAIN_LANDSCAPE_INSET_GUARDS =
                    new java.util.WeakHashMap<>();
    private static final java.util.WeakHashMap<View, Boolean>
            OWNED_MENU_INSTALLED =
                    new java.util.WeakHashMap<>();
    private static Bitmap sharedProfileAvatar;
    private static String sharedProfileAvatarUsername;
    /**
     * Item views that already carry our long-press listener. Every configure
     * (each onResume and feed switch) schedules the attach again; a rebuilt
     * menu has new item views and is attached anew.
     */
    private static final java.util.WeakHashMap<View, Boolean>
            LONG_PRESS_ITEMS =
                    new java.util.WeakHashMap<>();
    /** Navigation views whose selection listeners are already ours. */
    private static final java.util.WeakHashMap<View, Boolean>
            LISTENERS_ATTACHED =
                    new java.util.WeakHashMap<>();
    private static final String SHARED_INBOX_BADGE_MARKER =
            "MORPHE_BOOST_INBOX_BADGE_SHARED_STATE_V4D";
    private static volatile Integer SHARED_INBOX_BADGE_COUNT;
    private static final String SOURCE_SELECTION_RETAINED_MARKER =
            "MORPHE_BOOST_BOTTOM_NAV_SOURCE_SELECTION_RETAINED_ISSUE117_V2";
    private static final String PERSISTED_INBOX_BADGE_MARKER =
            "MORPHE_BOOST_INBOX_BADGE_COLD_START_PERSISTENCE_V6";
    private static final String PERSISTED_INBOX_BADGE_PREFERENCES =
            "morphe_boost_bottom_navigation";
    private static final String PERSISTED_INBOX_BADGE_KEY =
            "last_known_inbox_badge_count";

    private static final int UNDERLAY_RETRY_LIMIT = 24;
    private static final long UNDERLAY_RETRY_DELAY_MS = 75L;
    private static final String DECOR_UNDERLAY_TAG =
            "morphe_boost_bottom_navigation_decor_underlay";
    private static final String NATIVE_LONG_PRESS_CROSS_ACTIVITY_MARKER =
            "MORPHE_BOOST_NATIVE_LONGPRESS_CROSS_ACTIVITY_V1";
    private static final String PROFILE_LONG_PRESS_DIALOG_BRIDGE_MARKER =
            "MORPHE_BOOST_PROFILE_LONGPRESS_DIALOG_BRIDGE_V2";
    private static final int PROFILE_SELECTOR_TITLE_RESOURCE_ID =
            0x7f130116;
    private static final Object PROFILE_SELECTION_LOCK =
            new Object();
    private static volatile Integer PENDING_PROFILE_SELECTION_INDEX;
    private static final String TAG = "MorpheSearchNav";
    private static final int SEARCH_INITIAL_FOCUS_RETRY_LIMIT = 24;
    private static final long SEARCH_INITIAL_FOCUS_RETRY_DELAY_MS =
            75L;
    private static final int NATIVE_PROFILE_LONG_PRESS_RETRY_LIMIT = 24;
    private static final long NATIVE_PROFILE_LONG_PRESS_RETRY_DELAY_MS =
            75L;
    private static final int NATIVE_SUBSCRIPTIONS_LONG_PRESS_RETRY_LIMIT =
            24;
    private static final long NATIVE_SUBSCRIPTIONS_LONG_PRESS_RETRY_DELAY_MS =
            75L;

    private static final String SEARCH_ACTIVITY =
            "com.rubenmayayo.reddit.ui.search.SearchGenericActivity";
    private static final String SEARCH_SUBMISSIONS_ACTIVITY =
            "com.rubenmayayo.reddit.ui.submissions.search.SearchSubmissionsActivity";
    private static final String GO_TO_ACTIVITY =
            "com.rubenmayayo.reddit.ui.search.GoToGenericActivity";

    private static final String MAIN_ACTIVITY =
            "com.rubenmayayo.reddit.ui.submissions.subreddit.MainActivity";
    private static final String SUBREDDIT_ACTIVITY =
            "com.rubenmayayo.reddit.ui.submissions.subreddit.SubredditActivity";
    private static final String SUBSCRIPTIONS_ACTIVITY =
            "com.rubenmayayo.reddit.ui.subscriptions.SubscriptionsActivity";
    private static final String MESSAGES_ACTIVITY =
            "com.rubenmayayo.reddit.ui.messages.MessagesActivity";

    private static final String USER_ACTIVITY =
            "com.rubenmayayo.reddit.ui.profile.UserActivity";
    private static final WeakHashMap<Activity, Boolean>
            NATIVE_NAVIGATION_VISIBILITY_REQUESTS =
                    new WeakHashMap<>();
    /**
     * Boost's own last U1() decision per activity, before the preference
     * gate. SubmissionsActivity forces false while the swipe post viewer is
     * shown; onResume must not override that.
     */
    private static final WeakHashMap<Activity, Boolean>
            RUNTIME_VISIBILITY_REQUESTS =
                    new WeakHashMap<>();


    private BoostSearchBottomNavigation() {
    }

    public static void install(Activity activity) {
        if (!isTarget(activity)) {
            return;
        }

        scheduleSearchScopeHint(activity);
        scheduleInitialSearchInputFocus(activity);

        int fallbackIndex = GO_TO_ACTIVITY.equals(
                activity.getClass().getName()
        ) ? 2 : 1;

        standardizeMaterialNavigation(
                activity,
                fallbackIndex
        );

        /*
         * Search and Go To do not extend Boost's DrawerActivity, so U1() never
         * runs there and the host XML's HideBottomViewOnScrollBehavior applied
         * regardless of "Hide on scroll". Apply the preference here and keep
         * the list clear of a bar that no longer hides.
         */
        View navigation = findNativeMaterialNavigation(activity);

        if (navigation != null) {
            lockOwnedNavigationHost(navigation, false);
        }

        View list = findViewByResourceName(activity, "recyclerview");

        if (list != null) {
            BoostNavContentClearance.apply(list);
        }
    }

    private static boolean shouldOpenKeyboardOnSearchEntry(
            Activity activity
    ) {
        if (activity == null) {
            return false;
        }

        try {
            return android.preference.PreferenceManager
                    .getDefaultSharedPreferences(activity)
                    .getBoolean(
                            SEARCH_INITIAL_FOCUS_PREFERENCE_KEY,
                            false
                    );
        } catch (Throwable error) {
            Log.w(
                    TAG,
                    "search initial focus preference failed marker="
                            + SEARCH_INITIAL_FOCUS_MARKER,
                    error
            );
            return false;
        }
    }

    private static void scheduleInitialSearchInputFocus(
            final Activity activity
    ) {
        if (
                activity == null
                        || !SEARCH_ACTIVITY.equals(
                                activity.getClass().getName()
                        )
        ) {
            return;
        }

        if (!shouldOpenKeyboardOnSearchEntry(activity)) {
            Log.i(
                    TAG,
                    "search initial focus skipped marker="
                            + SEARCH_INITIAL_FOCUS_MARKER
                            + " enabled=false"
            );
            return;
        }

        final View decor = activity
                .getWindow()
                .getDecorView();

        decor.post(new Runnable() {
            private int attempts;

            @Override
            public void run() {
                attempts++;

                if (activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }

                if (!activity.hasWindowFocus()) {
                    if (attempts < SEARCH_INITIAL_FOCUS_RETRY_LIMIT) {
                        decor.postDelayed(
                                this,
                                SEARCH_INITIAL_FOCUS_RETRY_DELAY_MS
                        );
                    } else {
                        Log.w(
                                TAG,
                                "search initial focus exhausted marker="
                                        + SEARCH_INITIAL_FOCUS_MARKER
                                        + " attempts="
                                        + attempts
                        );
                    }
                    return;
                }

                boolean focused = focusSearchInput(activity);

                Log.i(
                        TAG,
                        "search initial focus marker="
                                + SEARCH_INITIAL_FOCUS_MARKER
                                + " focused="
                                + focused
                                + " attempts="
                                + attempts
                );

                if (
                        !focused
                                && attempts
                                < SEARCH_INITIAL_FOCUS_RETRY_LIMIT
                ) {
                    decor.postDelayed(
                            this,
                            SEARCH_INITIAL_FOCUS_RETRY_DELAY_MS
                    );
                }
            }
        });
    }

    private static void scheduleSearchScopeHint(
            final Activity activity
    ) {
        if (
                activity == null
                        || !SEARCH_ACTIVITY.equals(
                                activity.getClass().getName()
                        )
        ) {
            return;
        }

        View decor = activity
                .getWindow()
                .getDecorView();

        decor.post(new Runnable() {
            @Override
            public void run() {
                applySearchScopeHint(activity);
            }
        });
    }

    private static void applySearchScopeHint(
            Activity activity
    ) {
        if (
                activity == null
                        || !SEARCH_ACTIVITY.equals(
                                activity.getClass().getName()
                        )
        ) {
            return;
        }

        try {
            Intent intent = activity.getIntent();
            if (intent == null) return;

            Object subscription =
                    intent.getParcelableExtra("subscription");
            if (subscription == null) return;

            Field subredditField = findField(
                    subscription.getClass(),
                    BoostFieldNames.SubscriptionViewModelCommunity
            );
            if (subredditField == null) return;

            subredditField.setAccessible(true);
            Object rawSubreddit =
                    subredditField.get(subscription);
            if (!(rawSubreddit instanceof String)) return;

            String subreddit =
                    ((String) rawSubreddit).trim();
            if (subreddit.length() == 0) return;

            Field inputField = findField(
                    activity.getClass(),
                    "searchEditText"
            );
            if (inputField == null) return;

            inputField.setAccessible(true);
            Object input = inputField.get(activity);
            if (!(input instanceof android.widget.TextView)) return;

            ((android.widget.TextView) input).setHint(
                    "Search in r/" + subreddit
            );

            Log.i(
                    TAG,
                    "search scope hint applied marker="
                            + SEARCH_SCOPE_HINT_MARKER
                            + " subreddit="
                            + subreddit
            );
        } catch (Throwable error) {
            Log.w(
                    TAG,
                    "search scope hint failed marker="
                            + SEARCH_SCOPE_HINT_MARKER,
                    error
            );
        }
    }

    private static void normalizeBottomInset(
            final View navigation
    ) {
        /*
         * activity_search_generic already ends above the gesture-navigation
         * area. Material NavigationBarView adds the same bottom inset once
         * more, moving the icons one system inset too high.
         */
        if (Build.VERSION.SDK_INT >= 21) {
            navigation.setOnApplyWindowInsetsListener(
                    new View.OnApplyWindowInsetsListener() {
                        @Override
                        public WindowInsets onApplyWindowInsets(
                                View view,
                                WindowInsets insets
                        ) {
                            view.setPadding(
                                    view.getPaddingLeft(),
                                    view.getPaddingTop(),
                                    view.getPaddingRight(),
                                    0
                            );
                            return insets;
                        }
                    }
            );
            navigation.requestApplyInsets();
        }

        navigation.post(new Runnable() {
            @Override
            public void run() {
                navigation.setPadding(
                        navigation.getPaddingLeft(),
                        navigation.getPaddingTop(),
                        navigation.getPaddingRight(),
                        0
                );
                navigation.requestLayout();
            }
        });
    }

    public static void syncSystemNavigationBar(
            final Activity activity
    ) {
        scheduleSystemNavigationBarSync(activity, 0);
    }

    private static void scheduleSystemNavigationBarSync(
            final Activity activity,
            final int attempt
    ) {
        if (
                activity == null
                        || activity.isFinishing()
                        || activity.isDestroyed()
        ) {
            return;
        }

        final View decor = activity
                .getWindow()
                .getDecorView();

        decor.post(new Runnable() {
            @Override
            public void run() {
                if (
                        activity.isFinishing()
                                || activity.isDestroyed()
                ) {
                    return;
                }

                final View navigation =
                        findReadyBottomNavigation(activity);

                boolean ready = navigation != null;

                if (ready) {
                    applyDecorUnderlay(activity, navigation);
                    Log.i(
                            TAG,
                            "late underlay dispatch marker="
                                    + INBOX_PROFILE_LATE_RETRY_MARKER
                                    + " activity="
                                    + activity.getClass().getName()
                                    + " attempt="
                                    + attempt
                    );
                    return;
                }

                if (attempt < UNDERLAY_RETRY_LIMIT) {
                    decor.postDelayed(
                            new Runnable() {
                                @Override
                                public void run() {
                                    scheduleSystemNavigationBarSync(
                                            activity,
                                            attempt + 1
                                    );
                                }
                            },
                            UNDERLAY_RETRY_DELAY_MS
                    );
                    return;
                }

                Log.w(
                        TAG,
                        "late underlay timeout marker="
                                + INBOX_PROFILE_LATE_RETRY_MARKER
                                + " activity="
                                + activity.getClass().getName()
                                + " navigation="
                                + (navigation == null
                                    ? "null"
                                    : navigation.getClass().getName())
                                + " visible="
                                + (navigation != null
                                    && navigation.getVisibility() == View.VISIBLE)
                                + " attached="
                                + (navigation != null
                                    && navigation.isAttachedToWindow())
                                + " height="
                                + (navigation == null
                                    ? -1
                                    : navigation.getHeight())
                );
            }
        });
    }

    private static View findReadyBottomNavigation(
            Activity activity
    ) {
        View material = findViewByResourceName(
                activity,
                "bottom_navigation_view"
        );

        if (isReadyBottomNavigation(material)) {
            Log.i(
                    TAG,
                    "visible navigation selected marker="
                            + VISIBLE_NAV_FALLBACK_MARKER
                            + " kind=material"
                            + " class="
                            + material.getClass().getName()
                            + " id="
                            + material.getId()
                            + " height="
                            + material.getHeight()
            );
            return material;
        }

        View legacy = findViewByResourceName(
                activity,
                "bottom_navigation"
        );

        if (isReadyBottomNavigation(legacy)) {
            Log.i(
                    TAG,
                    "visible navigation selected marker="
                            + VISIBLE_NAV_FALLBACK_MARKER
                            + " kind=legacy"
                            + " class="
                            + legacy.getClass().getName()
                            + " id="
                            + legacy.getId()
                            + " height="
                            + legacy.getHeight()
            );
            return legacy;
        }

        return null;
    }

    private static View findViewByResourceName(
            Activity activity,
            String name
    ) {
        int id = resourceId(
                activity,
                name,
                "id"
        );

        return id == 0
                ? null
                : activity.findViewById(id);
    }

    private static boolean isReadyBottomNavigation(
            View navigation
    ) {
        return navigation != null
                && navigation.getVisibility() == View.VISIBLE
                && navigation.isAttachedToWindow()
                && navigation.getHeight() > 0;
    }

    private static void applyDecorUnderlay(
            Activity activity,
            View navigation
    ) {
        Integer surfaceColor =
                resolveNavigationSurfaceColor(
                        activity,
                        navigation
                );

        if (surfaceColor == null) {
            Log.w(
                    TAG,
                    "bottom-navigation surface unresolved"
            );
            return;
        }

        View decor = activity
                .getWindow()
                .getDecorView();

        if (!(decor instanceof ViewGroup)) {
            return;
        }

        int insetBottom =
                resolveNavigationInsetBottom(
                        activity,
                        decor
                );

        if (insetBottom <= 0) {
            return;
        }

        ViewGroup decorGroup = (ViewGroup) decor;
        View underlay =
                decor.findViewWithTag(
                        DECOR_UNDERLAY_TAG
                );

        if (underlay == null) {
            underlay = new View(activity);
            underlay.setTag(DECOR_UNDERLAY_TAG);
            underlay.setClickable(false);
            underlay.setFocusable(false);
            underlay.setImportantForAccessibility(
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO
            );

            ViewGroup.LayoutParams params;

            if (decorGroup instanceof FrameLayout) {
                FrameLayout.LayoutParams frameParams =
                        new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                insetBottom
                        );
                frameParams.gravity = Gravity.BOTTOM;
                params = frameParams;
            } else {
                params = new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        insetBottom
                );
            }

            decorGroup.addView(underlay, params);
        } else {
            ViewGroup.LayoutParams params =
                    underlay.getLayoutParams();

            if (params.height != insetBottom) {
                params.height = insetBottom;
                underlay.setLayoutParams(params);
            }
        }

        underlay.setBackgroundColor(surfaceColor);
        underlay.setVisibility(View.VISIBLE);
        underlay.bringToFront();

        Log.i(
                TAG,
                "decor underlay applied marker="
                        + DECOR_UNDERLAY_MARKER
                        + " lifecycle="
                        + INBOX_PROFILE_LIFECYCLE_MARKER
                        + " activity="
                        + activity.getClass().getName()
                        + " inset="
                        + insetBottom
                        + " color=#"
                        + String.format(
                                "%08X",
                                surfaceColor
                        )
        );
    }

    private static void hideDecorUnderlay(
            Activity activity
    ) {
        try {
            View underlay = activity
                    .getWindow()
                    .getDecorView()
                    .findViewWithTag(
                            DECOR_UNDERLAY_TAG
                    );

            if (underlay != null) {
                underlay.setVisibility(View.GONE);
            }
        } catch (Throwable ignored) {
        }
    }

    private static int resolveNavigationInsetBottom(
            Activity activity,
            View decor
    ) {
        if (Build.VERSION.SDK_INT >= 23) {
            WindowInsets insets =
                    decor.getRootWindowInsets();

            if (insets != null) {
                if (Build.VERSION.SDK_INT >= 30) {
                    return insets.getInsets(
                            WindowInsets.Type.navigationBars()
                    ).bottom;
                }

                return insets
                        .getSystemWindowInsetBottom();
            }
        }

        int resourceId = activity
                .getResources()
                .getIdentifier(
                        "navigation_bar_height",
                        "dimen",
                        "android"
                );

        return resourceId != 0
                ? activity.getResources()
                    .getDimensionPixelSize(resourceId)
                : 0;
    }

    /**
     * The color the bar actually renders. Material's MaterialShapeDrawable adds
     * an elevation overlay in dark themes when it draws, so its fill color is
     * darker than the bar and the strip under it showed as a second band.
     * Draw one pixel of the background near the bar's bottom edge instead.
     */
    private static Integer renderedBackgroundColor(View navigation) {
        Drawable background = navigation.getBackground();
        int width = navigation.getWidth();
        int height = navigation.getHeight();

        if (background == null || width <= 0 || height <= 0) {
            return null;
        }

        try {
            android.graphics.Bitmap pixel =
                    android.graphics.Bitmap.createBitmap(
                            1,
                            1,
                            android.graphics.Bitmap.Config.ARGB_8888
                    );
            android.graphics.Canvas canvas =
                    new android.graphics.Canvas(pixel);
            canvas.translate(-(width / 2f), -(height - 2f));
            background.draw(canvas);
            int color = pixel.getPixel(0, 0);
            pixel.recycle();

            return android.graphics.Color.alpha(color) == 0xFF
                    ? color
                    : null;
        } catch (Throwable error) {
            return null;
        }
    }

    private static Integer resolveNavigationSurfaceColor(
            Activity activity,
            View navigation
    ) {
        Integer rendered = renderedBackgroundColor(navigation);

        if (rendered != null) {
            return rendered;
        }

        try {
            ColorStateList tint =
                    navigation.getBackgroundTintList();

            if (tint != null) {
                return tint.getColorForState(
                        navigation.getDrawableState(),
                        tint.getDefaultColor()
                );
            }
        } catch (Throwable ignored) {
        }

        Drawable background =
                navigation.getBackground();

        if (background instanceof ColorDrawable) {
            return ((ColorDrawable) background)
                    .getColor();
        }

        if (background != null) {
            ColorStateList fill = BoostNativeNavigation.fillColor(background);
            if (fill != null) {
                return fill.getColorForState(
                        navigation.getDrawableState(),
                        fill.getDefaultColor()
                );
            }
        }

        int colorSurfaceAttr = activity
                .getResources()
                .getIdentifier(
                        "colorSurface",
                        "attr",
                        activity.getPackageName()
                );

        if (colorSurfaceAttr != 0) {
            TypedValue value = new TypedValue();

            if (
                    activity.getTheme()
                            .resolveAttribute(
                                    colorSurfaceAttr,
                                    value,
                                    true
                            )
                            && value.type
                            >= TypedValue.TYPE_FIRST_COLOR_INT
                            && value.type
                            <= TypedValue.TYPE_LAST_COLOR_INT
            ) {
                return value.data;
            }
        }

        int solidColor =
                navigation.getSolidColor();

        return solidColor != 0
                ? solidColor
                : null;
    }

    private static View findNativeMaterialNavigation(
            Activity activity
    ) {
        int id = resourceId(
                activity,
                "bottom_navigation_view",
                "id"
        );

        return id == 0
                ? null
                : activity.findViewById(id);
    }

    private static void fillLandscapeCutoutSurface(
            Activity activity,
            View donor
    ) {
        if (activity == null || donor == null) {
            return;
        }

        if (
                activity.getResources()
                        .getConfiguration()
                        .orientation
                        != android.content.res.Configuration
                            .ORIENTATION_LANDSCAPE
        ) {
            return;
        }

        try {
            Window window = activity.getWindow();

            if (window == null) {
                return;
            }

            Integer surfaceColor =
                    resolveNavigationSurfaceColor(
                            activity,
                            donor
                    );

            if (surfaceColor == null) {
                return;
            }

            android.graphics.drawable.ColorDrawable fill =
                    new android.graphics.drawable.ColorDrawable(
                            surfaceColor
                    );

            /*
             * Do not modify content/root padding and do not replace Boost's
             * native inset listener. Only color the area exposed outside
             * Boost's inset content bounds.
             */
            window.setBackgroundDrawable(fill);

            View decor = window.getDecorView();

            if (decor != null) {
                decor.setBackgroundColor(surfaceColor);
                decor.invalidate();
            }

            Log.i(
                    TAG,
                    "landscape cutout surface filled marker="
                            + CANONICAL_NAV_MARKER
                            + " color="
                            + surfaceColor
                            + " activity="
                            + activity.getClass().getName()
            );
        } catch (Throwable error) {
            Log.e(
                    TAG,
                    "landscape cutout surface fill failed marker="
                            + CANONICAL_NAV_MARKER,
                    error
            );
        }
    }

    public static void syncInboxBadge(
            Activity activity,
            int count
    ) {
        if (activity == null) {
            return;
        }

        int normalizedCount = Math.max(0, count);

        SHARED_INBOX_BADGE_COUNT = normalizedCount;

        try {
            activity.getSharedPreferences(
                            PERSISTED_INBOX_BADGE_PREFERENCES,
                            0
                    )
                    .edit()
                    .putInt(
                            PERSISTED_INBOX_BADGE_KEY,
                            normalizedCount
                    )
                    .apply();

            Log.i(
                    TAG,
                    "inbox badge persisted marker="
                            + PERSISTED_INBOX_BADGE_MARKER
                            + " count="
                            + normalizedCount
            );
        } catch (Throwable error) {
            Log.e(
                    TAG,
                    "inbox badge persistence failed marker="
                            + PERSISTED_INBOX_BADGE_MARKER,
                    error
            );
        }

        Log.i(
                TAG,
                "shared inbox badge stored marker="
                        + SHARED_INBOX_BADGE_MARKER
                        + " count="
                        + normalizedCount
        );

        View navigation = findNativeMaterialNavigation(activity);

        if (navigation == null) {
            Log.i(
                    TAG,
                    "inbox badge count stored marker=MORPHE_BOOST_INBOX_BADGE_SYNC_V2_ENTRY_HOOK"
                            + " count="
                            + normalizedCount
                            + " activity="
                            + activity.getClass().getName()
            );
            return;
        }

        try {
            applyInboxBadge(
                    activity,
                    navigation,
                    normalizedCount
            );
        } catch (Throwable error) {
            Log.e(
                    TAG,
                    "inbox badge synchronization failed marker=MORPHE_BOOST_INBOX_BADGE_SYNC_V2_ENTRY_HOOK"
                            + " count="
                            + normalizedCount,
                    error
            );
        }
    }

    private static void applyStoredInboxBadge(
            Activity activity,
            View navigation
    ) {
        Integer count = null;
        Integer sharedCount = SHARED_INBOX_BADGE_COUNT;

        if (sharedCount == null) {
            try {
                android.content.SharedPreferences preferences =
                        activity.getSharedPreferences(
                                PERSISTED_INBOX_BADGE_PREFERENCES,
                                0
                        );

                if (preferences.contains(PERSISTED_INBOX_BADGE_KEY)) {
                    sharedCount =
                            Math.max(
                                    0,
                                    preferences.getInt(
                                            PERSISTED_INBOX_BADGE_KEY,
                                            0
                                    )
                            );
                    SHARED_INBOX_BADGE_COUNT = sharedCount;

                    Log.i(
                            TAG,
                            "inbox badge restored marker="
                                    + PERSISTED_INBOX_BADGE_MARKER
                                    + " count="
                                    + sharedCount
                    );
                }
            } catch (Throwable error) {
                Log.e(
                        TAG,
                        "inbox badge restoration failed marker="
                                + PERSISTED_INBOX_BADGE_MARKER,
                        error
                );
            }
        }
        if (sharedCount != null) {
            count = sharedCount;
        }

        if (count == null) {
            return;
        }

        try {
            applyInboxBadge(
                    activity,
                    navigation,
                    count
            );
        } catch (Throwable error) {
            Log.e(
                    TAG,
                    "stored inbox badge application failed marker=MORPHE_BOOST_INBOX_BADGE_SYNC_V2_ENTRY_HOOK",
                    error
            );
        }
    }

    private static void applyInboxBadge(
            Activity activity,
            View navigation,
            int count
    ) throws Exception {
        int inboxId = resourceId(
                activity,
                "item_inbox",
                "id"
        );

        if (inboxId == 0) {
            throw new IllegalStateException(
                    "item_inbox resource unavailable"
            );
        }

        BoostNativeNavigation.applyInboxBadge(navigation, inboxId, count);

        if (count <= 0) {
            Log.i(
                    TAG,
                    "inbox badge removed marker=MORPHE_BOOST_INBOX_BADGE_SYNC_V2_ENTRY_HOOK"
                            + " activity="
                            + activity.getClass().getName()
            );
            return;
        }

        Log.i(
                TAG,
                "inbox badge synchronized marker=MORPHE_BOOST_INBOX_BADGE_SYNC_V2_ENTRY_HOOK"
                        + " count="
                        + count
                        + " activity="
                        + activity.getClass().getName()
        );
    }

    private static void installMainLandscapeCutoutInsetFix(
            final Activity activity
    ) {
        if (activity == null) {
            return;
        }

        if (
                activity.getResources()
                        .getConfiguration()
                        .orientation
                        != android.content.res.Configuration.ORIENTATION_LANDSCAPE
        ) {
            Log.i(
                    TAG,
                    "landscape horizontal padding fix skipped marker="
                            + CANONICAL_NAV_MARKER
                            + " mode=portrait-native-only"
                            + " activity="
                            + activity.getClass().getName()
            );
            return;
        }

        try {
            View content = activity.findViewById(android.R.id.content);

            if (!(content instanceof ViewGroup)) {
                return;
            }

            ViewGroup group = (ViewGroup) content;

            if (group.getChildCount() == 0) {
                return;
            }

            final View root = group.getChildAt(0);

            synchronized (MAIN_LANDSCAPE_INSET_GUARDS) {
                if (MAIN_LANDSCAPE_INSET_GUARDS.containsKey(root)) {
                    return;
                }

                MAIN_LANDSCAPE_INSET_GUARDS.put(
                        root,
                        Boolean.TRUE
                );
            }

            final Runnable normalizeHorizontalPadding =
                    new Runnable() {
                        @Override
                        public void run() {
                            if (
                                    activity.getResources()
                                            .getConfiguration()
                                            .orientation
                                            != android.content.res.Configuration.ORIENTATION_LANDSCAPE
                            ) {
                                return;
                            }

                            int top = root.getPaddingTop();
                            int bottom = root.getPaddingBottom();

                            if (
                                    root.getPaddingLeft() != 0
                                            || root.getPaddingRight() != 0
                            ) {
                                int oldLeft = root.getPaddingLeft();
                                int oldRight = root.getPaddingRight();

                                root.setPadding(
                                        0,
                                        top,
                                        0,
                                        bottom
                                );

                                Log.i(
                                        TAG,
                                        "landscape horizontal root padding normalized marker="
                                                + CANONICAL_NAV_MARKER
                                                + " oldLeft="
                                                + oldLeft
                                                + " oldRight="
                                                + oldRight
                                                + " topPreserved="
                                                + top
                                                + " bottomPreserved="
                                                + bottom
                                                + " activity="
                                                + activity
                                                    .getClass()
                                                    .getName()
                                );
                            }
                        }
                    };

            root.addOnLayoutChangeListener(
                    new View.OnLayoutChangeListener() {
                        @Override
                        public void onLayoutChange(
                                View view,
                                int left,
                                int top,
                                int right,
                                int bottom,
                                int oldLeft,
                                int oldTop,
                                int oldRight,
                                int oldBottom
                        ) {
                            normalizeHorizontalPadding.run();
                        }
                    }
            );

            root.post(normalizeHorizontalPadding);

            Log.i(
                    TAG,
                    "landscape horizontal padding observer installed marker="
                            + CANONICAL_NAV_MARKER
                            + " mode=preserve-native-inset-listener"
                            + " activity="
                            + activity.getClass().getName()
            );
        } catch (Throwable error) {
            Log.e(
                    TAG,
                    "landscape horizontal padding fix failed marker="
                            + CANONICAL_NAV_MARKER,
                    error
            );
        }
    }

    private static boolean isBottomNavigationPreferenceEnabled(
            Activity activity
    ) {
        if (activity == null) {
            return false;
        }

        try {
            return android.preference.PreferenceManager
                    .getDefaultSharedPreferences(activity)
                    .getBoolean(
                            "pref_bottom_navigation",
                            false
                    );
        } catch (Throwable error) {
            Log.e(
                    TAG,
                    "Bottom-navigation preference read failed marker="
                            + "MORPHE_BOOST_BOTTOM_NAV_PREFERENCE_V4B_ON_RESUME",
                    error
            );
            return false;
        }
    }

    public static void enforceMaterialOnlyRuntimeVisibility(
            Activity activity,
            boolean runtimeVisible
    ) {
        if (activity != null) {
            synchronized (RUNTIME_VISIBILITY_REQUESTS) {
                RUNTIME_VISIBILITY_REQUESTS.put(
                        activity,
                        runtimeVisible
                );
            }
        }

        boolean preferenceEnabled =
                isBottomNavigationPreferenceEnabled(activity);
        boolean visible =
                runtimeVisible && preferenceEnabled;

        Log.i(
                TAG,
                "Preference-gated visibility marker="
                        + "MORPHE_BOOST_BOTTOM_NAV_PREFERENCE_V4B_ON_RESUME"
                        + " runtimeVisible="
                        + runtimeVisible
                        + " preferenceEnabled="
                        + preferenceEnabled
                        + " appliedVisible="
                        + visible
        );

        enforceMaterialOnlyVisibility(
                activity,
                visible,
                true
        );
    }

    public static void refreshMaterialNavigationPreference(
            Activity activity
    ) {
        boolean runtimeVisible = true;

        synchronized (RUNTIME_VISIBILITY_REQUESTS) {
            Boolean requested =
                    RUNTIME_VISIBILITY_REQUESTS.get(activity);

            if (requested != null) {
                runtimeVisible = requested;
            }
        }

        boolean visible =
                runtimeVisible
                        && isBottomNavigationPreferenceEnabled(activity);

        enforceMaterialOnlyVisibility(
                activity,
                visible
        );

        Log.i(
                TAG,
                "Bottom-navigation preference refreshed marker="
                        + "MORPHE_BOOST_BOTTOM_NAV_PREFERENCE_V4B_ON_RESUME"
                        + " preferenceVisible="
                        + visible
                        + " activity="
                        + (
                            activity == null
                                    ? "null"
                                    : activity.getClass().getName()
                        )
        );
    }

    private static String nativeCoordinatorBehaviorName(
            View navigation
    ) {
        if (navigation == null) {
            return "navigation-null";
        }

        try {
            ViewGroup.LayoutParams params =
                    navigation.getLayoutParams();

            if (params == null) {
                return "layout-params-null";
            }

            Object behavior = BoostNativeNavigation.coordinatorBehavior(navigation);
            return behavior == null ? "none" : behavior.getClass().getName();
        } catch (Throwable error) {
            return "error:" + error.getClass().getSimpleName();
        }
    }

    private static View configureNativeCanonicalNavigation(
            Activity activity,
            View nativeMaterial,
            boolean visible
    ) throws Exception {
        if (activity == null || nativeMaterial == null) {
            throw new IllegalStateException(
                    "Native Material navigation unavailable"
            );
        }

        int legacyId = resourceId(
                activity,
                "bottom_navigation",
                "id"
        );

        View legacy = legacyId == 0
                ? null
                : activity.findViewById(legacyId);

        if (legacy != null && legacy != nativeMaterial) {
            legacy.setEnabled(false);
            legacy.setClickable(false);
            legacy.setVisibility(View.GONE);
        }

        /*
         * Follow Boost for Lemmy and Material's CoordinatorLayout contract:
         * the same BottomNavigationView owns rendering, badges, selection,
         * nested-scroll behavior and translation. Do not cancel animations,
         * replace its behavior, mirror translation or move it into decor.
         */
        nativeMaterial.setEnabled(true);
        nativeMaterial.setClickable(true);
        nativeMaterial.setAlpha(1.0f);

        String activityName =
                activity.getClass().getName();

        if (
                SEARCH_ACTIVITY.equals(activityName)
                        || GO_TO_ACTIVITY.equals(activityName)
        ) {
            /*
             * These layouts already stop above the system gesture area.
             * Material otherwise adds the same bottom inset again, moving
             * the entire native navigation row one inset too high.
             */
            normalizeBottomInset(nativeMaterial);

            Log.i(
                    TAG,
                    "native Search/GoTo inset normalized marker="
                            + NATIVE_COORDINATOR_INSET_MARKER
                            + " activity="
                            + activityName
                            + " paddingBottom="
                            + nativeMaterial.getPaddingBottom()
            );
        }

        installOwnedCanonicalMenu(
                activity,
                nativeMaterial
        );
        applyStoredInboxBadge(
                activity,
                nativeMaterial
        );

        nativeMaterial.setVisibility(
                visible
                        ? View.VISIBLE
                        : View.GONE
        );

        if (visible) {
            if (Build.VERSION.SDK_INT >= 21) {
                nativeMaterial.requestApplyInsets();
            }

            nativeMaterial.requestLayout();
            nativeMaterial.invalidate();
        }

        fillLandscapeCutoutSurface(
                activity,
                nativeMaterial
        );

        ViewParent parent = nativeMaterial.getParent();

        Log.i(
                TAG,
                "native CoordinatorLayout navigation configured marker="
                        + NATIVE_COORDINATOR_MARKER
                        + " activity="
                        + activity.getClass().getName()
                        + " visible="
                        + visible
                        + " parent="
                        + (
                            parent == null
                                    ? "null"
                                    : parent.getClass().getName()
                        )
                        + " behavior="
                        + nativeCoordinatorBehaviorName(nativeMaterial)
                        + " translationY="
                        + nativeMaterial.getTranslationY()
        );

        return nativeMaterial;
    }

    public static void enforceMaterialOnlyVisibility(
            Activity activity,
            boolean visible
    ) {
        enforceMaterialOnlyVisibility(
                activity,
                visible,
                false
        );
    }

    /**
     * @param revealScrolledBar Boost calls p3(true) when a feed is shown
     *         (All, Popular, a subreddit). Bring back a bar that
     *         hide-on-scroll moved off screen in the previous feed.
     */
    private static void enforceMaterialOnlyVisibility(
            Activity activity,
            boolean visible,
            boolean revealScrolledBar
    ) {
        if (activity == null) {
            return;
        }

        try {
            synchronized (NATIVE_NAVIGATION_VISIBILITY_REQUESTS) {
                NATIVE_NAVIGATION_VISIBILITY_REQUESTS.put(
                        activity,
                        visible
                );
            }

            View nativeMaterial =
                    findNativeMaterialNavigation(activity);

            if (nativeMaterial == null) {
                throw new IllegalStateException(
                        "bottom_navigation_view absent"
                );
            }

            boolean appliedVisible =
                    visible
                            && isBottomNavigationPreferenceEnabled(
                                activity
                            );

            installMainLandscapeCutoutInsetFix(activity);

            View navigation =
                    configureNativeCanonicalNavigation(
                            activity,
                            nativeMaterial,
                            appliedVisible
                    );

            lockOwnedNavigationHost(
                    navigation,
                    revealScrolledBar && appliedVisible
            );

            if (appliedVisible) {
                syncSystemNavigationBar(activity);
            } else {
                hideDecorUnderlay(activity);
            }

            Log.i(
                    TAG,
                    "native visibility applied marker="
                            + NATIVE_COORDINATOR_MARKER
                            + " requestedVisible="
                            + visible
                            + " appliedVisible="
                            + appliedVisible
                            + " navigationVisibility="
                            + navigation.getVisibility()
                            + " activity="
                            + activity.getClass().getName()
            );
        } catch (Throwable error) {
            Log.e(
                    TAG,
                    "native visibility failed marker="
                            + NATIVE_COORDINATOR_MARKER,
                    error
            );
        }
    }

    /**
     * Brings back a bar that hide-on-scroll moved off screen, with a fresh
     * behavior so the next downward scroll hides it again.
     */
    public static void revealScrolledNavigation(View navigation) {
        lockOwnedNavigationHost(navigation, true);
    }

    private static void lockOwnedNavigationHost(
            View navigation,
            boolean revealScrolledBar
    ) {
        if (navigation == null) {
            return;
        }

        try {
            Object behaviorBefore = BoostNativeNavigation.coordinatorBehavior(navigation);
            boolean hideOnScroll = BoostNativeNavigation.applyHideOnScroll(
                    navigation, revealScrolledBar);
            Object behaviorAfter = BoostNativeNavigation.coordinatorBehavior(navigation);
            navigation.setAlpha(1.0f);
            navigation.requestLayout();
            navigation.invalidate();

            Log.i(
                    TAG,
                    "native bottom-navigation hide preference applied marker="
                            + NATIVE_BOTTOM_NAV_HIDE_PREFERENCE_MARKER
                            + " enabled="
                            + hideOnScroll
                            + " behaviorBefore="
                            + (
                                behaviorBefore == null
                                        ? "none"
                                        : behaviorBefore
                                            .getClass()
                                            .getName()
                            )
                            + " behaviorAfter="
                            + (
                                behaviorAfter == null
                                        ? "none"
                                        : behaviorAfter
                                            .getClass()
                                            .getName()
                            )
                            + " translationY="
                            + navigation.getTranslationY()
            );
        } catch (Throwable error) {
            Log.e(
                    TAG,
                    "native bottom-navigation behavior preservation failed marker="
                            + NATIVE_BOTTOM_NAV_HIDE_PREFERENCE_MARKER,
                    error
            );
        }
    }

    private static void dismissProfileSelectorDialog(
            Dialog dialog
    ) {
        if (dialog == null) {
            return;
        }

        try {
            dialog.dismiss();
        } catch (Throwable error) {
            Log.w(
                    TAG,
                    "Profile selector dialog dismiss failed marker="
                            + PROFILE_LONG_PRESS_DIALOG_BRIDGE_MARKER,
                    error
            );
        }
    }

    private static boolean invokeProfileAddAccount(
            Activity activity
    ) {
        if (activity == null) {
            return false;
        }

        try {
            BoostNativeNavigation.addAccount(activity);

            Log.i(
                    TAG,
                    "Profile selector Add account dispatched marker="
                            + PROFILE_LONG_PRESS_DIALOG_BRIDGE_MARKER
                            + " nativeAction=xy1.x(Activity)"
                            + " activity="
                            + activity.getClass().getName()
            );
            return true;
        } catch (Throwable error) {
            Log.e(
                    TAG,
                    "Profile selector Add account failed marker="
                            + PROFILE_LONG_PRESS_DIALOG_BRIDGE_MARKER,
                    error
            );
            return false;
        }
    }

    private static boolean queueProfileSelection(
            Activity activity,
            int accountIndex
    ) {
        if (activity == null) {
            return false;
        }

        synchronized (PROFILE_SELECTION_LOCK) {
            PENDING_PROFILE_SELECTION_INDEX =
                    Integer.valueOf(accountIndex);
        }

        try {
            Intent intent = new Intent().setClassName(activity, MAIN_ACTIVITY);
            intent.addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP
            );

            activity.startActivity(intent);
            activity.overridePendingTransition(
                    0,
                    0
            );

            Log.i(
                    TAG,
                    "Profile selector native account switch queued marker="
                            + PROFILE_LONG_PRESS_DIALOG_BRIDGE_MARKER
                            + " accountIndex="
                            + accountIndex
                            + " from="
                            + activity.getClass().getName()
                            + " destination="
                            + MAIN_ACTIVITY
            );
            return true;
        } catch (Throwable error) {
            synchronized (PROFILE_SELECTION_LOCK) {
                PENDING_PROFILE_SELECTION_INDEX = null;
            }

            Log.e(
                    TAG,
                    "Profile selector native account switch queue failed marker="
                            + PROFILE_LONG_PRESS_DIALOG_BRIDGE_MARKER
                            + " accountIndex="
                            + accountIndex,
                    error
            );
            return false;
        }
    }

    private static void schedulePendingProfileSelection(
            final Activity activity
    ) {
        if (
                activity == null
                        || !MAIN_ACTIVITY.equals(
                                activity.getClass().getName()
                        )
        ) {
            return;
        }

        final Integer accountIndex;

        synchronized (PROFILE_SELECTION_LOCK) {
            accountIndex = PENDING_PROFILE_SELECTION_INDEX;
            PENDING_PROFILE_SELECTION_INDEX = null;
        }

        if (accountIndex == null) {
            return;
        }

        final View decor = activity
                .getWindow()
                .getDecorView();

        decor.post(new Runnable() {
            @Override
            public void run() {
                if (
                        activity.isFinishing()
                                || activity.isDestroyed()
                ) {
                    Log.w(
                            TAG,
                            "Profile selector pending native switch abandoned marker="
                                    + PROFILE_LONG_PRESS_DIALOG_BRIDGE_MARKER
                                    + " accountIndex="
                                    + accountIndex
                    );
                    return;
                }

                try {
                    BoostNativeNavigation.selectAccount(activity, accountIndex.intValue());

                    Log.i(
                            TAG,
                            "Profile selector native account switch consumed marker="
                                    + PROFILE_LONG_PRESS_DIALOG_BRIDGE_MARKER
                                    + " nativeAction=pj0.K1(int)"
                                    + " accountIndex="
                                    + accountIndex
                                    + " activity="
                                    + activity.getClass().getName()
                    );
                } catch (Throwable error) {
                    Log.e(
                            TAG,
                            "Profile selector native account switch consume failed marker="
                                    + PROFILE_LONG_PRESS_DIALOG_BRIDGE_MARKER
                                    + " accountIndex="
                                    + accountIndex,
                            error
                    );
                }
            }
        });
    }

    private static boolean invokeProfileLongPressFallback(
            final Activity activity
    ) {
        if (activity == null) {
            return false;
        }

        try {
            final Dialog[] dialogHolder = new Dialog[1];
            dialogHolder[0] = BoostNativeNavigation.showProfileSelector(
                    activity,
                    PROFILE_SELECTOR_TITLE_RESOURCE_ID,
                    new BoostNativeNavigation.ProfileCallback() {
                        @Override
                        public void onAddAccount() {
                            dismissProfileSelectorDialog(dialogHolder[0]);
                            invokeProfileAddAccount(activity);
                        }

                        @Override
                        public void onAccountSelected(
                                String selectedUsername,
                                boolean anonymous,
                                int accountIndex
                        ) {
                            String activeUsername = currentUsername(activity);
                            dismissProfileSelectorDialog(dialogHolder[0]);
                            if (anonymous && !isLoggedIn(activity)) {
                                Log.i(TAG, "Profile selector anonymous account already active marker="
                                        + PROFILE_LONG_PRESS_DIALOG_BRIDGE_MARKER);
                                return;
                            }
                            if (selectedUsername != null && selectedUsername.equals(activeUsername)) {
                                Log.i(TAG, "Profile selector selected account already active marker="
                                        + PROFILE_LONG_PRESS_DIALOG_BRIDGE_MARKER
                                        + " username=" + selectedUsername);
                                return;
                            }
                            queueProfileSelection(activity, accountIndex);
                        }
                    }
            );

            Log.i(
                    TAG,
                    "native Profile long-press dialog bridge shown marker="
                            + PROFILE_LONG_PRESS_DIALOG_BRIDGE_MARKER
                            + " nativePattern=UserSelectorView(Context,boolean)+dp1.b"
                            + " activity="
                            + activity.getClass().getName()
            );
            return true;
        } catch (Throwable error) {
            Log.e(
                    TAG,
                    "native Profile long-press dialog bridge failed marker="
                            + PROFILE_LONG_PRESS_DIALOG_BRIDGE_MARKER,
                    error
            );
            return false;
        }
    }

    private static boolean invokeSubscriptionsLongPressFallback(
            Activity activity
    ) {
        if (activity == null) {
            return false;
        }

        try {
            if (!startDirectActivity(activity, SUBSCRIPTIONS_ACTIVITY)) {
                return false;
            }

            Log.i(
                    TAG,
                    "native Subscriptions long-press cross-activity action marker="
                            + NATIVE_LONG_PRESS_CROSS_ACTIVITY_MARKER
                            + " nativeAction=SubscriptionsActivity"
                            + " activity="
                            + activity.getClass().getName()
            );
            return true;
        } catch (Throwable error) {
            Log.e(
                    TAG,
                    "native Subscriptions long-press cross-activity action failed marker="
                            + NATIVE_LONG_PRESS_CROSS_ACTIVITY_MARKER,
                    error
            );
            return false;
        }
    }

    private static boolean hasLongPress(View item) {
        synchronized (LONG_PRESS_ITEMS) {
            return LONG_PRESS_ITEMS.containsKey(item);
        }
    }

    private static void rememberLongPress(View item) {
        synchronized (LONG_PRESS_ITEMS) {
            LONG_PRESS_ITEMS.put(item, Boolean.TRUE);
        }
    }

    private static void scheduleNativeProfileLongPress(
            final Activity activity,
            final View navigation
    ) {
        if (activity == null || navigation == null) {
            return;
        }

        final int profileId = resourceId(
                activity,
                "item_profile",
                "id"
        );

        if (profileId == 0) {
            Log.e(
                    TAG,
                    "native Profile long-press ID unavailable marker="
                            + NATIVE_PROFILE_LONG_PRESS_MARKER
            );
            return;
        }

        navigation.post(new Runnable() {
            private int attempts;

            @Override
            public void run() {
                attempts++;

                if (activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }

                final View profileItem =
                        navigation.findViewById(profileId);

                if (profileItem != null && hasLongPress(profileItem)) {
                    return;
                }

                if (profileItem == null) {
                    if (
                            attempts
                                    < NATIVE_PROFILE_LONG_PRESS_RETRY_LIMIT
                    ) {
                        navigation.postDelayed(
                                this,
                                NATIVE_PROFILE_LONG_PRESS_RETRY_DELAY_MS
                        );
                    } else {
                        Log.e(
                                TAG,
                                "native Profile long-press view exhausted marker="
                                        + NATIVE_PROFILE_LONG_PRESS_MARKER
                                        + " attempts="
                                        + attempts
                        );
                    }
                    return;
                }

                try {
                    if (!BoostNativeNavigation.isDrawerActivity(activity)) {
                        profileItem.setOnLongClickListener(
                                new View.OnLongClickListener() {
                                    @Override
                                    public boolean onLongClick(View view) {
                                        return invokeProfileLongPressFallback(
                                                activity
                                        );
                                    }
                                }
                        );
                        profileItem.setLongClickable(true);
                        rememberLongPress(profileItem);

                        Log.i(
                                TAG,
                                "native Profile long-press cross-activity fallback attached marker="
                                        + NATIVE_LONG_PRESS_CROSS_ACTIVITY_MARKER
                                        + " activity="
                                        + activity.getClass().getName()
                                        + " attempts="
                                        + attempts
                        );
                        return;
                    }

                    final View.OnLongClickListener nativeListener =
                            BoostNativeNavigation.profileLongPress(activity);

                    profileItem.setOnLongClickListener(
                            new View.OnLongClickListener() {
                                @Override
                                public boolean onLongClick(View view) {
                                    try {
                                        boolean handled =
                                                nativeListener.onLongClick(
                                                        view
                                                );

                                        Log.i(
                                                TAG,
                                                "native Profile long-press invoked marker="
                                                        + NATIVE_PROFILE_LONG_PRESS_MARKER
                                                        + " handled="
                                                        + handled
                                        );
                                        return handled;
                                    } catch (Throwable error) {
                                        Log.e(
                                                TAG,
                                                "native Profile long-press invocation failed marker="
                                                        + NATIVE_PROFILE_LONG_PRESS_MARKER,
                                                error
                                        );
                                        return false;
                                    }
                                }
                            }
                    );
                    profileItem.setLongClickable(true);
                    rememberLongPress(profileItem);

                    Log.i(
                            TAG,
                            "native Profile long-press attached marker="
                                    + NATIVE_PROFILE_LONG_PRESS_MARKER
                                    + " listener="
                                    + "pj0.G1(4,false)"
                                    + " attempts="
                                    + attempts
                    );
                } catch (Throwable error) {
                    Log.e(
                            TAG,
                            "native Profile long-press attach failed marker="
                                    + NATIVE_PROFILE_LONG_PRESS_MARKER,
                            error
                    );
                }
            }
        });
    }

    private static void scheduleNativeSubscriptionsLongPress(
            final Activity activity,
            final View navigation
    ) {
        if (activity == null || navigation == null) {
            return;
        }

        final int subscriptionsId = resourceId(
                activity,
                "item_subs",
                "id"
        );

        if (subscriptionsId == 0) {
            Log.e(
                    TAG,
                    "native Subscriptions long-press ID unavailable marker="
                            + NATIVE_SUBSCRIPTIONS_LONG_PRESS_MARKER
            );
            return;
        }

        navigation.post(new Runnable() {
            private int attempts;

            @Override
            public void run() {
                attempts++;

                if (activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }

                final View subscriptionsItem =
                        navigation.findViewById(subscriptionsId);

                if (subscriptionsItem != null && hasLongPress(subscriptionsItem)) {
                    return;
                }

                if (subscriptionsItem == null) {
                    if (
                            attempts
                                    < NATIVE_SUBSCRIPTIONS_LONG_PRESS_RETRY_LIMIT
                    ) {
                        navigation.postDelayed(
                                this,
                                NATIVE_SUBSCRIPTIONS_LONG_PRESS_RETRY_DELAY_MS
                        );
                    } else {
                        Log.e(
                                TAG,
                                "native Subscriptions long-press view exhausted marker="
                                        + NATIVE_SUBSCRIPTIONS_LONG_PRESS_MARKER
                                        + " attempts="
                                        + attempts
                        );
                    }
                    return;
                }

                try {
                    if (!BoostNativeNavigation.isDrawerActivity(activity)) {
                        subscriptionsItem.setOnLongClickListener(
                                new View.OnLongClickListener() {
                                    @Override
                                    public boolean onLongClick(View view) {
                                        return invokeSubscriptionsLongPressFallback(
                                                activity
                                        );
                                    }
                                }
                        );
                        subscriptionsItem.setLongClickable(true);
                        rememberLongPress(subscriptionsItem);

                        Log.i(
                                TAG,
                                "native Subscriptions long-press cross-activity fallback attached marker="
                                        + NATIVE_LONG_PRESS_CROSS_ACTIVITY_MARKER
                                        + " activity="
                                        + activity.getClass().getName()
                                        + " attempts="
                                        + attempts
                        );
                        return;
                    }

                    final View.OnLongClickListener nativeListener =
                            BoostNativeNavigation.subscriptionsLongPress(activity);

                    subscriptionsItem.setOnLongClickListener(
                            new View.OnLongClickListener() {
                                @Override
                                public boolean onLongClick(View view) {
                                    try {
                                        boolean handled =
                                                nativeListener.onLongClick(
                                                        view
                                                );

                                        Log.i(
                                                TAG,
                                                "native Subscriptions long-press invoked marker="
                                                        + NATIVE_SUBSCRIPTIONS_LONG_PRESS_MARKER
                                                        + " handled="
                                                        + handled
                                        );
                                        return handled;
                                    } catch (Throwable error) {
                                        Log.e(
                                                TAG,
                                                "native Subscriptions long-press invocation failed marker="
                                                        + NATIVE_SUBSCRIPTIONS_LONG_PRESS_MARKER,
                                                error
                                        );
                                        return false;
                                    }
                                }
                            }
                    );
                    subscriptionsItem.setLongClickable(true);
                    rememberLongPress(subscriptionsItem);

                    Log.i(
                            TAG,
                            "native Subscriptions long-press attached marker="
                                    + NATIVE_SUBSCRIPTIONS_LONG_PRESS_MARKER
                                    + " listener="
                                    + "pj0.G1(2,false)"
                                    + " attempts="
                                    + attempts
                    );
                } catch (Throwable error) {
                    Log.e(
                            TAG,
                            "native Subscriptions long-press attach failed marker="
                                    + NATIVE_SUBSCRIPTIONS_LONG_PRESS_MARKER,
                            error
                    );
                }
            }
        });
    }

    private static void installOwnedCanonicalMenu(
            Activity activity,
            View navigation
    ) throws Exception {
        Menu menu = BoostNativeNavigation.menu(navigation);

        boolean rebuildOwnedMenu;

        synchronized (OWNED_MENU_INSTALLED) {
            rebuildOwnedMenu =
                    !OWNED_MENU_INSTALLED.containsKey(navigation);
        }

        if (rebuildOwnedMenu) {
            menu.clear();

            addOwnedMenuItem(
                    activity,
                    menu,
                    "item_home",
                    "home",
                    "ic_home_round_24dp",
                    0
            );
            addOwnedMenuItem(
                    activity,
                    menu,
                    "item_search",
                    "search",
                    "ic_search_24dp",
                    1
            );
            addOwnedMenuItem(
                    activity,
                    menu,
                    "item_subs",
                    "title_activity_subscriptions",
                    "ic_list_24dp",
                    2
            );
            addOwnedMenuItem(
                    activity,
                    menu,
                    "item_inbox",
                    "title_activity_inbox",
                    "ic_email_24dp",
                    3
            );
            addOwnedMenuItem(
                    activity,
                    menu,
                    "item_profile",
                    "action_profile",
                    "ic_person_24dp",
                    4
            );

            Drawable avatar = currentProfileAvatar();
            if (avatar != null) {
                menu.findItem(resourceId(activity, "item_profile", "id")).setIcon(avatar);
            }

            menu.setGroupCheckable(
                    0,
                    true,
                    true
            );

            synchronized (OWNED_MENU_INSTALLED) {
                OWNED_MENU_INSTALLED.put(
                        navigation,
                        Boolean.TRUE
                );
            }
        }

        BoostNativeNavigation.setPresentation(navigation);

        Log.i(
                TAG,
                "Issue #54 shared Search host scoped marker="
                        + "MORPHE_BOOST_ISSUE54_SHARED_HOST_SCOPE_V8"
        );
        int selectedItemId =
                selectedItemIdForActivity(
                        activity,
                        0
                );

        setCheckedItem(
                navigation,
                selectedItemId
        );
        applyCanonicalTint(navigation);

        String listeners =
                attachSelectionListenersOnce(
                        activity,
                        navigation
                );

        scheduleNativeProfileLongPress(
                activity,
                navigation
        );
        scheduleNativeSubscriptionsLongPress(
                activity,
                navigation
        );

        Log.i(
                TAG,
                "owned canonical menu installed marker="
                        + CANONICAL_NAV_MARKER
                        + " activity="
                        + activity.getClass().getName()
                        + " items="
                        + menu.size()
                        + " rebuilt="
                        + rebuildOwnedMenu
                        + " selectedItemId="
                        + selectedItemId
                        + " listeners="
                        + listeners
        );
    }

    private static Drawable currentProfileAvatar() {
        if (sharedProfileAvatar == null
                || !BoostNativeNavigation.isLoggedIn()
                || !BoostNativeNavigation.showProfileAvatar()
                || sharedProfileAvatarUsername == null
                || !sharedProfileAvatarUsername.equals(BoostNativeNavigation.currentUsername())) {
            return null;
        }
        return new ProfileAvatarDrawable(sharedProfileAvatar);
    }

    public static void syncProfileAvatar(Activity activity, Drawable avatar) {
        if (avatar != null && !(avatar instanceof ProfileAvatarDrawable)) {
            throw new IllegalStateException("Unexpected Boost profile avatar drawable");
        }

        sharedProfileAvatar = avatar == null ? null : ((ProfileAvatarDrawable) avatar).bitmap;
        sharedProfileAvatarUsername = avatar == null ? null : BoostNativeNavigation.currentUsername();

        ArrayList<View> navigations;
        synchronized (OWNED_MENU_INSTALLED) {
            navigations = new ArrayList<>(OWNED_MENU_INSTALLED.keySet());
        }
        if (navigations.isEmpty()) return;

        int profileId = resourceId(activity, "item_profile", "id");
        int defaultIconId = resourceId(activity, "ic_person_24dp", "drawable");
        if (profileId == 0 || defaultIconId == 0) {
            throw new IllegalStateException("Boost profile navigation resources are missing");
        }

        for (View navigation : navigations) {
            MenuItem profile = BoostNativeNavigation.menu(navigation).findItem(profileId);
            if (profile == null) continue;

            Drawable current = currentProfileAvatar();
            if (current == null) {
                profile.setIcon(defaultIconId);
            } else {
                profile.setIcon(current);
            }
            applyCanonicalTint(navigation);
        }
    }

    private static void addOwnedMenuItem(
            Activity activity,
            Menu menu,
            String itemIdName,
            String titleName,
            String iconName,
            int order
    ) {
        int itemId = resourceId(
                activity,
                itemIdName,
                "id"
        );
        int titleId = resourceId(
                activity,
                titleName,
                "string"
        );
        int iconId = resourceId(
                activity,
                iconName,
                "drawable"
        );

        if (itemId == 0 || titleId == 0 || iconId == 0) {
            throw new IllegalStateException(
                    "Owned menu resource unresolved: "
                            + itemIdName
                            + "/"
                            + titleName
                            + "/"
                            + iconName
            );
        }

        MenuItem item = menu.add(
                0,
                itemId,
                order,
                activity.getString(titleId)
        );
        item.setIcon(iconId);
        item.setEnabled(true);
        item.setCheckable(true);
    }

    public static void standardizeHome(
            Activity activity
    ) {
        standardizeMaterialNavigation(
                activity,
                0
        );
        schedulePendingProfileSelection(activity);
    }

    public static void standardizeSubreddit(
            Activity activity
    ) {
        standardizeMaterialNavigation(
                activity,
                0
        );
    }

    public static void standardizeInbox(
            Activity activity
    ) {
        standardizeMaterialNavigation(
                activity,
                3
        );
    }

    public static void standardizeProfile(
            Activity activity
    ) {
        standardizeMaterialNavigation(
                activity,
                4
        );
    }

    public static void standardizeMaterialNavigation(
            final Activity activity,
            int fallbackIndex
    ) {
        if (activity == null) {
            return;
        }

        try {
            View nativeMaterial =
                    findNativeMaterialNavigation(activity);

            if (nativeMaterial == null) {
                throw new IllegalStateException(
                        "bottom_navigation_view absent"
                );
            }

            installMainLandscapeCutoutInsetFix(activity);

            boolean requestedVisible = true;

            synchronized (NATIVE_NAVIGATION_VISIBILITY_REQUESTS) {
                Boolean requested =
                        NATIVE_NAVIGATION_VISIBILITY_REQUESTS.get(
                                activity
                        );

                if (requested != null) {
                    requestedVisible = requested;
                }
            }

            boolean preferenceEnabled =
                    isBottomNavigationPreferenceEnabled(activity);
            requestedVisible =
                    requestedVisible && preferenceEnabled;

            View navigation =
                    configureNativeCanonicalNavigation(
                            activity,
                            nativeMaterial,
                            requestedVisible
                    );

            int selectedItemId = selectedItemIdForActivity(
                    activity,
                    fallbackIndex
            );

            if (selectedItemId == 0) {
                throw new IllegalStateException(
                        "Selected destination unresolved"
                );
            }

            setCheckedItem(
                    navigation,
                    selectedItemId
            );
            applyCanonicalTint(navigation);
            attachSelectionListenersOnce(
                    activity,
                    navigation
            );
            applyStoredInboxBadge(
                    activity,
                    navigation
            );

            if (requestedVisible) {
                syncSystemNavigationBar(activity);
            } else {
                hideDecorUnderlay(activity);
            }

            Log.i(
                    TAG,
                    "native canonical navigation marker="
                            + NATIVE_COORDINATOR_MARKER
                            + " activity="
                            + activity.getClass().getName()
                            + " selectedItemId="
                            + selectedItemId
                            + " fallbackIndex="
                            + fallbackIndex
                            + " requestedVisible="
                            + requestedVisible
                            + " parent="
                            + (
                                navigation.getParent() == null
                                        ? "null"
                                        : navigation.getParent()
                                            .getClass()
                                            .getName()
                            )
                            + " behavior="
                            + nativeCoordinatorBehaviorName(
                                navigation
                            )
                            + " translationY="
                            + navigation.getTranslationY()
            );
        } catch (Throwable error) {
            Log.e(
                    TAG,
                    "native canonical navigation failed marker="
                            + NATIVE_COORDINATOR_MARKER
                            + " activity="
                            + (
                                activity == null
                                        ? "null"
                                        : activity.getClass().getName()
                            ),
                    error
            );
        }
    }

    private static int selectedItemIdForActivity(
            Activity activity,
            int fallbackIndex
    ) {
        String activityName =
                activity.getClass().getName();

        if (MAIN_ACTIVITY.equals(activityName)) {
            return resourceId(
                    activity,
                    "item_home",
                    "id"
            );
        }

        if (
                SEARCH_ACTIVITY.equals(activityName)
                        || SEARCH_SUBMISSIONS_ACTIVITY.equals(
                                activityName
                        )
        ) {
            return resourceId(
                    activity,
                    "item_search",
                    "id"
            );
        }

        if (SUBREDDIT_ACTIVITY.equals(activityName)) {
            Log.i(
                    TAG,
                    "Subreddit selects Subscriptions marker="
                            + SUBREDDIT_SELECTION_MARKER
                            + " activity="
                            + activityName
            );

            return resourceId(
                    activity,
                    "item_subs",
                    "id"
            );
        }

        if (
                GO_TO_ACTIVITY.equals(activityName)
                        || SUBSCRIPTIONS_ACTIVITY.equals(
                                activityName
                        )
        ) {
            return resourceId(
                    activity,
                    "item_subs",
                    "id"
            );
        }

        if (MESSAGES_ACTIVITY.equals(activityName)) {
            return resourceId(
                    activity,
                    "item_inbox",
                    "id"
            );
        }

        if (USER_ACTIVITY.equals(activityName)) {
            return resourceId(
                    activity,
                    "item_profile",
                    "id"
            );
        }

        switch (fallbackIndex) {
            case 0:
                return resourceId(
                        activity,
                        "item_home",
                        "id"
                );
            case 1:
                return resourceId(
                        activity,
                        "item_search",
                        "id"
                );
            case 2:
                return resourceId(
                        activity,
                        "item_subs",
                        "id"
                );
            case 3:
                return resourceId(
                        activity,
                        "item_inbox",
                        "id"
                );
            case 4:
                return resourceId(
                        activity,
                        "item_profile",
                        "id"
                );
            default:
                return 0;
        }
    }

    public static void setSelectedItemSilently(View navigation, int selectedItemId) {
        try {
            setCheckedItem(navigation, selectedItemId);
        } catch (IllegalStateException | ClassCastException error) {
            Log.e(TAG, "Could not initialize bottom-navigation selection for item " + selectedItemId, error);
        }
    }

    private static void setCheckedItem(
            View navigation,
            int selectedItemId
    ) {
        Menu menu = BoostNativeNavigation.menu(navigation);
        MenuItem selectedItem =
                menu.findItem(selectedItemId);

        if (selectedItem == null) {
            throw new IllegalStateException(
                    "Selected menu item not found: "
                            + selectedItemId
            );
        }

        selectedItem.setCheckable(true);
        selectedItem.setChecked(true);

        Log.i(
                TAG,
                "single target checked marker="
                        + CANONICAL_NAV_MARKER
                        + " selectedItemId="
                        + selectedItemId
        );
    }





    private static int applyRenderedItemIconAppearance(
            View navigation,
            android.content.res.ColorStateList contrastTint,
            int iconColor
    ) throws Exception {
        Menu menu = BoostNativeNavigation.menu(navigation);
        int renderedIconCount = 0;

        for (int index = 0; index < menu.size(); index++) {
            MenuItem item = menu.getItem(index);

            if (item == null) {
                continue;
            }

            View itemView =
                    navigation.findViewById(
                            item.getItemId()
                    );

            if (itemView == null) {
                continue;
            }

            itemView.setAlpha(1.0f);

            BoostNativeNavigation.setItemIconTint(
                    itemView,
                    item.getIcon() instanceof ProfileAvatarDrawable ? null : contrastTint
            );

            renderedIconCount +=
                    applyRenderedImagesRecursively(
                            itemView,
                            itemView,
                            contrastTint,
                            iconColor
                    );
        }

        navigation.invalidate();
        return renderedIconCount;
    }

    private static int applyRenderedImagesRecursively(
            View root,
            View itemRoot,
            android.content.res.ColorStateList contrastTint,
            int iconColor
    ) {
        int count = 0;

        if (root instanceof android.widget.ImageView) {
            android.widget.ImageView imageView =
                    (android.widget.ImageView) root;

            imageView.setAlpha(1.0f);

            android.graphics.drawable.Drawable drawable =
                    imageView.getDrawable();

            if (drawable instanceof ProfileAvatarDrawable) {
                imageView.setImageTintList(null);
                imageView.clearColorFilter();
                drawable.clearColorFilter();
            } else {
                imageView.setImageTintList(contrastTint);
            }

            if (drawable != null && !(drawable instanceof ProfileAvatarDrawable)) {
                android.graphics.drawable.Drawable mutable =
                        drawable.mutate();

                mutable.setTint(iconColor);
                mutable.setColorFilter(
                        iconColor,
                        android.graphics.PorterDuff.Mode.SRC_IN
                );

                imageView.setImageDrawable(mutable);
            }

            android.view.ViewParent parent =
                    imageView.getParent();

            while (
                    parent instanceof View
                            && parent != itemRoot
            ) {
                ((View) parent).setAlpha(1.0f);
                parent = parent.getParent();
            }

            count++;
        }

        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;

            for (
                    int index = 0;
                    index < group.getChildCount();
                    index++
            ) {
                count +=
                        applyRenderedImagesRecursively(
                                group.getChildAt(index),
                                itemRoot,
                                contrastTint,
                                iconColor
                        );
            }
        }

        return count;
    }




    private static int canonicalIconColor(
            View navigation
    ) {
        int nightMode =
                navigation
                        .getResources()
                        .getConfiguration()
                        .uiMode
                        & android.content.res.Configuration
                                .UI_MODE_NIGHT_MASK;

        return nightMode
                == android.content.res.Configuration
                        .UI_MODE_NIGHT_YES
                ? android.graphics.Color.WHITE
                : android.graphics.Color.BLACK;
    }

    private static void applyCanonicalTint(
            View navigation
    ) {
        if (navigation == null) return;

        try {
            int nightMode =
                    navigation
                            .getResources()
                            .getConfiguration()
                            .uiMode
                            & android.content.res.Configuration
                                    .UI_MODE_NIGHT_MASK;

            boolean darkTheme =
                    nightMode
                            == android.content.res.Configuration
                                    .UI_MODE_NIGHT_YES;

            int iconColor =
                    canonicalIconColor(navigation);

            android.content.res.ColorStateList contrastTint =
                    android.content.res.ColorStateList.valueOf(
                            iconColor
                    );

            BoostNativeNavigation.setTint(navigation, contrastTint);

            int renderedIconCount =
                    applyRenderedItemIconAppearance(
                            navigation,
                            contrastTint,
                            iconColor
                    );

            Log.i(
                    TAG,
                    "rendered icon appearance applied marker="
                            + CANONICAL_NAV_MARKER
                            + " iconCount="
                            + renderedIconCount
                            + " color="
                            + (
                                iconColor
                                        == android.graphics.Color.BLACK
                                        ? "#000000"
                                        : "#FFFFFF"
                            )
            );

            Log.i(
                    TAG,
                    "theme contrast icon tint marker="
                            + CANONICAL_NAV_MARKER
                            + " theme="
                            + (darkTheme ? "dark" : "light")
                            + " color="
                            + (darkTheme ? "#FFFFFF" : "#000000")
                            + " navigation="
                            + navigation.getClass().getName()
            );
        } catch (Throwable error) {
            Log.e(
                    TAG,
                    "theme contrast icon tint failed marker="
                            + CANONICAL_NAV_MARKER,
                    error
            );
        }
    }

    /**
     * Boost sets its own callback in R1, before our hook. Replace it once;
     * subsequent configurations retain this activity's selection state.
     */
    private static String attachSelectionListenersOnce(
            Activity activity,
            View navigation
    ) throws Exception {
        synchronized (LISTENERS_ATTACHED) {
            if (LISTENERS_ATTACHED.containsKey(navigation)) {
                return "already-attached";
            }
        }

        BoostNativeNavigation.setSelectionCallback(
                navigation,
                new BoostNativeNavigation.SelectionCallback() {
                    @Override
                    public boolean onSelected(MenuItem item) {
                        return preserveSourceSelectionAfterRoute(
                                activity, item, handleItem(activity, item));
                    }

                    @Override
                    public void onReselected(MenuItem item) {
                        boolean handled = handleReselectedItem(activity, item);
                        Log.i(TAG, "Bottom navigation reselect routed marker="
                                + CANONICAL_NAV_MARKER
                                + " itemId=" + item.getItemId()
                                + " handled=" + handled);
                    }
                }
        );

        synchronized (LISTENERS_ATTACHED) {
            LISTENERS_ATTACHED.put(navigation, Boolean.TRUE);
        }

        return "MenuBuilder.u";
    }

    private static boolean preserveSourceSelectionAfterRoute(
            Activity activity,
            MenuItem routedItem,
            boolean handled
    ) {
        if (
                !handled
                        || activity == null
                        || routedItem == null
        ) {
            return handled;
        }

        int sourceItemId =
                selectedItemIdForActivity(
                        activity,
                        0
                );
        int routedItemId =
                routedItem.getItemId();

        if (
                sourceItemId == 0
                        || sourceItemId == routedItemId
        ) {
            return true;
        }

        /*
         * BottomNavigationView interprets true as permission to persist the
         * tapped destination as selected in the current Activity. Morphe has
         * already opened a different Activity, so the source Activity must
         * reject that visual state change and retain its own destination.
         */
        Log.i(
                TAG,
                "Source selection retained after routed tab marker="
                        + SOURCE_SELECTION_RETAINED_MARKER
                        + " activity="
                        + activity.getClass().getName()
                        + " sourceItemId="
                        + sourceItemId
                        + " routedItemId="
                        + routedItemId
        );

        return false;
    }

    private static boolean dispatchHomeGoToTop(
            Activity activity,
            MenuItem item
    ) {
        if (
                activity == null
                        || item == null
                        || !MAIN_ACTIVITY.equals(
                                activity.getClass().getName()
                        )
        ) {
            return false;
        }

        int homeId = resourceId(
                activity,
                "item_home",
                "id"
        );

        if (
                homeId == 0
                        || item.getItemId() != homeId
        ) {
            return false;
        }

        try {
            int goTopId = resourceId(
                    activity,
                    "subreddit_fab_go_top",
                    "id"
            );

            if (goTopId == 0) {
                throw new IllegalStateException(
                        "subreddit_fab_go_top resource unavailable"
                );
            }

            View goTop = activity.findViewById(goTopId);

            if (goTop == null) {
                throw new IllegalStateException(
                        "subreddit_fab_go_top view unavailable"
                );
            }

            /*
             * Reuse Boost's existing and independently working Go to top
             * action. performClick() invokes the same listener as the FAB
             * submenu button without duplicating feed internals.
             */
            boolean clicked = goTop.performClick();

            Log.i(
                    TAG,
                    "Home reselect dispatched to FAB Go to top marker="
                            + HOME_RESELECT_TOP_MARKER
                            + " clicked="
                            + clicked
                            + " visibility="
                            + goTop.getVisibility()
                            + " enabled="
                            + goTop.isEnabled()
                            + " viewClass="
                            + goTop.getClass().getName()
            );

            return clicked;
        } catch (Throwable error) {
            Log.e(
                    TAG,
                    "Home reselect FAB Go to top failed marker="
                            + HOME_RESELECT_TOP_MARKER,
                    error
            );
            return false;
        }
    }

    private static boolean completeStaticTabTransition(
            Activity activity,
            boolean handled
    ) {
        if (!handled || activity == null) {
            return handled;
        }

        try {
            activity.overridePendingTransition(
                    0,
                    0
            );

            Log.i(
                    TAG,
                    "Static tab transition applied marker="
                            + "MORPHE_BOOST_BOTTOM_NAV_STATIC_TAB_TRANSITION_V5"
                            + " activity="
                            + activity.getClass().getName()
            );
        } catch (Throwable error) {
            Log.e(
                    TAG,
                    "Static tab transition failed marker="
                            + "MORPHE_BOOST_BOTTOM_NAV_STATIC_TAB_TRANSITION_V5",
                    error
            );
        }

        return handled;
    }

    private static boolean isForeignUserProfileActivity(
            Activity activity
    ) {
        if (
                activity == null
                        || !USER_ACTIVITY.equals(
                                activity.getClass().getName()
                        )
        ) {
            return false;
        }

        try {
            String ownUsername = currentUsername(activity);

            if (
                    ownUsername == null
                            || ownUsername.trim().length() == 0
            ) {
                Log.w(
                        TAG,
                        "Foreign Profile detection unavailable marker="
                                + FOREIGN_PROFILE_ROUTE_MARKER
                                + " reason=current-username-unavailable"
                );
                return false;
            }

            String shownUsername = null;
            Field usernameField = findField(
                    activity.getClass(),
                    BoostFieldNames.UserActivityUsername
            );

            if (usernameField != null) {
                usernameField.setAccessible(true);
                Object value = usernameField.get(activity);

                if (value instanceof String) {
                    shownUsername = ((String) value).trim();
                }
            }

            if (
                    shownUsername == null
                            || shownUsername.length() == 0
            ) {
                Intent intent = activity.getIntent();

                if (intent != null) {
                    String value = intent.getStringExtra("username");

                    if (value != null) {
                        shownUsername = value.trim();
                    }
                }
            }

            if (
                    shownUsername == null
                            || shownUsername.length() == 0
            ) {
                Log.w(
                        TAG,
                        "Foreign Profile detection unavailable marker="
                                + FOREIGN_PROFILE_ROUTE_MARKER
                                + " reason=shown-username-unavailable"
                );
                return false;
            }

            boolean foreign =
                    !ownUsername.trim().equalsIgnoreCase(
                            shownUsername
                    );

            Log.i(
                    TAG,
                    "UserActivity Profile ownership marker="
                            + FOREIGN_PROFILE_ROUTE_MARKER
                            + " foreign="
                            + foreign
            );

            return foreign;
        } catch (Throwable error) {
            Log.w(
                    TAG,
                    "Foreign Profile detection failed marker="
                            + FOREIGN_PROFILE_ROUTE_MARKER,
                    error
            );
            return false;
        }
    }

    private static boolean handleReselectedItem(
            Activity activity,
            MenuItem item
    ) {
        if (activity == null || item == null) {
            return false;
        }

        int selectedId = item.getItemId();

        int homeId = resourceId(activity, "item_home", "id");
        int searchId = resourceId(activity, "item_search", "id");
        int subscriptionsId = resourceId(
                activity,
                "item_subs",
                "id"
        );
        int inboxId = resourceId(activity, "item_inbox", "id");
        int profileId = resourceId(
                activity,
                "item_profile",
                "id"
        );

        int currentItemId =
                selectedItemIdForActivity(activity, -1);
        String activityName = activity.getClass().getName();
        final String stateMachineMarker =
                "MORPHE_BOOST_BOTTOM_NAV_STATE_MACHINE_ISSUE163_V1";

        if (
                currentItemId == 0
                        || selectedId != currentItemId
        ) {
            Log.w(
                    TAG,
                    "Bottom navigation reselect reclassified as selection marker="
                            + RESELECT_RECOVERY_MARKER
                            + " stateMachineMarker="
                            + stateMachineMarker
                            + " activity="
                            + activityName
                            + " currentItemId="
                            + currentItemId
                            + " selectedId="
                            + selectedId
            );
            boolean handled = handleItem(activity, item);
            return preserveSourceSelectionAfterRoute(
                    activity,
                    item,
                    handled
            );
        }

        Log.i(
                TAG,
                "Bottom navigation reselect transition marker="
                        + stateMachineMarker
                        + " activity="
                        + activityName
                        + " selectedId="
                        + selectedId
        );

        if (selectedId == homeId) {
            return dispatchHomeGoToTop(activity, item);
        }

        if (selectedId == searchId) {
            if (SEARCH_SUBMISSIONS_ACTIVITY.equals(activityName)) {
                Log.i(
                        TAG,
                        "Search results reselect opens Search entry marker="
                                + RESELECT_RECOVERY_MARKER
                                + " activity="
                                + activityName
                );
                return completeStaticTabTransition(
                        activity,
                        openSearch(activity)
                );
            }
            return focusSearchInput(activity);
        }

        if (selectedId == subscriptionsId) {
            if (SUBREDDIT_ACTIVITY.equals(activityName)) {
                return completeStaticTabTransition(
                        activity,
                        openSubscriptions(activity)
                );
            }

            return true;
        }

        if (
                selectedId == profileId
                        && isForeignUserProfileActivity(activity)
        ) {
            Log.i(
                    TAG,
                    "Foreign Profile reselect routes to own Profile marker="
                            + FOREIGN_PROFILE_ROUTE_MARKER
                            + " activity="
                            + activityName
            );
            return completeStaticTabTransition(
                    activity,
                    openProfile(activity)
            );
        }

        if (
                selectedId == inboxId
                        || selectedId == profileId
        ) {
            return true;
        }

        return false;
    }

    private static boolean handleItem(
            Activity activity,
            MenuItem item
    ) {
        int selectedId = item.getItemId();

        int homeId = resourceId(activity, "item_home", "id");
        int searchId = resourceId(activity, "item_search", "id");
        int subscriptionsId = resourceId(activity, "item_subs", "id");
        int inboxId = resourceId(activity, "item_inbox", "id");
        int profileId = resourceId(activity, "item_profile", "id");

        if (
                selectedId == searchId
                        && SEARCH_ACTIVITY.equals(
                                activity.getClass().getName()
                        )
        ) {
            return focusSearchInput(activity);
        }

        if (
                selectedId == profileId
                        && isForeignUserProfileActivity(activity)
        ) {
            Log.i(
                    TAG,
                    "Foreign Profile selection routes to own Profile marker="
                            + FOREIGN_PROFILE_ROUTE_MARKER
                            + " activity="
                            + activity.getClass().getName()
            );
            return completeStaticTabTransition(
                    activity,
                    openProfile(activity)
            );
        }

        int currentItemId =
                selectedItemIdForActivity(activity, -1);

        if (
                currentItemId != 0
                        && selectedId == currentItemId
        ) {
            return true;
        }
        if (selectedId == searchId) {
            return completeStaticTabTransition(
                    activity,
                    openSearch(activity)
            );
        }
        if (selectedId == homeId) {
            return completeStaticTabTransition(
                    activity,
                    openHome(activity)
            );
        }
        if (selectedId == subscriptionsId) {
            return completeStaticTabTransition(
                    activity,
                    openSubscriptions(activity)
            );
        }
        if (selectedId == inboxId) {
            return completeStaticTabTransition(
                    activity,
                    openInbox(activity)
            );
        }
        if (selectedId == profileId) {
            return completeStaticTabTransition(
                    activity,
                    openProfile(activity)
            );
        }

        return false;
    }

    private static boolean attachSubredditSearchScope(
            Activity activity,
            Intent destination
    ) {
        if (
                activity == null
                        || destination == null
                        || !SUBREDDIT_ACTIVITY.equals(
                                activity.getClass().getName()
                        )
        ) {
            return false;
        }

        try {
            /*
             * SubredditActivity inherits the current SubscriptionViewModel
             * from SubmissionsActivity.O. It is the same Parcelable Boost
             * saves under "subscription" during activity state handling.
             */
            Field subscriptionField = findField(
                    activity.getClass(),
                    BoostFieldNames.SubmissionsActivitySubscription
            );

            if (subscriptionField == null) {
                throw new NoSuchFieldException(
                        "SubmissionsActivity.O"
                );
            }

            subscriptionField.setAccessible(true);
            Object subscription = subscriptionField.get(activity);

            if (!(subscription instanceof android.os.Parcelable)) {
                throw new IllegalStateException(
                        "Current subreddit is not Parcelable: "
                                + (
                                    subscription == null
                                            ? "null"
                                            : subscription
                                                .getClass()
                                                .getName()
                                )
                );
            }

            destination.putExtra(
                    "subscription",
                    (android.os.Parcelable) subscription
            );

            Log.i(
                    TAG,
                    "subreddit Search scope attached marker="
                            + SEARCH_CONTEXT_MARKER
                            + " subscription="
                            + subscription.getClass().getName()
            );
            return true;
        } catch (Throwable error) {
            Log.w(
                    TAG,
                    "subreddit Search scope unavailable marker="
                            + SEARCH_CONTEXT_MARKER,
                    error
            );
            return false;
        }
    }

    private static boolean openSearch(
            Activity activity
    ) {
        if (activity == null) {
            return false;
        }

        if (
                SEARCH_ACTIVITY.equals(
                        activity.getClass().getName()
                )
        ) {
            return focusSearchInput(activity);
        }

        try {
            Intent intent = new Intent().setClassName(activity, SEARCH_ACTIVITY);
            boolean subredditScopeAttached =
                    attachSubredditSearchScope(
                            activity,
                            intent
                    );

            Log.i(
                    TAG,
                    "search route marker="
                            + SEARCH_ROUTE_MARKER
                            + " freshEntryMarker="
                            + SEARCH_FRESH_ENTRY_MARKER
                            + " from="
                            + activity.getClass().getName()
                            + " to="
                            + SEARCH_ACTIVITY
                            + " subredditScopeAttached="
                            + subredditScopeAttached
            );

            activity.startActivity(intent);
            Log.i(
                TAG,
                "search caller preserved marker=" + SEARCH_ROUTE_MARKER
                    + " from=" + activity.getClass().getName()
            );
            return true;
        } catch (Throwable error) {
            Log.e(
                    TAG,
                    "search route failed marker="
                            + SEARCH_ROUTE_MARKER
                            + " from="
                            + (
                                activity == null
                                        ? "null"
                                        : activity
                                            .getClass()
                                            .getName()
                            ),
                    error
            );
            return false;
        }
    }

    private static boolean focusSearchInput(
            final Activity activity
    ) {
        if (activity == null) {
            return false;
        }

        try {
            Field field = findField(
                    activity.getClass(),
                    "searchEditText"
            );

            if (field == null) {
                throw new NoSuchFieldException("searchEditText");
            }

            field.setAccessible(true);
            Object value = field.get(activity);

            if (!(value instanceof View)) {
                throw new IllegalStateException(
                        "searchEditText is not a View"
                );
            }

            final View searchInput = (View) value;
            searchInput.requestFocus();
            activity.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams
                            .SOFT_INPUT_STATE_ALWAYS_VISIBLE
            );
            searchInput.post(new Runnable() {
                @Override
                public void run() {
                    Object service = activity.getSystemService(
                            Context.INPUT_METHOD_SERVICE
                    );

                    if (service instanceof InputMethodManager) {
                        ((InputMethodManager) service).showSoftInput(
                                searchInput,
                                InputMethodManager.SHOW_IMPLICIT
                        );
                    }
                }
            });

            Log.i(
                    TAG,
                    "search reselect focused marker="
                            + SEARCH_RESELECT_MARKER
            );
            return true;
        } catch (Throwable error) {
            Log.e(
                    TAG,
                    "search reselect failed marker="
                            + SEARCH_RESELECT_MARKER,
                    error
            );
            return false;
        }
    }

    private static boolean isMainActivityTaskRoot(Activity activity) {
        try {
            ActivityManager manager = (ActivityManager)
                    activity.getSystemService(Activity.ACTIVITY_SERVICE);

            if (manager == null) {
                return true;
            }

            int taskId = activity.getTaskId();

            for (ActivityManager.AppTask task : manager.getAppTasks()) {
                ActivityManager.RecentTaskInfo info = task.getTaskInfo();

                if (info == null || info.persistentId != taskId) {
                    continue;
                }

                return info.baseActivity != null
                        && MAIN_ACTIVITY.equals(
                                info.baseActivity.getClassName()
                        );
            }
        } catch (Throwable error) {
            Log.w(TAG, "Home task root lookup failed", error);
        }

        // Unknown: keep the stack-preserving route.
        return true;
    }

    private static boolean openHome(Activity activity) {
        try {
            Intent intent = new Intent().setClassName(activity, MAIN_ACTIVITY);

            if (isMainActivityTaskRoot(activity)) {
                intent.addFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                );
            } else {
                /*
                 * MainActivity.onCreate() finishes itself unless it is the
                 * task root. Tasks started from a notification (Inbox) or a
                 * deep link have another root, so Home would open and close
                 * immediately (issue #198). Restart the task with Home as root.
                 */
                intent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TASK
                );
                Log.i(
                        TAG,
                        "Home restarts task as root marker="
                                + HOME_TASK_ROOT_MARKER
                                + " from="
                                + activity.getClass().getName()
                );
            }

            activity.startActivity(intent);
            Log.i(
                    TAG,
                    "Home caller preserved marker="
                            + ACTIVITY_STACK_STATE_MARKER
                            + " from="
                            + activity.getClass().getName()
            );
            return true;
        } catch (Throwable error) {
            Log.e(TAG, "Home route failed", error);
            return false;
        }
    }

    private static boolean openSubscriptionsFromCurrentSubreddit(
            Activity activity
    ) {
        if (
                activity == null
                        || !SUBREDDIT_ACTIVITY.equals(
                                activity.getClass().getName()
                        )
        ) {
            return false;
        }

        try {
            /*
             * Boost 1.14.0's SubredditActivity.F1() attaches the current
             * SubmissionsActivity.O as "target_subscription", starts
             * GoToGenericActivity for result with request 0x8f, and applies
             * Boost's native transition.
             *
             * GoToGenericActivity applies Boost's own preference gate before
             * scrolling to that target. Do not duplicate that policy here.
             */
            boolean handled = BoostNativeNavigation.openCurrentSubredditSubscriptions(activity);

            Log.i(
                    TAG,
                    "Current subreddit Subscriptions route marker="
                            + CURRENT_SUBREDDIT_SUBSCRIPTIONS_MARKER
                            + " handled=" + handled
                            + " activity="
                            + activity.getClass().getName()
            );

            return handled;
        } catch (Throwable error) {
            Log.w(
                    TAG,
                    "Current subreddit Subscriptions route failed marker="
                            + CURRENT_SUBREDDIT_SUBSCRIPTIONS_MARKER,
                    error
            );
            return false;
        }
    }

    private static boolean openSubscriptions(Activity activity) {
        if (activity != null && SUBREDDIT_ACTIVITY.equals(activity.getClass().getName())) {
            return openSubscriptionsFromCurrentSubreddit(activity);
        }

        try {
            // The native Go To entry was inlined in 1.14.0.
            Intent intent = new Intent().setClassName(activity, GO_TO_ACTIVITY);
            intent.putExtra("keyboard_visible", false);
            activity.startActivity(intent);
            Log.i(
                    TAG,
                    "Subscriptions caller preserved marker="
                            + ACTIVITY_STACK_STATE_MARKER
                            + " from="
                            + activity.getClass().getName()
            );
            return true;
        } catch (Throwable error) {
            Log.e(TAG, "Subscriptions route failed", error);
            return false;
        }
    }

    private static boolean openInbox(Activity activity) {
        if (!isLoggedIn(activity)) {
            showSignInRequired(activity);
            return false;
        }

        return startDirectActivity(
                activity,
                MESSAGES_ACTIVITY
        );
    }

    private static boolean openProfile(Activity activity) {
        if (!isLoggedIn(activity)) {
            showSignInRequired(activity);
            return false;
        }

        try {
            String username = currentUsername(activity);
            if (username == null || username.length() == 0) {
                return false;
            }

            BoostNativeNavigation.openProfile(activity, username);
            Log.i(
                    TAG,
                    "Profile caller preserved marker="
                            + ACTIVITY_STACK_STATE_MARKER
                            + " from="
                            + activity.getClass().getName()
            );
            return true;
        } catch (Throwable error) {
            Log.e(TAG, "Profile route failed", error);
            return false;
        }
    }

    private static boolean startDirectActivity(
            Activity activity,
            String destinationClassName
    ) {
        try {
            activity.startActivity(
                    new Intent().setClassName(activity, destinationClassName)
            );
            Log.i(
                    TAG,
                    "Direct caller preserved marker="
                            + ACTIVITY_STACK_STATE_MARKER
                            + " from="
                            + activity.getClass().getName()
                            + " to="
                            + destinationClassName
            );
            return true;
        } catch (Throwable error) {
            Log.e(
                    TAG,
                    "Direct route failed: " + destinationClassName,
                    error
            );
            return false;
        }
    }

    private static boolean isLoggedIn(Activity activity) {
        try {
            return BoostNativeNavigation.isLoggedIn();
        } catch (Throwable error) {
            Log.w(TAG, "Login-state check failed", error);
            return false;
        }
    }

    private static String currentUsername(Activity activity) {
        try {
            return BoostNativeNavigation.currentUsername();
        } catch (Throwable error) {
            Log.w(TAG, "Username lookup failed", error);
            return null;
        }
    }

    private static void showSignInRequired(Activity activity) {
        Toast.makeText(
                activity,
                "Sign in required",
                Toast.LENGTH_SHORT
        ).show();
    }

    private static boolean isTarget(Activity activity) {
        if (activity == null) return false;

        String name = activity.getClass().getName();
        return SEARCH_ACTIVITY.equals(name)
                || GO_TO_ACTIVITY.equals(name);
    }

    private static int resourceId(
            Activity activity,
            String name,
            String type
    ) {
        int id = activity.getResources().getIdentifier(
                name,
                type,
                activity.getPackageName()
        );

        if (id != 0) return id;

        return activity.getResources().getIdentifier(
                name,
                type,
                "com.rubenmayayo.reddit"
        );
    }

    private static Field findField(
            Class<?> type,
            String name
    ) {
        Class<?> current = type;

        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }

        return null;
    }

}
