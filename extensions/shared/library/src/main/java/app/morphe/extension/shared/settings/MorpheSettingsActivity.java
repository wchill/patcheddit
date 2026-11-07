/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.settings;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.EditTextPreference;
import android.preference.CheckBoxPreference;
import android.util.Log;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.morphe.extension.shared.debug.ExceptionHook;
import app.morphe.extension.shared.debug.ExceptionLogStore;
import app.morphe.extension.shared.debug.FatalCrashLogs;

@SuppressWarnings("deprecation")
public final class MorpheSettingsActivity extends PreferenceActivity {
    private static final String TAG = "MorpheDiagnostics";
    private static final String SCREEN = "morphe_screen";
    private static final String EXCEPTIONS = "morphe_exception_log";
    private static final String CRASHES = "morphe_fatal_crash_logs";
    private static final String SOURCE = "morphe_crash_log_source";
    private static final int SAVE_EXCEPTIONS = 7403;
    private static final int SAVE_CRASH = 7404;
    private static final int OPEN_CRASH = 7405;
    private static final int MAX_VIEW_CHARACTERS = 64 * 1024;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean destroyed;
    private String screen;
    private PreferenceCategory reports;
    private Preference action;
    private int pendingTasks;
    private Uri pendingSource;
    private AlertDialog visibleDialog;

