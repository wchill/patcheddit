/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.boostforreddit.debug;

import android.util.Log;

import app.morphe.extension.boostforreddit.utils.LoggingUtils;
import app.morphe.extension.shared.Logger;

/**
 * @noinspection unused
 */
public class ExceptionHook {
    public static void handleException(Throwable t, String s) {
        String stackTrace = Log.getStackTraceString(t);
        LoggingUtils.logException(
                true,
                () -> String.format("Exception:\n%s\n\nContext:\n%s\n\nTraceback:\n%s", t, s, stackTrace),
                t
        );
    }
    public static void handleException(Throwable t) {
        String stackTrace = Log.getStackTraceString(t);
        LoggingUtils.logException(
                true,
                () -> String.format("Exception:\n%s\n\nTraceback:\n%s", t, stackTrace),
                t
        );
    }

    public static void log(String s, Object[] objs) {
        LoggingUtils.logInfo(
                true,
                () -> String.format(s, objs)
        );
    }
}
