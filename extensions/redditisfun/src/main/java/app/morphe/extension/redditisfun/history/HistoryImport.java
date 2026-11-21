/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.redditisfun.history;

import android.content.ContentProviderClient;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class HistoryImport {
    private static final long MAX_BYTES = 512L * 1024 * 1024;
    private static final String PREFIX = "patcheddit-history-";
    private static final String[] COLUMNS = {"uri", "referer", "accessed", "access_count", "thread_id"};

    public interface Owner {
        SQLiteDatabase morpheHistoryDatabase();
    }

    private HistoryImport() {}

    public static Uri historyUri() {
        throw new IllegalStateException("Native history URI was not bound");
    }

    public static synchronized void onEntry(Context context, ZipInputStream input, ZipEntry entry) {
        if (entry == null || !entry.getName().equals("browserhistory.db")) return;
        File copy = null;
        ContentProviderClient client = null;
        boolean merging = false;
        try {
            // All imports use this lock, so matching leftovers cannot belong to another active import.
            File[] stale = context.getCacheDir().listFiles((dir, name) ->
                    name.startsWith(PREFIX) && name.matches(".*\\.db(?:-(?:journal|wal|shm))?"));
            if (stale != null) {
                for (File file : stale) {
                    String name = file.getName();
                    remove(new File(file.getParentFile(), name.substring(0, name.indexOf(".db") + 3)));
                }
            }
            if (entry.getSize() > MAX_BYTES) throw new IOException("History entry exceeds 512 MiB");
            copy = File.createTempFile(PREFIX, ".db", context.getCacheDir());
            try (FileOutputStream output = new FileOutputStream(copy)) {
                byte[] buffer = new byte[8192];
                long total = 0;
                int count;
                while ((count = input.read(buffer, 0, (int) Math.min(buffer.length, MAX_BYTES + 1 - total))) != -1) {
                    if ((total += count) > MAX_BYTES) throw new IOException("History entry exceeds 512 MiB");
                    output.write(buffer, 0, count);
                }
            }
            try (FileInputStream file = new FileInputStream(copy)) {
                byte[] header = new byte[16];
                if (file.read(header) != header.length
                        || !Arrays.equals(header, "SQLite format 3\0".getBytes(StandardCharsets.US_ASCII))) {
                    throw new IOException("History entry is not a SQLite database");
                }
            }
            try (SQLiteDatabase source = SQLiteDatabase.openDatabase(copy.getPath(), null,
                    SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS)) {
                if (!source.isDatabaseIntegrityOk() || DatabaseUtils.longForQuery(source,
                        "SELECT count(*) FROM sqlite_master WHERE type='table' AND name='uri_history'", null) != 1) {
                    throw new IOException("Invalid history database");
                }
                try (Cursor schema = source.rawQuery("SELECT * FROM uri_history LIMIT 0", null)) {
                    schema.getColumnIndexOrThrow("uri");
                }
                client = context.getContentResolver().acquireContentProviderClient(historyUri());
                if (client == null || !(client.getLocalContentProvider() instanceof Owner)) {
                    throw new IOException("Native history database owner is unavailable");
                }
                merging = true;
                merge(((Owner) client.getLocalContentProvider()).morpheHistoryDatabase(), source,
                        System.currentTimeMillis());
            }
        } catch (IOException | SQLiteException | IllegalArgumentException | IllegalStateException | SecurityException failure) {
            Log.e("PatchedditHistory", "History import failed; preserving existing visits", failure);
            int message = context.getResources().getIdentifier("patcheddit_history_import_failed",
                    "string", context.getPackageName());
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(context.getApplicationContext(), message, Toast.LENGTH_LONG).show());
        } finally {
            if (client != null) client.release();
            if (copy != null) remove(copy);
            if (merging) {
                try {
                    context.getContentResolver().notifyChange(historyUri(), null);
                } catch (SecurityException failure) {
                    Log.e("PatchedditHistory", "Could not notify history observers", failure);
                }
            }
        }
    }

    public static int merge(SQLiteDatabase target, SQLiteDatabase source, long now) {
        int added = 0;
        int processed = 0;
        try (Cursor schema = source.rawQuery("SELECT * FROM uri_history LIMIT 0", null);
             Cursor rows = source.query("uri_history", null, null, null, null, null,
                     schema.getColumnIndex("accessed") < 0 ? null : "accessed DESC");
             SQLiteStatement insert = target.compileStatement(
                     "INSERT INTO uri_history(uri,referer,accessed,access_count,thread_id)"
                             + " SELECT ?,?,?,?,? WHERE NOT EXISTS(SELECT 1 FROM uri_history WHERE uri=? COLLATE BINARY)")) {
            int uri = rows.getColumnIndexOrThrow("uri");
            int[] columns = new int[COLUMNS.length];
            for (int i = 0; i < columns.length; i++) columns[i] = rows.getColumnIndex(COLUMNS[i]);
            // Yields may commit earlier batches; URI deduplication makes retries safe.
            target.beginTransactionNonExclusive();
            try {
                while (rows.moveToNext()) {
                    if (++processed % 500 == 0) target.yieldIfContendedSafely();
                    if (rows.getType(uri) != Cursor.FIELD_TYPE_STRING || rows.getString(uri).isEmpty()) continue;
                    insert.clearBindings();
                    for (int i = 0; i < columns.length; i++) bind(insert, i + 1, rows, columns[i], now);
                    insert.bindString(6, rows.getString(uri));
                    if (insert.executeInsert() != -1) added++;
                }
                target.setTransactionSuccessful();
            } finally {
                target.endTransaction();
            }
        }
        return added;
    }

    private static void bind(SQLiteStatement insert, int parameter, Cursor row, int column, long now) {
        if (column < 0) {
            if (parameter == 2 || parameter == 5) insert.bindString(parameter, "");
            else insert.bindLong(parameter, parameter == 3 ? now : 1);
            return;
        }
        switch (row.getType(column)) {
            case Cursor.FIELD_TYPE_NULL: insert.bindNull(parameter); break;
            case Cursor.FIELD_TYPE_INTEGER: insert.bindLong(parameter, row.getLong(column)); break;
            case Cursor.FIELD_TYPE_FLOAT: insert.bindDouble(parameter, row.getDouble(column)); break;
            case Cursor.FIELD_TYPE_BLOB: insert.bindBlob(parameter, row.getBlob(column)); break;
            default: insert.bindString(parameter, row.getString(column));
        }
    }

    private static void remove(File file) {
        if (!SQLiteDatabase.deleteDatabase(file) && file.exists()) {
            Log.w("PatchedditHistory", "Could not remove a temporary history database");
        }
    }
}
