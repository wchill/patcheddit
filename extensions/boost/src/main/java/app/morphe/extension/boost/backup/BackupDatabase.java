/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.boost.backup;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class BackupDatabase {
    private static final int OPEN = SQLiteDatabase.NO_LOCALIZED_COLLATORS;
    private static final String SCHEMA = "SELECT type, name, sql FROM sqlite_master"
            + " WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' AND name <> 'android_metadata'"
            + " ORDER BY CASE type WHEN 'table' THEN 0 ELSE 1 END, name";

    private BackupDatabase() {}

    static void snapshot(File original, File snapshot) {
        try (SQLiteDatabase source = SQLiteDatabase.openDatabase(original.getPath(), null, OPEN | SQLiteDatabase.OPEN_READWRITE);
             SQLiteDatabase target = SQLiteDatabase.openDatabase(snapshot.getPath(), null, OPEN | SQLiteDatabase.CREATE_IF_NECESSARY)) {
            // Read from SQLite, not the file: the latest committed rows may still be in the WAL.
            source.beginTransactionNonExclusive();
            try {
                target.beginTransaction();
                try {
                    List<String> deferred = new ArrayList<>();
                    try (Cursor schema = source.rawQuery(SCHEMA, null)) {
                        while (schema.moveToNext()) {
                            String sql = schema.getString(2);
                            if ("table".equals(schema.getString(0))) {
                                target.execSQL(sql);
                                copyRows(source, target, schema.getString(1));
                            } else {
                                deferred.add(sql);
                            }
                        }
                    }
                    if (hasTable(source, "sqlite_sequence")) {
                        target.delete("sqlite_sequence", null, null);
                        copyRows(source, target, "sqlite_sequence");
                    }
                    for (String sql : deferred) target.execSQL(sql);
                    target.setVersion(source.getVersion());
                    target.setTransactionSuccessful();
                } finally {
                    target.endTransaction();
                }
                source.setTransactionSuccessful();
            } finally {
                source.endTransaction();
            }
        }
    }

    private static void copyRows(SQLiteDatabase source, SQLiteDatabase target, String table) {
        String name = '"' + table.replace("\"", "\"\"") + '"';
        try (Cursor rows = source.rawQuery("SELECT * FROM " + name, null)) {
            if (!rows.moveToFirst()) return;
            int columns = rows.getColumnCount();
            StringBuilder values = new StringBuilder("?");
            for (int i = 1; i < columns; i++) values.append(",?");
            try (SQLiteStatement insert = target.compileStatement("INSERT INTO " + name + " VALUES (" + values + ")")) {
                do {
                    for (int i = 0; i < columns; i++) {
                        switch (rows.getType(i)) {
                            case Cursor.FIELD_TYPE_NULL -> insert.bindNull(i + 1);
                            case Cursor.FIELD_TYPE_INTEGER -> insert.bindLong(i + 1, rows.getLong(i));
                            case Cursor.FIELD_TYPE_FLOAT -> insert.bindDouble(i + 1, rows.getDouble(i));
                            case Cursor.FIELD_TYPE_STRING -> insert.bindString(i + 1, rows.getString(i));
                            case Cursor.FIELD_TYPE_BLOB -> insert.bindBlob(i + 1, rows.getBlob(i));
                            default -> throw new IllegalStateException("Unknown SQLite value type");
                        }
                    }
                    insert.executeInsert();
                } while (rows.moveToNext());
            }
        }
    }

    static void validate(File file, String requiredTable) throws IOException {
        try (SQLiteDatabase backup = SQLiteDatabase.openDatabase(file.getPath(), null, OPEN | SQLiteDatabase.OPEN_READONLY);
             Cursor check = backup.rawQuery("PRAGMA quick_check(1)", null)) {
            if (!hasTable(backup, requiredTable) || !check.moveToFirst() || !"ok".equals(check.getString(0))) {
                throw new IOException("A database in the backup is invalid.");
            }
        }
    }

    private static boolean hasTable(SQLiteDatabase db, String name) {
        try (Cursor cursor = db.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? COLLATE NOCASE", new String[]{name})) {
            return cursor.moveToFirst();
        }
    }
}
