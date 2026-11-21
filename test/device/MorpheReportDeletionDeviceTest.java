/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.patcheddit.fixture;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.provider.MediaStore;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Only run on a disposable installation: the fixture clears both kinds of report. */
public final class MorpheReportDeletionDeviceTest {
    private static final String ACTIVITY = "app.morphe.extension.shared.settings.MorpheSettingsActivity";

    public static void run(Context context, Bundle arguments) throws Exception {
        Class<?> hook = context.getClassLoader().loadClass("app.morphe.extension.shared.debug.ExceptionHook");
        Object store = hook.getMethod("getLogStore", Context.class).invoke(null, context);
        var append = store.getClass().getMethod("append", Throwable.class, String.class);
        var read = store.getClass().getMethod("read");
        append.invoke(store, new IOException("fixture-persist"), "fixture");
        append.invoke(store, new IllegalStateException("fixture-delete"), "fixture");
        verifyScreen(context, "morphe_exception_log", "morphe_exception_log_reports",
                IllegalStateException.class.getName(), IOException.class.getName());
        check(((String) read.invoke(store)).isEmpty(), "Clearing non-fatal reports left saved data");

        File documents = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        String first = name(context.getPackageName(), "01");
        String second = name(context.getPackageName(), "02");
        String publicReport = name(context.getPackageName(), "03");
        File firstFile = new File(documents, first);
        File secondFile = new File(documents, second);
        File unrelated = new File(documents, name("com.example.other", "01"));
        for (File file : new File[]{firstFile, secondFile, unrelated}) {
            Files.write(file.toPath(), new byte[]{'c', 'r', 'a', 's', 'h'});
        }
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, publicReport);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/Morphe");
        Uri collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri publicUri = context.getContentResolver().insert(collection, values);
        check(publicUri != null, "Could not seed the public crash report");
        try (var output = context.getContentResolver().openOutputStream(publicUri)) {
            output.write('!');
        }
        verifyScreen(context, "morphe_fatal_crash_logs", "morphe_crash_logs_reports",
                publicReport, second);
        check(!firstFile.exists() && !secondFile.exists(), "Clearing reports left app-specific files");
        check(unrelated.isFile(), "Clearing reports removed a different app's file");
        try (var cursor = context.getContentResolver().query(publicUri,
                new String[]{MediaStore.MediaColumns._ID}, null, null, null)) {
            check(cursor != null && !cursor.moveToFirst(), "The selected public report was not deleted");
        }
    }

    private static String name(String packageName, String seconds) {
        return "crash_" + packageName + "_2026-10-26_12-00-" + seconds + ".txt";
    }

    private static void verifyScreen(Context context, String screen, String groupKey, String selected,
                                     String remaining) throws Exception {
        var instrumentation = PatchedditInstrumentation.instance;
        var monitor = instrumentation.addMonitor(ACTIVITY, null, false);
        PreferenceActivity activity;
        try {
            context.startActivity(new Intent().setClassName(context, ACTIVITY)
                    .putExtra("morphe_screen", screen).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            activity = (PreferenceActivity) monitor.waitForActivityWithTimeout(15_000);
            check(activity != null, "The reports screen did not start: " + screen);
        } finally {
            instrumentation.removeMonitor(monitor);
        }
        Field visibleDialog = activity.getClass().getDeclaredField("visibleDialog");
        visibleDialog.setAccessible(true);
        try {
            waitFor(() -> report(activity, groupKey, selected) != null && report(activity, groupKey, remaining) != null);
            openDelete(activity, groupKey, selected, visibleDialog);
            clickDialog(activity, visibleDialog, AlertDialog.BUTTON_NEGATIVE);
            check(onMain(() -> report(activity, groupKey, selected) != null), "Cancel removed the selected report");

            openDelete(activity, groupKey, selected, visibleDialog);
            clickDialog(activity, visibleDialog, AlertDialog.BUTTON_POSITIVE);
            waitFor(() -> report(activity, groupKey, selected) == null && report(activity, groupKey, remaining) != null);

            onMain(() -> {
                click(activity.findPreference("morphe_log_clear"));
                return null;
            });
            waitFor(() -> hasDeleteButton(activity, visibleDialog, AlertDialog.BUTTON_POSITIVE));
            clickDialog(activity, visibleDialog, AlertDialog.BUTTON_POSITIVE);
            String empty = screen.equals("morphe_exception_log") ? "No exception reports found" : "No crash reports found";
            waitFor(() -> report(activity, groupKey, empty) != null);
        } finally {
            onMain(() -> { activity.finish(); return null; });
        }
    }

    private static void openDelete(PreferenceActivity activity, String group, String title, Field field) throws Exception {
        onMain(() -> { click(report(activity, group, title)); return null; });
        waitFor(() -> hasDeleteButton(activity, field, AlertDialog.BUTTON_NEUTRAL));
        clickDialog(activity, field, AlertDialog.BUTTON_NEUTRAL);
        waitFor(() -> hasDeleteButton(activity, field, AlertDialog.BUTTON_POSITIVE));
    }

    private static boolean hasDeleteButton(PreferenceActivity activity, Field field, int button) throws Exception {
        AlertDialog dialog = (AlertDialog) field.get(activity);
        return dialog != null && dialog.isShowing()
                && "Delete".contentEquals(dialog.getButton(button).getText());
    }

    private static void clickDialog(PreferenceActivity activity, Field field, int button) throws Exception {
        onMain(() -> { ((AlertDialog) field.get(activity)).getButton(button).performClick(); return null; });
    }

    private static Preference report(PreferenceActivity activity, String key, String title) {
        PreferenceGroup group = (PreferenceGroup) activity.findPreference(key);
        for (int index = 0; index < group.getPreferenceCount(); index++) {
            Preference entry = group.getPreference(index);
            if (title.contentEquals(entry.getTitle())) return entry;
        }
        return null;
    }

    private static void click(Preference preference) {
        check(preference != null && preference.isEnabled(), "Diagnostic action is unavailable");
        check(preference.getOnPreferenceClickListener().onPreferenceClick(preference), "Action was not handled");
    }

    private static void waitFor(Callable<Boolean> ready) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            if (onMain(ready)) return;
            SystemClock.sleep(50);
        }
        throw new AssertionError("Timed out waiting for a diagnostics action");
    }

    private static <T> T onMain(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        PatchedditInstrumentation.instance.runOnMainSync(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
