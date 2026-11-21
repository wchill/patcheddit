/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.patcheddit.fixture;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Use only on a disposable Boost installation: exercising full restore replaces its data. */
public final class BoostFullBackupDeviceTest {
    private static final String ACCOUNT = "fixture_backup_account";
    private static final String TOKEN = "synthetic-refresh-token-for-backup-fixture";
    private static final String DRAFT = "synthetic draft \uD83D\uDE42";
    private static final String SUBREDDIT = "fixture_backup_subreddit";
    private static final String READ = "t3_fixture_backup_read";
    private static final String KEY = "patcheddit.fixture.backup";
    private static final String LOCAL = "patcheddit.fixture.destination_only";

    public static void run(Context context, Bundle arguments) throws Exception {
        if ("diagnostics".equals(arguments.getString("phase"))) {
            verifyDebugLogging(context);
        } else if ("verify".equals(arguments.getString("phase"))) {
            verifyRestored(context);
        } else {
            verifyActions(context);
            stageRestore(context, Integer.parseInt(arguments.getString("version-offset", "0")));
        }
    }

    private static void verifyDebugLogging(Context context) throws Exception {
        var instrumentation = PatchedditInstrumentation.instance;
        String type = "app.morphe.extension.shared.settings.MorpheSettingsActivity";
        var monitor = instrumentation.addMonitor(type, null, false);
        Activity activity;
        try {
            context.startActivity(new Intent().setClassName(context, type)
                    .putExtra("morphe_screen", "morphe_fatal_crash_logs")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            activity = monitor.waitForActivityWithTimeout(15_000);
            check(activity != null, "Fatal crash logs screen did not start");
        } finally {
            instrumentation.removeMonitor(monitor);
        }
        FutureTask<Void> assertions = new FutureTask<>(() -> {
            Class<?> logger = context.getClassLoader().loadClass("app.morphe.extension.shared.Logger");
            Method debug = logger.getMethod("isDebugEnabled");
            Method verbose = logger.getMethod("isVerboseEnabled");
            ListPreference level = (ListPreference) ((PreferenceActivity) activity).findPreference("morphe_debug_level");
            check(level != null, "Missing crash log level preference");
            var listener = level.getOnPreferenceChangeListener();
            check(listener != null, "The debug logging preference is not bound");
            String previous = level.getValue();
            try {
                check("DISABLED".equals(previous) && !(Boolean) debug.invoke(null),
                        "Debug logging was enabled by default");
                check(listener.onPreferenceChange(level, "DEBUG") && (Boolean) debug.invoke(null)
                        && !(Boolean) verbose.invoke(null), "Debug level did not reach the Morphe logger");
                check(listener.onPreferenceChange(level, "VERBOSE") && (Boolean) verbose.invoke(null),
                        "Verbose level did not reach the Morphe logger");
                check(listener.onPreferenceChange(level, "DISABLED") && !(Boolean) debug.invoke(null),
                        "Disabling debug logging did not reach the Morphe logger");
            } finally {
                listener.onPreferenceChange(level, previous);
                activity.finish();
            }
            return null;
        });
        instrumentation.runOnMainSync(assertions);
        assertions.get(10, TimeUnit.SECONDS);
    }

    private static void verifyActions(Context context) throws Exception {
        var instrumentation = PatchedditInstrumentation.instance;
        String type = "com.rubenmayayo.reddit.BackupActivity";
        var monitor = instrumentation.addMonitor(type, null, false);
        Activity activity;
        try {
            context.startActivity(new Intent().setClassName(context, type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            activity = monitor.waitForActivityWithTimeout(15_000);
            check(activity != null, "Boost Backup screen did not start");
        } finally {
            instrumentation.removeMonitor(monitor);
        }
        FutureTask<Void> checkActions = new FutureTask<>(() -> {
            checkBackupRow(activity, "export", "Export full backup");
            checkBackupRow(activity, "import", "Import full backup");
            activity.finish();
            return null;
        });
        instrumentation.runOnMainSync(checkActions);
        checkActions.get(10, TimeUnit.SECONDS);
    }

    private static void checkBackupRow(Activity activity, String action, String title) {
        int id = activity.getResources().getIdentifier(action + "_settings", "id", activity.getPackageName());
        View existing = activity.findViewById(id);
        check(existing != null && existing.hasOnClickListeners(), "Native settings-only action was removed: " + action);
        View full = activity.getWindow().getDecorView().findViewWithTag("morphe_full_backup_" + action);
        check(full instanceof ViewGroup && full.isShown() && full.hasOnClickListeners(),
                "Full backup action is unavailable or is not a settings row: " + action);

        TextView nativeLabel = (TextView) ((ViewGroup) existing).getChildAt(0);
        TextView fullLabel = (TextView) ((ViewGroup) full).getChildAt(0);
        check(title.contentEquals(fullLabel.getText())
                        && nativeLabel.getTextSize() == fullLabel.getTextSize()
                        && nativeLabel.getCurrentTextColor() == fullLabel.getCurrentTextColor(),
                "Full backup label does not match the native row: " + action);
        check(existing.getPaddingStart() == full.getPaddingStart() && existing.getPaddingEnd() == full.getPaddingEnd()
                        && existing.getPaddingTop() == full.getPaddingTop()
                        && existing.getPaddingBottom() == full.getPaddingBottom(),
                "Full backup spacing does not match the native row: " + action);
        View nativeRow = (View) existing.getParent(), fullRow = (View) full.getParent();
        check(nativeRow.getBackground().getClass() == fullRow.getBackground().getClass()
                        && dividerAfter(nativeRow).getClass() == dividerAfter(fullRow).getClass(),
                "Full backup background or divider does not match the native row: " + action);
    }

    private static View dividerAfter(View row) {
        ViewGroup group = (ViewGroup) row.getParent();
        return group.getChildAt(group.indexOfChild(row) + 1);
    }

    private static void stageRestore(Context context, int versionOffset) throws Exception {
        Object account = saveNativeRow(context, "User", "name", ACCOUNT, "refreshToken", TOKEN,
                "expirationDate", new Date(0));
        saveNativeRow(context, "Draft", "text", DRAFT, "user", account, "date", new Date(0));
        SharedPreferences prefs = prefs(context, "_preferences");
        check(prefs.edit().putLong(KEY, 42).putStringSet(KEY + ".set", Set.of("fixture & <>", "value")).commit(),
                "Could not seed Boost settings");
        check(prefs(context, ".SUBREDDIT_HISTORY").edit().putString(KEY, SUBREDDIT).commit(),
                "Could not seed visited communities");
        check(prefs(context, ".READ").edit().putBoolean(READ, true).commit(), "Could not seed read posts");
        check(prefs(context, ".POST_HISTORY").edit().putString(KEY, READ).commit(), "Could not seed post history");
        Class<?> database = context.getClassLoader().loadClass("com.rubenmayayo.reddit.room.AppDatabase");
        Object room = database.getMethod("j", Context.class).invoke(null, context);
        // The native Room connection creates or migrates its own schema.
        database.getMethod("c").invoke(room);
        database.getMethod("f").invoke(room);
        try (SQLiteDatabase db = openDatabase(context, "room_database")) {
            db.execSQL("INSERT INTO search (name, query) VALUES (?, ?)", new String[]{KEY, "fixture search"});
        }

        Class<?> storage = context.getClassLoader().loadClass("app.morphe.extension.boost.backup.BoostBackupStorage");
        Method export = storage.getDeclaredMethod("exportBackup", Context.class, java.io.OutputStream.class);
        Method restore = storage.getDeclaredMethod("stageRestore", Context.class, java.io.InputStream.class);
        export.setAccessible(true);
        restore.setAccessible(true);
        ByteArrayOutputStream document = new ByteArrayOutputStream();
        export.invoke(null, context, document);
        byte[] backup = document.toByteArray();
        verifyArchive(context, backup);

        expectInvalid(restore, context, Arrays.copyOf(backup, backup.length / 2), "invalid or damaged");
        expectInvalid(restore, context, rewriteZip(backup, 0, true), "Unexpected or duplicate entry");
        expectInvalid(restore, context, rewriteZip(backup, 1, false), "newer version");
        check(prefs.getLong(KEY, -1) == 42, "A rejected backup modified data");

        check(prefs.edit().remove(KEY).putString(LOCAL, "not in the backup").commit(),
                "Could not create destination-only settings");
        check(prefs(context, ".READ").edit().remove(READ).putBoolean(LOCAL, true).commit(),
                "Could not create destination-only read history");
        try (SQLiteDatabase db = openDatabase(context, "reddit.db")) {
            check(db.delete("User", "Name = ?", new String[]{ACCOUNT}) == 1,
                    "Could not remove the source account before restore");
        }
        try (SQLiteDatabase db = openDatabase(context, "room_database")) {
            db.execSQL("UPDATE search SET query = ? WHERE name = ?", new String[]{LOCAL, KEY});
        }
        saveNativeRow(context, "User", "name", LOCAL);
        byte[] source = versionOffset == 0 ? backup : rewriteZip(backup, versionOffset, false);
        restore.invoke(null, context, new ByteArrayInputStream(source));
        check(prefs.contains(LOCAL), "Staging changed live settings before restart");
    }

    private static void verifyRestored(Context context) {
        SharedPreferences prefs = prefs(context, "_preferences");
        check(prefs.getLong(KEY, -1) == 42 && !prefs.contains(LOCAL), "Full restore did not replace settings");
        check(Set.of("fixture & <>", "value").equals(prefs.getStringSet(KEY + ".set", null)),
                "Full restore lost a native preference type");
        check(SUBREDDIT.equals(prefs(context, ".SUBREDDIT_HISTORY").getString(KEY, "")),
                "Full restore lost visited communities");
        check(prefs(context, ".READ").getBoolean(READ, false) && !prefs(context, ".READ").contains(LOCAL),
                "Full restore did not replace read posts");
        check(READ.equals(prefs(context, ".POST_HISTORY").getString(KEY, "")), "Full restore lost post history");
        try (SQLiteDatabase db = openDatabase(context, "reddit.db")) {
            check(DatabaseUtils.longForQuery(db, "SELECT count(*) FROM User WHERE Name = ?", new String[]{ACCOUNT}) == 1,
                    "Full restore lost the synthetic account");
            check(TOKEN.equals(DatabaseUtils.stringForQuery(db,
                            "SELECT RefreshToken FROM User WHERE Name = ?", new String[]{ACCOUNT})),
                    "Full restore lost the synthetic token");
            check(DatabaseUtils.longForQuery(db, "SELECT count(*) FROM User WHERE Name = ?", new String[]{LOCAL}) == 0,
                    "Full restore merged rather than replaced the account store");
            check(DatabaseUtils.longForQuery(db, "SELECT count(*) FROM Draft WHERE Text = ?", new String[]{DRAFT}) == 1,
                    "Full restore lost the synthetic draft");
        }
        try (SQLiteDatabase db = openDatabase(context, "room_database")) {
            check("fixture search".equals(DatabaseUtils.stringForQuery(db,
                            "SELECT query FROM search WHERE name = ?", new String[]{KEY})),
                    "Full restore lost the saved search");
        }
    }

    private static Object saveNativeRow(Context context, String type, Object... fields) throws Exception {
        Class<?> model = context.getClassLoader().loadClass("com.rubenmayayo.reddit.aa." + type);
        Object row = model.getConstructor().newInstance();
        for (int i = 0; i < fields.length; i += 2) model.getField((String) fields[i]).set(row, fields[i + 1]);
        check(model.getMethod("save").invoke(row) != null, "Could not seed " + type);
        return row;
    }

    private static SharedPreferences prefs(Context context, String suffix) {
        return context.getSharedPreferences(context.getPackageName() + suffix, Context.MODE_PRIVATE);
    }

    private static SQLiteDatabase openDatabase(Context context, String name) {
        return SQLiteDatabase.openDatabase(context.getDatabasePath(name).getPath(), null,
                SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
    }

    private static void verifyArchive(Context context, byte[] backup) throws IOException {
        boolean manifest = false, history = false, account = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(backup))) {
            for (ZipEntry item; (item = zip.getNextEntry()) != null; ) {
                if ("manifest.properties".equals(item.getName())) {
                    Properties properties = new Properties();
                    properties.load(zip);
                    check(context.getPackageName().equals(properties.getProperty("packageName")),
                            "ZIP manifest is missing Boost's package name");
                    manifest = true;
                } else if ("databases/reddit.db".equals(item.getName())) {
                    String sqlite = new String(zip.readAllBytes(), StandardCharsets.ISO_8859_1);
                    check(sqlite.contains(TOKEN), "Synthetic token is missing from the ZIP's unencrypted database");
                    account = true;
                } else if (("shared_prefs/" + context.getPackageName() + ".SUBREDDIT_HISTORY.xml").equals(item.getName())) {
                    check(new String(zip.readAllBytes(), StandardCharsets.UTF_8).contains(SUBREDDIT),
                            "ZIP lost the native visited communities file");
                    history = true;
                }
            }
        }
        check(manifest && account && history, "Full backup is missing named ZIP entries");
    }

    private static byte[] rewriteZip(byte[] backup, int offset, boolean unexpectedEntry) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        try (ZipInputStream source = new ZipInputStream(new ByteArrayInputStream(backup));
             ZipOutputStream output = new ZipOutputStream(result)) {
            for (ZipEntry item; (item = source.getNextEntry()) != null; ) {
                output.putNextEntry(new ZipEntry(item.getName()));
                if ("manifest.properties".equals(item.getName())) {
                    Properties manifest = new Properties();
                    manifest.load(source);
                    long version = Long.parseLong(manifest.getProperty("versionCode"));
                    manifest.setProperty("versionCode", Long.toString(version + offset));
                    manifest.store(output, null);
                } else {
                    source.transferTo(output);
                }
                output.closeEntry();
            }
            if (unexpectedEntry) {
                output.putNextEntry(new ZipEntry("unrecognized.txt"));
                output.write(0);
                output.closeEntry();
            }
        }
        return result.toByteArray();
    }

    private static void expectInvalid(Method restore, Context context, byte[] bytes, String reason) throws Exception {
        try {
            restore.invoke(null, context, new ByteArrayInputStream(bytes));
            throw new AssertionError("Invalid backup was accepted (expected: " + reason + ")");
        } catch (InvocationTargetException expected) {
            if (!(expected.getCause() instanceof IOException exception)
                    || !exception.getMessage().contains(reason)) throw expected;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
