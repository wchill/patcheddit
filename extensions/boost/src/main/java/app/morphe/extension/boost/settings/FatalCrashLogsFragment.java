/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.boost.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import app.morphe.extension.shared.debug.FatalCrashLogs;

public final class FatalCrashLogsFragment extends MorpheSettingsFragment {
    private static final String TAG = "MorpheCrashLogs";
    private static final String REPORTS = "morphe_boost_crash_logs_reports";
    private static final String SOURCE = "morphe_boost_crash_log_source";
    private static final int SAVE_FILE = 7404;
    private static final int MAX_VIEW_CHARACTERS = 64 * 1024;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean destroyed;
    private int pendingTasks;
    private Uri pendingSource;
    private AlertDialog visibleDialog;

    @Override
    protected String resourceName() {
        return "morphe_boost_fatal_crash_logs";
    }

    @Override
    public void onCreatePreferences(Bundle state, String rootKey) {
        super.onCreatePreferences(state, rootKey);
        String source = state == null ? null : state.getString(SOURCE);
        pendingSource = source == null ? null : Uri.parse(source);
    }

    @Override
    public void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (pendingSource != null) state.putString(SOURCE, pendingSource.toString());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (pendingTasks == 0) refreshReports();
    }

    @Override
    public void onDestroyView() {
        dismissDialog();
        PreferenceCategory reports = findPreference(REPORTS);
        reports.removeAll();
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        worker.shutdownNow();
        dismissDialog();
        super.onDestroy();
    }

    private void refreshReports() {
        Context context = requireContext().getApplicationContext();
        notice("Loading crash reports...", null);
        runTask(() -> FatalCrashLogs.list(context), listing -> {
            PreferenceCategory reports = findPreference(REPORTS);
            reports.removeAll();
            for (FatalCrashLogs.Entry entry : listing.entries) {
                reports.addPreference(reportPreference(entry.uri, entry.name));
            }
            if (listing.entries.isEmpty()) {
                notice("No crash reports found", listing.incomplete
                        ? "Some report locations could not be read. Check storage access and retry." : null);
            } else if (listing.incomplete) {
                reports.addPreference(noticePreference("Some report locations could not be read",
                        "The available reports are listed above. Public reports may require storage access."));
            }
        }, reason -> notice("Could not load crash reports", reason));
    }

    private Preference reportPreference(Uri uri, String name) {
        Preference preference = new Preference(requireContext(), null) {
            @Override
            protected void onClick() {
                showReport(uri, name);
            }
        };
        preference.setKey(uri.toString());
        preference.setTitle(name);
        preference.setSingleLineTitle(false);
        preference.setPersistent(false);
        return preference;
    }

    private void showReport(Uri uri, String name) {
        Context context = requireContext().getApplicationContext();
        runTask(() -> FatalCrashLogs.preview(context, uri, MAX_VIEW_CHARACTERS), preview -> {
            TextView content = (TextView) getLayoutInflater().inflate(
                    resourceId("layout", "morphe_boost_log_text"), null);
            content.setText((preview.truncated
                    ? "[Preview shows the end of the report. Export it for the complete contents.]\n\n" : "")
                    + preview.text);
            ScrollView scroll = new ScrollView(requireContext());
            scroll.addView(content);
            AlertDialog dialog = show(new AlertDialog.Builder(requireContext())
                    .setTitle(name)
                    .setView(scroll)
                    .setPositiveButton("Save a copy", (ignored, which) -> exportReport(uri, name))
                    .setNegativeButton(android.R.string.cancel, null));
            dialog.setOnDismissListener(ignored -> content.setText(""));
        }, reason -> message("Could not open crash report", reason));
    }

    private void exportReport(Uri uri, String name) {
        pendingSource = uri;
        try {
            startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                    .putExtra(Intent.EXTRA_TITLE, name), SAVE_FILE);
        } catch (ActivityNotFoundException failure) {
            pendingSource = null;
            Log.w(TAG, "No document picker is available.", failure);
            message("Crash report", "No document picker is available on this device.");
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != SAVE_FILE) return;
        Uri source = pendingSource;
        pendingSource = null;
        if (resultCode != Activity.RESULT_OK) return;
        Uri destination = data == null ? null : data.getData();
        if (source == null || destination == null || !"content".equals(destination.getScheme())) {
            message("Crash report", "The document picker did not return a valid report destination.");
            return;
        }
        Context context = requireContext().getApplicationContext();
        runTask(() -> {
            try (OutputStream stream = context.getContentResolver().openOutputStream(destination, "wt")) {
                if (stream == null) throw new IOException("The selected document is unavailable.");
                FatalCrashLogs.copy(context, source, stream);
            }
            return true;
        }, ignored -> message("Crash report exported", "A full copy was saved. Review it before sharing."),
                reason -> message("Crash report export failed", reason));
    }

    private void notice(String title, String summary) {
        PreferenceCategory reports = findPreference(REPORTS);
        reports.removeAll();
        reports.addPreference(noticePreference(title, summary));
    }

    private Preference noticePreference(String title, String summary) {
        Preference preference = new Preference(requireContext(), null);
        preference.setTitle(title);
        preference.setSummary(summary);
        preference.setSingleLineTitle(false);
        preference.setPersistent(false);
        preference.setSelectable(false);
        return preference;
    }

    private AlertDialog show(AlertDialog.Builder builder) {
        dismissDialog();
        visibleDialog = builder.create();
        if (visibleDialog.getWindow() != null) {
            visibleDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
        visibleDialog.show();
        return visibleDialog;
    }

    private void dismissDialog() {
        if (visibleDialog != null) {
            visibleDialog.dismiss();
            visibleDialog = null;
        }
    }

    private void message(String title, String text) {
        if (destroyed || !isAdded()) return;
        show(new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(text)
                .setPositiveButton(android.R.string.ok, null));
    }

    private interface Task<T> {
        T run() throws IOException;
    }

    private <T> void runTask(Task<T> task, Consumer<T> success, Consumer<String> failure) {
        pendingTasks++;
        findPreference(REPORTS).setEnabled(false);
        worker.execute(() -> {
            try {
                T result = task.run();
                main.post(() -> {
                    if (taskFinished()) success.accept(result);
                });
            } catch (IOException | SecurityException | IllegalArgumentException error) {
                if (destroyed) return;
                Log.e(TAG, "Could not access the crash report or selected document.", error);
                main.post(() -> {
                    if (taskFinished()) failure.accept(
                            "Check free storage and document access, then retry.");
                });
            }
        });
    }

    private boolean taskFinished() {
        if (destroyed) return false;
        pendingTasks--;
        if (!isAdded() || getView() == null) return false;
        findPreference(REPORTS).setEnabled(pendingTasks == 0);
        return true;
    }
}
