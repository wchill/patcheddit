/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.boost.backup;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BoostFullBackup {
    private static final String TAG = "BoostFullBackup";
    private static final String MIME = "application/zip";
    private static final String BUTTON_TAG = "morphe_full_backup_export";
    private static final int EXPORT = 15001, IMPORT = 15002;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean BUSY = new AtomicBoolean();

    private BoostFullBackup() {}

    public static void restoreAtStartup(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        try {
            BoostBackupStorage.applyPendingRestore(context);
        } catch (IOException exception) {
            // Do not let Boost open a partially restored database or preference file.
            throw new IllegalStateException("Could not finish restoring the full Boost backup.", exception);
        }
        try {
            BoostBackupStorage.clearInterruptedStage(context);
        } catch (IOException exception) {
            Log.w(TAG, "Could not remove interrupted backup work", exception);
        }
    }

    public static void addActions(Activity activity) {
        if (Build.VERSION.SDK_INT < 26) return;
        int id = activity.getResources().getIdentifier("backup_explanation", "id", activity.getPackageName());
        View explanation = activity.findViewById(id);
        if (explanation == null || !(explanation.getParent() instanceof LinearLayout)) {
            Log.e(TAG, "Could not find the native backup screen");
            Toast.makeText(activity, "Full backup options are unavailable.", Toast.LENGTH_LONG).show();
            return;
        }
        LinearLayout parent = (LinearLayout) explanation.getParent();
        if (parent.findViewWithTag(BUTTON_TAG) != null) return;
        int position = parent.indexOfChild(explanation);
        Button export = new Button(activity);
        export.setText("Export full backup");
        export.setTag(BUTTON_TAG);
        export.setOnClickListener(view -> new AlertDialog.Builder(activity)
                .setTitle("Export unencrypted backup?")
                .setMessage("The file includes your account sign-in tokens, drafts, and history. "
                        + "Anyone with a copy could access your accounts. Keep it somewhere private.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Choose where to save", (dialog, which) -> chooseDocument(activity, true))
                .show());
        parent.addView(export, position);
        Button restore = new Button(activity);
        restore.setText("Import full backup");
        restore.setOnClickListener(view -> chooseDocument(activity, false));
        parent.addView(restore, position + 1);
    }

    private static void chooseDocument(Activity activity, boolean export) {
        if (BUSY.get()) {
            Toast.makeText(activity, "A full backup operation is already running.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(export ? Intent.ACTION_CREATE_DOCUMENT : Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType(export ? MIME : "*/*");
        if (export) intent.putExtra(Intent.EXTRA_TITLE, "boost-full-backup.zip");
        try {
            activity.startActivityForResult(intent, export ? EXPORT : IMPORT);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(activity, "No file picker is available.", Toast.LENGTH_LONG).show();
        }
    }

    public static void onDocumentResult(Activity activity, int request, int result, Intent data) {
        if (Build.VERSION.SDK_INT < 26 || (request != EXPORT && request != IMPORT)
                || result != Activity.RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (request == EXPORT) {
            run(activity, uri, true);
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle("Replace local Boost data?")
                .setMessage("This replaces the accounts, drafts, history, and settings currently on this device. "
                        + "Only restore backups from sources you trust.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Replace and restore", (dialog, which) -> run(activity, uri, false))
                .show();
    }

    private static void run(Activity activity, Uri uri, boolean export) {
        if (!BUSY.compareAndSet(false, true)) {
            Toast.makeText(activity, "A full backup operation is already running.", Toast.LENGTH_SHORT).show();
            return;
        }
        Context context = activity.getApplicationContext();
        Toast.makeText(context, export ? "Writing full backup..." : "Checking full backup...", Toast.LENGTH_SHORT).show();
        WORKER.execute(() -> {
            String error = null;
            try {
                if (export) {
                    try (OutputStream output = context.getContentResolver().openOutputStream(uri, "wt")) {
                        if (output == null) throw new IOException("Could not open the backup destination.");
                        BoostBackupStorage.exportBackup(context, output);
                    }
                } else {
                    try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                        if (input == null) throw new IOException("Could not open the backup.");
                        BoostBackupStorage.stageRestore(context, input);
                    }
                }
            } catch (IOException exception) {
                error = exception.getMessage();
                if (error == null) error = "Could not read or write the backup.";
            } catch (SQLiteException | SecurityException exception) {
                Log.w(TAG, "Full backup failed: " + exception.getClass().getSimpleName());
                error = export ? "Boost could not create a full backup." : "Boost could not read the full backup.";
            } finally {
                BUSY.set(false);
            }
            String failure = error;
            activity.runOnUiThread(() -> {
                if (failure != null || export) {
                    Toast.makeText(context, failure == null ? "Full backup exported." : failure, Toast.LENGTH_LONG).show();
                } else if (activity.isFinishing() || activity.isDestroyed()) {
                    Toast.makeText(context, "Full backup ready. Restart Boost to restore it.", Toast.LENGTH_LONG).show();
                } else {
                    new AlertDialog.Builder(activity)
                            .setTitle("Full backup ready to restore")
                            .setMessage("Close Boost, then reopen it to apply the backup.")
                            .setPositiveButton("Close Boost", (ignored, which) -> {
                                activity.finishAffinity();
                                Process.killProcess(Process.myPid());
                            })
                            .setNegativeButton("Later", null)
                            .show();
                }
            });
        });
    }
}
