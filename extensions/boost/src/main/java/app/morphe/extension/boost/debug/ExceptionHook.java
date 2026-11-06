/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.boost.debug;

import android.app.Application;
import android.content.Context;
import java.io.IOException;

import app.morphe.extension.boost.utils.LoggingUtils;
import app.morphe.extension.shared.debug.ExceptionLogStore;

/**
 * @noinspection unused
 */
public class ExceptionHook {
    public static void initialize(Application context) {
        app.morphe.extension.shared.debug.ExceptionHook.initialize(context);
    }

    public static void handleException(Throwable error, String context) {
        app.morphe.extension.shared.debug.ExceptionHook.handleException(error, context);
    }

    public static void handleException(Throwable error) {
        handleException(error, "Non-fatal Boost error");
    }

    public static ExceptionLogStore getLogStore(Context context) throws IOException {
        return app.morphe.extension.shared.debug.ExceptionHook.getLogStore(context);
    }

    public static void log(String s, Object[] objs) {
        LoggingUtils.logInfo(
                true,
                () -> String.format(s, objs)
        );
    }
}