    @Override
    protected void onCreate(Bundle state) {
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        setTheme(dark ? android.R.style.Theme_Material : android.R.style.Theme_Material_Light);
        super.onCreate(state);
        screen = getIntent().getStringExtra(SCREEN);
        ActionBar bar = getActionBar();
        if (bar != null) bar.setDisplayHomeAsUpEnabled(true);
        if (screen == null) {
            OAuthSettings.migrateLegacyPreferences(this);
            addPreferencesFromResource(resource("xml", "morphe_settings"));
            setTitle(resource("string", "morphe_settings_title"));
            bindPatchPreferences(getPreferenceScreen());
            bindOAuthPreferences();
            preference(EXCEPTIONS).setOnPreferenceClickListener(ignored -> openScreen(EXCEPTIONS));
            preference("morphe_crash_logs").setOnPreferenceClickListener(ignored -> openScreen(CRASHES));
            return;
        }
        if (!EXCEPTIONS.equals(screen) && !CRASHES.equals(screen)) {
            Log.e(TAG, "Unknown Morphe diagnostics screen: " + screen);
            Toast.makeText(this, "Could not open the requested diagnostics screen.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        addPreferencesFromResource(resource("xml", screen));
        boolean exceptions = EXCEPTIONS.equals(screen);
        setTitle(resource("string", exceptions ? "morphe_exception_log_title" : "morphe_crash_logs_title"));
        Preference category = preference(exceptions ? "morphe_exception_log_reports" : "morphe_crash_logs_reports");
        if (!(category instanceof PreferenceCategory)) {
            throw new IllegalStateException("The Morphe reports category is missing.");
        }
        reports = (PreferenceCategory) category;
        action = preference(exceptions ? "morphe_exception_log_save" : "morphe_crash_logs_open");
        action.setOnPreferenceClickListener(ignored -> {
            if (exceptions) {
                chooseDocument(Intent.ACTION_CREATE_DOCUMENT, SAVE_EXCEPTIONS, getPackageName() + "-exceptions.log");
            } else {
                chooseDocument(Intent.ACTION_OPEN_DOCUMENT, OPEN_CRASH, null);
            }
            return true;
        });
        String source = state == null ? null : state.getString(SOURCE);
        pendingSource = source == null ? null : Uri.parse(source);
    }

    private void bindPatchPreferences(PreferenceGroup group) {
        for (int index = 0; index < group.getPreferenceCount(); index++) {
            Preference entry = group.getPreference(index);
            if (entry instanceof PreferenceGroup) bindPatchPreferences((PreferenceGroup) entry);
            String key = entry.getKey();
            if (entry instanceof CheckBoxPreference && key != null && key.startsWith("morphe_patch_")) {
                entry.setOnPreferenceChangeListener((preference, value) -> {
                    Toast.makeText(this, "Restart the app to apply the patch change.", Toast.LENGTH_LONG).show();
                    return true;
                });
            }
        }
    }

    private void bindOAuthPreferences() {
        String[] keys = OAuthSettings.preferenceKeys(this);
        if (findPreference(keys[0]) == null) return;
        for (String key : keys) {
            Preference entry = preference(key);
            if (!(entry instanceof EditTextPreference)) {
                entry.setSummary(OAuthSettings.defaultValue(key)
                        + "\nFixed in this APK. Repatch the app to change the callback URI.");
                continue;
            }
            EditTextPreference editor = (EditTextPreference) entry;
            editor.setSummary(OAuthSettings.summary(key, editor.getText()));
            editor.setOnPreferenceChangeListener((preference, value) -> {
                String text = (String) value;
                String error = OAuthSettings.validationError(key, text);
                if (error != null) {
                    Log.w(TAG, "Rejected an invalid OAuth setting.");
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                    return false;
                }
                preference.setSummary(OAuthSettings.summary(key, text));
                Toast.makeText(this, "Restart the app and sign in again after changing OAuth values.",
                        Toast.LENGTH_LONG).show();
                return true;
            });
        }
    }

    private boolean openScreen(String name) {
        startActivity(new Intent(this, MorpheSettingsActivity.class).putExtra(SCREEN, name));
        return true;
    }

    private int resource(String type, String name) {
        return MorpheSettings.resourceId(this, type, name);
    }

    private Preference preference(String key) {
        Preference preference = findPreference(key);
        if (preference == null) throw new IllegalStateException("Missing Morphe preference: " + key);
        return preference;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (reports != null && pendingTasks == 0) refreshReports();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (pendingSource != null) state.putString(SOURCE, pendingSource.toString());
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        worker.shutdownNow();
        main.removeCallbacksAndMessages(null);
        dismissDialog();
        super.onDestroy();
    }

    private void refreshReports() {
        notice("Loading reports...", null);
        if (EXCEPTIONS.equals(screen)) {
            runTask(() -> ExceptionHook.getLogStore(getApplicationContext()).reports(), this::displayExceptions,
                    reason -> notice("Could not load exception reports", reason));
        } else {
            runTask(() -> FatalCrashLogs.list(getApplicationContext()), listing -> {
                reports.removeAll();
                for (FatalCrashLogs.Entry entry : listing.entries) {
                    reports.addPreference(report(entry.name, null, () -> showCrash(entry.uri, entry.name)));
                }
                if (listing.entries.isEmpty()) {
                    notice("No crash reports found", listing.incomplete
                            ? "Some locations could not be read. Use the document picker to open a saved report." : null);
                } else if (listing.incomplete) {
                    reports.addPreference(noticePreference("Some report locations could not be read",
                            "Use the document picker to open other saved reports."));
                }
            }, reason -> notice("Could not load crash reports", reason));
        }
    }

    private void displayExceptions(List<ExceptionLogStore.Report> entries) {
        reports.removeAll();
        if (entries.isEmpty()) {
            notice("No exception reports found", "Errors captured by the Log exceptions patch will appear here.");
            return;
        }
        for (ExceptionLogStore.Report entry : entries) {
            reports.addPreference(report(entry.exceptionClass,
                    entry.timestamp.replace('T', ' ').replace("Z", " UTC") + (entry.incomplete ? " (incomplete)" : ""),
                    () -> showText(entry.exceptionClass, entry.details(MAX_VIEW_CHARACTERS), null, null)));
        }
    }

    private Preference report(String title, String summary, Runnable open) {
        Preference preference = new Preference(this);
        preference.setTitle(title);
        preference.setSummary(summary);
        preference.setPersistent(false);
        preference.setOnPreferenceClickListener(ignored -> {
            open.run();
            return true;
        });
        return preference;
    }

    private void showCrash(Uri uri, String name) {
        runTask(() -> FatalCrashLogs.preview(getApplicationContext(), uri, MAX_VIEW_CHARACTERS), preview ->
                showText(name, (preview.truncated
                        ? "[Preview shows the end of the report. Export it for the complete contents.]\n\n" : "")
                        + preview.text, uri, name), reason -> message("Could not open crash report", reason));
    }

    private void showText(String title, String text, Uri source, String name) {
        TextView content = (TextView) getLayoutInflater().inflate(resource("layout", "morphe_log_text"), null);
        content.setText(text);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(title).setView(scroll);
        if (source == null) {
            builder.setPositiveButton(android.R.string.ok, null);
        } else {
            builder.setPositiveButton("Save a copy", (ignored, which) -> {
                pendingSource = source;
                if (!chooseDocument(Intent.ACTION_CREATE_DOCUMENT, SAVE_CRASH, name)) pendingSource = null;
            }).setNegativeButton(android.R.string.cancel, null);
        }
        show(builder).setOnDismissListener(ignored -> content.setText(""));
    }

    private boolean chooseDocument(String intentAction, int request, String name) {
        Intent intent = new Intent(intentAction).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/plain").putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        if (name != null) intent.putExtra(Intent.EXTRA_TITLE, name);
        try {
            startActivityForResult(intent, request);
            return true;
        } catch (ActivityNotFoundException | SecurityException failure) {
            Log.e(TAG, "Could not open the document picker.", failure);
            message("Diagnostics", "The document picker is unavailable or access was denied.");
            return false;
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != SAVE_EXCEPTIONS && request != SAVE_CRASH && request != OPEN_CRASH) return;
        Uri source = pendingSource;
        if (request == SAVE_CRASH) pendingSource = null;
        if (result != RESULT_OK) return;
        Uri uri = data == null ? null : data.getData();
        if (uri == null || !"content".equals(uri.getScheme()) || request == SAVE_CRASH && source == null) {
            Log.e(TAG, "The document picker did not return a valid diagnostic document.");
            message("Diagnostics", "The document picker did not return a valid document.");
            return;
        }
        if (request == OPEN_CRASH) {
            showCrash(uri, "crash_" + getPackageName() + ".txt");
            return;
        }
        runTask(() -> {
            try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                if (output == null) throw new IOException("The selected document is unavailable.");
                if (request == SAVE_CRASH) {
                    FatalCrashLogs.copy(getApplicationContext(), source, output);
                } else {
                    try (OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
                        writer.write(ExceptionHook.getLogStore(getApplicationContext()).read());
                    }
                }
            }
            return true;
        }, ignored -> message("Log exported", "A full copy was saved. Review it before sharing."),
                reason -> message("Log export failed", reason));
    }

    private void notice(String title, String summary) {
        reports.removeAll();
        reports.addPreference(noticePreference(title, summary));
    }

    private Preference noticePreference(String title, String summary) {
        Preference preference = new Preference(this);
        preference.setTitle(title);
        preference.setSummary(summary);
        preference.setSelectable(false);
        preference.setPersistent(false);
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
        if (!destroyed && !isFinishing()) {
            show(new AlertDialog.Builder(this).setTitle(title).setMessage(text)
                    .setPositiveButton(android.R.string.ok, null));
        }
    }

    private interface Task<T> {
        T run() throws IOException;
    }

    private interface Result<T> {
        void accept(T value);
    }

    private <T> void runTask(Task<T> task, Result<T> success, Result<String> failure) {
        pendingTasks++;
        updateEnabled();
        worker.execute(() -> {
            try {
                T result = task.run();
                main.post(() -> {
                    if (taskFinished()) success.accept(result);
                });
            } catch (IOException | SecurityException | IllegalArgumentException error) {
                if (destroyed) return;
                Log.e(TAG, "Could not access diagnostic reports or the selected document.", error);
                main.post(() -> {
                    if (taskFinished()) failure.accept("Check free storage and document access, then retry.");
                });
            }
        });
    }

    private boolean taskFinished() {
        if (destroyed || isFinishing()) return false;
        pendingTasks--;
        updateEnabled();
        return true;
    }

    private void updateEnabled() {
        reports.setEnabled(pendingTasks == 0);
        action.setEnabled(pendingTasks == 0);
    }
}
