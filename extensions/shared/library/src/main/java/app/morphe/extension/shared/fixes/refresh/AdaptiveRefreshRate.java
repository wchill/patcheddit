/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 */

package app.morphe.extension.shared.fixes.refresh;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import java.util.WeakHashMap;

public final class AdaptiveRefreshRate {
    private static final String TAG = "MorpheRefresh";
    private static final String MARKER = "MORPHE_ADAPTIVE_REFRESH_RATE_V2";

    private static final float HIGH_REFRESH_RATE_HINT_HZ = 120.0f;

    private static final WeakHashMap<Application, Boolean> INSTALLED = new WeakHashMap<>();
    private static final WeakHashMap<Activity, Boolean> LOGGED = new WeakHashMap<>();

    private AdaptiveRefreshRate() {
    }

    public static void install(@NonNull Application application) {
        if (INSTALLED.containsKey(application)) return;
        try {
            application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(@NonNull final Activity activity, Bundle savedInstanceState) {
                    apply(activity);
                }

                @Override
                public void onActivityResumed(@NonNull final Activity activity) {
                    apply(activity);
                }

                @Override public void onActivityStarted(@NonNull Activity activity) {}
                @Override public void onActivityPaused(@NonNull Activity activity) {}
                @Override public void onActivityStopped(@NonNull Activity activity) {}
                @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}
                @Override public void onActivityDestroyed(@NonNull Activity activity) {
                    LOGGED.remove(activity);
                }
            });
            INSTALLED.put(application, Boolean.TRUE);
        } catch (RuntimeException failure) {
            Log.w(TAG, "Could not install adaptive refresh rate callbacks", failure);
        }
    }

    private static void apply(Activity activity) {
        try {
            applyNow(activity);

            final Window window = activity.getWindow();
            if (window == null) return;

            final View decor = window.getDecorView();
            if (decor == null) return;

            Runnable retry = () -> applyNow(activity);

            decor.post(retry);
            decor.postDelayed(retry, 250L);
            decor.postDelayed(retry, 1000L);
        } catch (RuntimeException failure) {
            Log.w(TAG, "Could not schedule adaptive refresh rate updates", failure);
        }
    }

    private static void applyNow(Activity activity) {
        try {
            Window window = activity.getWindow();
            if (window == null) return;

            if (!isEnabled()) {
                clearWindowPreference(activity, window);
                return;
            }

            WindowManager.LayoutParams attrs = window.getAttributes();
            if (attrs != null && Math.abs(attrs.preferredRefreshRate - HIGH_REFRESH_RATE_HINT_HZ) > 0.01f) {
                attrs.preferredRefreshRate = HIGH_REFRESH_RATE_HINT_HZ;
                window.setAttributes(attrs);
            }

            View decor = window.getDecorView();
            if (decor != null) {
                requestViewHighRefreshRate(decor);
            }

            logAppliedOnce(activity, attrs == null ? 0.0f : attrs.preferredRefreshRate);
        } catch (RuntimeException | LinkageError failure) {
            Log.w(TAG, "Could not apply the refresh rate preference", failure);
        }
    }

    private static boolean isEnabled() {
        // The patch binds this to the client's Patcheddit setting.
        return true;
    }

    private static void clearWindowPreference(Activity activity, Window window) {
        WindowManager.LayoutParams attrs = window.getAttributes();
        if (attrs != null && attrs.preferredRefreshRate != 0.0f) {
            attrs.preferredRefreshRate = 0.0f;
            window.setAttributes(attrs);
        }

        requestViewNoPreference(window.getDecorView());
        LOGGED.remove(activity);
    }

    private static void requestViewHighRefreshRate(View view) {
        if (view != null && Build.VERSION.SDK_INT >= 35) {
            view.setRequestedFrameRate(View.REQUESTED_FRAME_RATE_CATEGORY_HIGH);
        }
    }

    private static void requestViewNoPreference(View view) {
        if (view != null && Build.VERSION.SDK_INT >= 35) {
            view.setRequestedFrameRate(View.REQUESTED_FRAME_RATE_CATEGORY_NO_PREFERENCE);
        }
    }

    private static void logAppliedOnce(Activity activity, float preferredRefreshRate) {
        if (LOGGED.put(activity, Boolean.TRUE) != null) return;
        Log.i(TAG, MARKER
                + " activity=" + activity.getClass().getName()
                + " preferredRefreshRate=" + preferredRefreshRate);
    }
}
