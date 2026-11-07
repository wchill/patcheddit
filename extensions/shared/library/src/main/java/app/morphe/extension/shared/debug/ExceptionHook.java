/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.debug;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.IOException;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.PatchSettings;

public final class ExceptionHook {
    private static final String TAG = "MorpheExceptionLog";
    private static final ThreadLocal<Boolean> RECORDING = new ThreadLocal<>();
    private static volatile Application application;
    private static ExceptionLogStore store;

    private ExceptionHook() {}

    public static void initialize(Application context) {
        application = context;
        if (!Utils.isContextSet()) Utils.setContext(context);
    }

    public static void handleException(Throwable error, String context) {
        if (!PatchSettings.isEnabled("morphe_patch_log_exceptions", true)) return;
        if (Boolean.TRUE.equals(RECORDING.get())) {
            Log.e(TAG, "Recursive exception logging was suppressed.");
            return;
        }
        RECORDING.set(true);
        try {
            getLogStore(application).append(error, context);
        } catch (IOException | SecurityException | IllegalStateException failure) {
            Log.e(TAG, "Could not write the private exception log.", failure);
        } finally {
            RECORDING.remove();
        }
    }

    public static void handleException(Throwable error) {
        // Native logging trees also receive ordinary messages with no exception.
        if (error == null) return;
        Application context = application;
        handleException(error, context == null ? "Non-fatal app error"
                : "Non-fatal " + context.getPackageName() + " error");
    }

    public static synchronized ExceptionLogStore getLogStore(Context context) throws IOException {
        if (context == null) throw new IOException("The application context is not initialized.");
        if (store == null) {
            File directory = context.getNoBackupFilesDir();
            if (directory == null) throw new IOException("Private app storage is unavailable.");
            store = new ExceptionLogStore(new File(directory, "morphe-exceptions"));
        }
        return store;
    }

    public static void printStackTrace(Throwable error) {
        handleException(error);
        error.printStackTrace();
    }

    private static void recordLog(String tag, String message, Throwable error) {
        if (error != null) handleException(error, tag + ": " + message);
    }

    public static int e(String tag, String message, Throwable error) {
        recordLog(tag, message, error);
        return Log.e(tag, message, error);
    }

    public static int w(String tag, String message, Throwable error) {
        recordLog(tag, message, error);
        return Log.w(tag, message, error);
    }

    public static int w(String tag, Throwable error) {
        recordLog(tag, "", error);
        return Log.w(tag, error);
    }

    public static int wtf(String tag, String message, Throwable error) {
        recordLog(tag, message, error);
        return Log.wtf(tag, message, error);
    }

    public static int wtf(String tag, Throwable error) {
        recordLog(tag, "", error);
        return Log.wtf(tag, error);
    }
}
