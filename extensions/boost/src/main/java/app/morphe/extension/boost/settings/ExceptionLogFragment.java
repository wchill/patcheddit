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
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import app.morphe.extension.shared.debug.ExceptionHook;
import app.morphe.extension.shared.debug.ExceptionLogStore;

public final class ExceptionLogFragment extends MorpheSettingsFragment {
    private static final String TAG = "MorpheExceptionLog";
    private static final String SAVE = "morphe_boost_exception_log_save";
    private static final String REPORTS = "morphe_boost_exception_log_reports";
    private static final int SAVE_FILE = 7403;
    private static final int MAX_VIEW_CHARACTERS = 64 * 1024;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean destroyed;
    private int pendingTasks;
    private CharSequence exportSummary;
    private AlertDialog visibleDialog;

    @Override
    protected String resourceName() {
        return "morphe_boost_exception_log";
    }

    @Override
    public void onCreatePreferences(Bundle state, String rootKey) {
        super.onCreatePreferences(state, rootKey);
        exportSummary = findPreference(SAVE).getSummary();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (pendingTasks == 0) {
            findPreference(SAVE).setSummary(exportSummary);
            refreshReports();
        }
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

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (SAVE.equals(preference.getKey())) {
            if (pendingTasks != 0) return true;
            try {
                startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                        .putExtra(Intent.EXTRA_TITLE, "boost-exceptions.log"), SAVE_FILE);
            } catch (ActivityNotFoundException failure) {
                Log.w(TAG, "No document picker is available.", failure);
                message("Exception log", "No document picker is available on this device.");
            }
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    private void refreshReports() {
        Context context = requireContext().getApplicationContext();
        notice("Loading exception reports...", null);
        runTask(() -> ExceptionHook.getLogStore(context).reports(), this::displayReports,
                reason -> notice("Could not load exception reports",
                        reason + " Try exporting the full log to inspect its contents."));
    }

    private void displayReports(List<ExceptionLogStore.Report> entries) {
        PreferenceCategory reports = findPreference(REPORTS);
        reports.removeAll();
        if (entries.isEmpty()) {
            notice("No exception reports found", "Errors caught by Boost will appear here.");
            return;
        }
        int index = 0;
        for (ExceptionLogStore.Report report : entries) {
            Preference preference = new Preference(requireContext(), null) {
                @Override
                protected void onClick() {
                    showReport(report);
                }
            };
            preference.setKey("morphe_boost_exception_report_" + index++);
            preference.setTitle(report.exceptionClass);
            preference.setSummary(report.timestamp.replace('T', ' ').replace("Z", " UTC")
                    + (report.incomplete ? " (incomplete)" : ""));
            preference.setSingleLineTitle(false);
            preference.setPersistent(false);
            reports.addPreference(preference);
        }
    }

    private void notice(String title, String summary) {
        PreferenceCategory reports = findPreference(REPORTS);
        reports.removeAll();
        Preference preference = new Preference(requireContext(), null);
        preference.setTitle(title);
        preference.setSummary(summary);
        preference.setSingleLineTitle(false);
        preference.setPersistent(false);
        preference.setSelectable(false);
        reports.addPreference(preference);
    }

    private void showReport(ExceptionLogStore.Report report) {
        TextView content = (TextView) getLayoutInflater().inflate(
                resourceId("layout", "morphe_boost_log_text"), null);
        content.setText(report.details(MAX_VIEW_CHARACTERS));
        ScrollView scroll = new ScrollView(requireContext());
        scroll.addView(content);
        AlertDialog dialog = show(new AlertDialog.Builder(requireContext())
                .setTitle(report.exceptionClass)
                .setView(scroll)
                .setPositiveButton(android.R.string.ok, null));
        dialog.setOnDismissListener(ignored -> content.setText(""));
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != SAVE_FILE || resultCode != Activity.RESULT_OK) return;
        Uri uri = data == null ? null : data.getData();
        if (uri == null || !"content".equals(uri.getScheme())) {
            message("Exception log", "The document picker did not return a valid document.");
            return;
        }
        Context context = requireContext().getApplicationContext();
        runTask(() -> {
            String text = ExceptionHook.getLogStore(context).read();
            try (OutputStream stream = context.getContentResolver().openOutputStream(uri, "wt")) {
                if (stream == null) throw new IOException("The selected document is unavailable.");
                try (OutputStreamWriter writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8)) {
                    writer.write(text);
                }
            }
            return true;
        }, ignored -> {
            findPreference(SAVE).setSummary("Full exception log exported. Review it before sharing.");
            refreshReports();
        }, reason -> message("Exception log export failed", reason));
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
        updateEnabled();
        worker.execute(() -> {
            try {
                T result = task.run();
                main.post(() -> {
                    if (taskFinished()) success.accept(result);
                });
            } catch (IOException | SecurityException error) {
                if (destroyed) return;
                Log.e(TAG, "Could not access the exception log or selected document.", error);
                main.post(() -> {
                    if (taskFinished()) failure.accept(
                            "Could not read or export the log. Check free storage and document access, then retry.");
                });
            }
        });
    }

    private boolean taskFinished() {
        if (destroyed) return false;
        pendingTasks--;
        if (!isAdded() || getView() == null) return false;
        updateEnabled();
        return true;
    }

    private void updateEnabled() {
        findPreference(SAVE).setEnabled(pendingTasks == 0);
        findPreference(REPORTS).setEnabled(pendingTasks == 0);
    }
}
