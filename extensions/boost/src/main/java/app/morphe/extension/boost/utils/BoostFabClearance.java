/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (82d70f7).
 */

package app.morphe.extension.boost.utils;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Owns the vertical clearance of Boost's scroll-aware FAB menu and
 * bottom-navigation FABs.
 *
 * Boost 1.14.3 clears dependencies in the FAB's own CoordinatorLayout.
 * Patcheddit's navigation can anchor a Snackbar in a different host, so also
 * track those obstructions while preserving the native system-bar clearance.
 */
@SuppressWarnings("unused")
public final class BoostFabClearance {
    private static final String TAG = "MorpheFabClearance";
    private static final String MARKER =
            "MORPHE_BOOST_FAB_DEPENDENCY_CLEARANCE_ISSUE179_V2";

    /** Same dependency set as Boost's native layoutDependsOn checks. */
    private static final String[] DEPENDENCY_CLASSES = {
            "com.google.android.material.snackbar.Snackbar$SnackbarLayout",
            "com.google.android.material.bottomnavigation.BottomNavigationView",
            "com.aurelhubert.ahbottomnavigation.AHBottomNavigation",
            "com.rubenmayayo.reddit.ui.customviews.NavigationArrowsView",
    };

    private static final Map<Class<?>, Boolean> DEPENDENCY_CACHE =
            new HashMap<>();

    /** FABs this class positions, per coordinator. */
    private static final Map<ViewGroup, Set<View>> CLEARED_CHILDREN =
            new WeakHashMap<>();

    /**
     * Snackbars shown outside the FAB's coordinator (e.g. in the activity
     * content frame). They are no CoordinatorLayout dependency, so the native
     * callbacks never fire for them.
     */
    private static final Map<ViewGroup, Set<View>> FOREIGN_OBSTRUCTIONS =
            new WeakHashMap<>();

    private BoostFabClearance() {
    }

    /** Keeps FABs in {@code coordinator} above {@code obstruction} while it is shown. */
    public static void trackForeignObstruction(
            final ViewGroup coordinator,
            final View obstruction
    ) {
        if (coordinator == null || obstruction == null) {
            return;
        }

        weakSet(FOREIGN_OBSTRUCTIONS, coordinator).add(obstruction);

        final ViewTreeObserver.OnPreDrawListener preDraw = () -> {
            refresh(coordinator);
            return true;
        };
        obstruction.getViewTreeObserver().addOnPreDrawListener(preDraw);
        obstruction.addOnAttachStateChangeListener(
                new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(View view) {
                    }

                    @Override
                    public void onViewDetachedFromWindow(View view) {
                        view.removeOnAttachStateChangeListener(this);
                        view.getViewTreeObserver().removeOnPreDrawListener(preDraw);
                        weakSet(FOREIGN_OBSTRUCTIONS, coordinator).remove(view);
                        refresh(coordinator);

                        Log.i(
                                TAG,
                                "foreign obstruction removed marker=" + MARKER
                        );
                    }
                }
        );
    }

    private static void refresh(ViewGroup coordinator) {
        for (View child : new ArrayList<>(weakSet(CLEARED_CHILDREN, coordinator))) {
            if (child.getParent() == coordinator) {
                apply(coordinator, child, null);
            }
        }
    }

    private static Set<View> weakSet(Map<ViewGroup, Set<View>> map, ViewGroup key) {
        Set<View> set = map.get(key);

        if (set == null) {
            set = Collections.newSetFromMap(new WeakHashMap<>());
            map.put(key, set);
        }

        return set;
    }

    /** Replaces onDependentViewChanged. */
    public static void onDependentViewChanged(
            ViewGroup coordinator,
            View child
    ) {
        apply(coordinator, child, null);
    }

    /** Replaces onDependentViewRemoved. */
    public static void onDependentViewRemoved(
            ViewGroup coordinator,
            View child,
            View removed
    ) {
        float translation = apply(coordinator, child, removed);

        Log.i(
                TAG,
                "dependency removed marker="
                        + MARKER
                        + " removed="
                        + (removed == null ? "null" : removed.getClass().getName())
                        + " translationY="
                        + translation
        );
    }

    private static float apply(
            ViewGroup coordinator,
            View child,
            View excluded
    ) {
        if (coordinator == null || child == null) {
            return 0.0f;
        }

        // The edge the FAB is laid out against. Using the coordinator height
        // would add its bottom padding (system inset) to the lift.
        int anchorBottom = child.getBottom();
        ViewGroup.LayoutParams params = child.getLayoutParams();

        if (params instanceof ViewGroup.MarginLayoutParams) {
            anchorBottom += ((ViewGroup.MarginLayoutParams) params).bottomMargin;
        }

        weakSet(CLEARED_CHILDREN, coordinator).add(child);

        int[] coordinatorLocation = new int[2];
        coordinator.getLocationInWindow(coordinatorLocation);
        int safeBottom = coordinator.getRootView().getHeight()
                - BoostSystemBarInsetsFix.navigationBottomInset(coordinator);
        float translation = Math.min(0.0f, safeBottom - coordinatorLocation[1] - anchorBottom);

        for (int i = 0, count = coordinator.getChildCount(); i < count; i++) {
            View dependency = coordinator.getChildAt(i);

            if (
                    dependency == null
                            || dependency == child
                            || dependency == excluded
                            || !dependency.isShown()
                            || !isDependency(dependency.getClass())
            ) {
                continue;
            }

            // Equals Boost's (translationY - height) for bottom-aligned views,
            // and also clears a Snackbar anchored above the navigation.
            float visibleTop =
                    dependency.getTop() + dependency.getTranslationY();
            translation = Math.min(
                    translation,
                    visibleTop - anchorBottom
            );
        }

        Set<View> foreign = FOREIGN_OBSTRUCTIONS.get(coordinator);

        if (foreign != null && !foreign.isEmpty()) {
            int[] obstructionLocation = new int[2];

            for (View obstruction : foreign) {
                if (obstruction == excluded || !obstruction.isShown()) {
                    continue;
                }

                // Window location already includes the Snackbar's translation.
                obstruction.getLocationInWindow(obstructionLocation);
                float visibleTop = obstructionLocation[1] - coordinatorLocation[1];
                translation = Math.min(translation, visibleTop - anchorBottom);
            }
        }

        if (child.getTranslationY() != translation) {
            child.setTranslationY(translation);
        }
        return translation;
    }

    private static boolean isDependency(Class<?> type) {
        Boolean cached = DEPENDENCY_CACHE.get(type);

        if (cached != null) {
            return cached;
        }

        boolean result = false;

        for (Class<?> current = type; current != null && !result; current = current.getSuperclass()) {
            String name = current.getName();

            for (String dependencyClass : DEPENDENCY_CLASSES) {
                if (dependencyClass.equals(name)) {
                    result = true;
                    break;
                }
            }
        }

        DEPENDENCY_CACHE.put(type, result);
        return result;
    }
}
