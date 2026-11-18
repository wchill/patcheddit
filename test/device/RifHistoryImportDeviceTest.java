/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.patcheddit.fixture;

import android.content.ContentProvider;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.content.pm.ProviderInfo;
import android.database.DatabaseErrorHandler;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;
import android.os.Bundle;
import android.test.mock.MockContentResolver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.util.concurrent.FutureTask;

public final class RifHistoryImportDeviceTest {
    private static final String SCHEMA = "CREATE TABLE uri_history(_id INTEGER PRIMARY KEY,uri TEXT,"
            + "referer TEXT,accessed INTEGER,access_count INTEGER,thread_id TEXT)";

    public static void run(Context context, Bundle arguments) throws Exception {
        Sandbox sandbox = new Sandbox(context);
        SQLiteDatabase target = null;
        try {
            ClassLoader loader = context.getClassLoader();
            Class<?> hook = loader.loadClass("app.morphe.extension.redditisfun.history.HistoryImport");
            Uri uri = (Uri) hook.getMethod("historyUri").invoke(null);
            ContentProvider provider = (ContentProvider) loader
                    .loadClass("com.andrewshu.android.reddit.history.UriHistoryProvider").getConstructor().newInstance();
            ProviderInfo info = new ProviderInfo();
            info.authority = uri.getAuthority();
            provider.attachInfo(sandbox, info);
            MockContentResolver resolver = new MockContentResolver(sandbox);
            resolver.addProvider(uri.getAuthority(), provider);
            sandbox.resolver = resolver;
            try (ContentProviderClient client = sandbox.resolver.acquireContentProviderClient(uri)) {
                check(client != null && client.getLocalContentProvider() == provider, "Sandbox provider routing failed");
            }
            target = (SQLiteDatabase) provider.getClass().getMethod("morpheHistoryDatabase").invoke(provider);
            check(new File(target.getPath()).getCanonicalPath().startsWith(sandbox.root.getCanonicalPath() + "/"),
                    "Refusing to touch a non-fixture history database");
            Method merge = hook.getMethod("merge", SQLiteDatabase.class, SQLiteDatabase.class, long.class);
            Method onEntry = hook.getMethod("onEntry", Context.class, ZipInputStream.class, ZipEntry.class);
            target.execSQL("INSERT INTO uri_history VALUES(55,'existing','local',7,2,'t3_local')");
            File sourceFile = new File(sandbox.root, "source.db");
            try (SQLiteDatabase source = SQLiteDatabase.openOrCreateDatabase(sourceFile, null)) {
                source.execSQL(SCHEMA);
                source.execSQL("INSERT INTO uri_history VALUES(300,'existing','remote',999,99,'t3_remote')");
                source.execSQL("INSERT INTO uri_history VALUES(1,'fresh','older',10,1,'t3_old')");
                source.execSQL("INSERT INTO uri_history VALUES(2,'fresh','newer',20,8,'t3_new')");
                source.execSQL("INSERT INTO uri_history VALUES(3,'nullable',NULL,NULL,NULL,NULL)");
                check((Integer) merge.invoke(null, target, source, 123L) == 2, "Wrong additive merge count");
                check((Integer) merge.invoke(null, target, source, 123L) == 0, "Repeat import was not idempotent");
            }
            check(DatabaseUtils.longForQuery(target, "SELECT _id FROM uri_history WHERE uri='existing'", null) == 55,
                    "Imported an internal row ID or replaced a local row");
            check("local".equals(DatabaseUtils.stringForQuery(target,
                    "SELECT referer FROM uri_history WHERE uri='existing'", null)), "Updated existing metadata");
            check("newer".equals(DatabaseUtils.stringForQuery(target,
                    "SELECT referer FROM uri_history WHERE uri='fresh'", null)), "Did not choose the newest duplicate");
            check(DatabaseUtils.longForQuery(target,
                    "SELECT count(*) FROM uri_history WHERE uri='nullable' AND referer IS NULL"
                            + " AND accessed IS NULL AND access_count IS NULL AND thread_id IS NULL", null) == 1,
                    "Replaced explicit null metadata with defaults");

            try (SQLiteDatabase minimal = SQLiteDatabase.create(null)) {
                minimal.execSQL("CREATE TABLE uri_history(uri)");
                minimal.execSQL("INSERT INTO uri_history VALUES('minimal'),(''),(NULL),(42),(x'0102')");
                check((Integer) merge.invoke(null, target, minimal, 123L) == 1, "Accepted non-text/empty URI rows");
                check(DatabaseUtils.longForQuery(target,
                        "SELECT count(*) FROM uri_history WHERE uri='minimal' AND referer='' AND accessed=123"
                                + " AND access_count=1 AND thread_id=''", null) == 1, "Wrong missing-column defaults");
            }
            byte[] database;
            try (InputStream input = new FileInputStream(sourceFile)) { database = input.readAllBytes(); }
            File stale = new File(sandbox.getCacheDir(), "patcheddit-history-stale.db");
            java.nio.file.Files.write(stale.toPath(), new byte[]{1});
            File unrelated = new File(sandbox.getCacheDir(), "keep.db");
            java.nio.file.Files.write(unrelated.toPath(), new byte[]{1});
            byte[] zip = zip(database, "after.txt", "remaining");
            try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zip))) {
                onEntry.invoke(null, sandbox, input, input.getNextEntry());
                input.closeEntry();
                check(input.getNextEntry().getName().equals("after.txt"), "History consumed the next ZIP entry");
                check(new String(input.readAllBytes(), StandardCharsets.UTF_8).equals("remaining"), "Lost trailing ZIP data");
            }
            check(!stale.exists() && unrelated.exists(), "Cleanup affected an unrelated file");
            try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zip))) {
                ZipEntry entry = input.getNextEntry();
                onEntry.invoke(null, sandbox, input, new ZipEntry("nested/browserhistory.db"));
                check(java.util.Arrays.equals(database, input.readAllBytes()), "Consumed a non-matching entry");
            }
            long before = DatabaseUtils.queryNumEntries(target, "uri_history");
            try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zip(new byte[]{1, 2}, "after.txt", "safe")))) {
                onEntry.invoke(null, sandbox, input, input.getNextEntry());
                input.closeEntry();
                check(input.getNextEntry().getName().equals("after.txt"), "Invalid history aborted remaining entries");
            }
            check(DatabaseUtils.queryNumEntries(target, "uri_history") == before, "Invalid history mutated the target");
            nativeImport(context, sandbox, database);
            contention(target, merge);

            long limit = 512L * 1024 * 1024;
            long[] read = {0};
            ZipInputStream oversized = new ZipInputStream(new ByteArrayInputStream(new byte[0])) {
                @Override public int read(byte[] bytes, int offset, int length) {
                    if (read[0] == limit + 100) return -1;
                    int count = (int) Math.min(length, limit + 100 - read[0]);
                    java.util.Arrays.fill(bytes, offset, offset + count, (byte) 0);
                    read[0] += count;
                    return count;
                }
            };
            onEntry.invoke(null, sandbox, oversized, new ZipEntry("browserhistory.db"));
            check(read[0] == limit + 1, "Decompressed history exceeded the exact size ceiling");
            check(sandbox.getCacheDir().listFiles((dir, name) -> name.startsWith("patcheddit-history-")).length == 0,
                    "An import left temporary databases behind");
        } finally {
            if (target != null) target.close();
            remove(sandbox.root);
        }
    }

    private static void contention(SQLiteDatabase target, Method merge) throws Exception {
        Throwable[] failure = {null};
        Thread writer = new Thread(() -> {
            try {
                target.execSQL("INSERT INTO uri_history(uri,referer) VALUES('batch_1100','local-race')");
            } catch (Throwable error) {
                failure[0] = error;
            }
        });
        target.setCustomScalarFunction("fixture_writer", value -> {
            writer.start();
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (writer.getState() != Thread.State.WAITING && writer.getState() != Thread.State.TIMED_WAITING) {
                check(writer.isAlive() && System.nanoTime() < deadline, "Native writer did not queue behind import");
                java.util.concurrent.locks.LockSupport.parkNanos(1_000_000);
            }
            return value;
        });
        target.execSQL("CREATE TEMP TRIGGER fixture_queue BEFORE INSERT ON uri_history"
                + " WHEN new.uri='batch_0498' BEGIN SELECT fixture_writer(new.uri); END");
        target.execSQL("CREATE TEMP TRIGGER fixture_fail BEFORE INSERT ON uri_history"
                + " WHEN new.uri='batch_0599' BEGIN SELECT RAISE(ABORT,'fixture batch failure'); END");
        try (SQLiteDatabase source = SQLiteDatabase.create(null)) {
            source.execSQL("CREATE TABLE uri_history(uri TEXT,accessed INTEGER)");
            source.beginTransaction();
            try (SQLiteStatement insert = source.compileStatement("INSERT INTO uri_history VALUES(?,?)")) {
                for (int i = 0; i < 1200; i++) {
                    insert.bindString(1, String.format(java.util.Locale.ROOT, "batch_%04d", i));
                    insert.bindLong(2, 1200 - i);
                    insert.executeInsert();
                }
                source.setTransactionSuccessful();
            } finally {
                source.endTransaction();
            }
            try {
                merge.invoke(null, target, source, 123L);
                throw new AssertionError("Fixture failure did not interrupt the import");
            } catch (InvocationTargetException expected) {
                check(expected.getCause() instanceof SQLiteException, "Unexpected merge failure");
            }
            writer.join(5000);
            check(!writer.isAlive() && failure[0] == null, "Import did not yield to the native writer");
            check(DatabaseUtils.longForQuery(target,
                    "SELECT count(*) FROM uri_history WHERE uri LIKE 'batch_%'", null) == 500,
                    "Failure lost a yielded batch or committed the failed batch");
            target.execSQL("DROP TRIGGER fixture_fail");
            target.execSQL("DROP TRIGGER fixture_queue");
            check((Integer) merge.invoke(null, target, source, 123L) == 700, "Partial import was not safely repeatable");
            check((Integer) merge.invoke(null, target, source, 123L) == 0, "Large import was not idempotent");
            check("local-race".equals(DatabaseUtils.stringForQuery(target,
                    "SELECT referer FROM uri_history WHERE uri='batch_1100'", null)), "Replaced a concurrent native visit");
        }
    }

    private static void nativeImport(Context context, Sandbox sandbox, byte[] database) throws Exception {
        ClassLoader loader = context.getClassLoader();
        boolean paid = context.getPackageName().endsWith("donation");
        String[] names = (String[]) loader.loadClass(paid ? "o4.a" : "n4.a").getField("b").get(null);
        String preference = java.util.Arrays.stream(names).filter(name -> !name.contains(".")).findFirst().orElseThrow();
        String xml = "<?xml version='1.0' encoding='utf-8'?><map><string name='fixture'>safe</string></map>";
        File archive = new File(sandbox.root, "backup.zip");
        java.nio.file.Files.write(archive.toPath(), zip(database, preference + ".xml", xml));
        Class<?> activityType = loader.loadClass("com.andrewshu.android.reddit.settings.export.SettingsExportImportActivity");
        FutureTask<Object> create = new FutureTask<>(() -> {
            Object activity = activityType.getConstructor().newInstance();
            Method attach = ContextWrapper.class.getDeclaredMethod("attachBaseContext", Context.class);
            attach.setAccessible(true);
            attach.invoke(activity, sandbox);
            return activity;
        });
        PatchedditInstrumentation.instance.runOnMainSync(create);
        Object activity = create.get();
        Method path = loader.loadClass(paid ? "p5.k0" : "o5.k0").getMethod("a", String.class, Context.class);
        File settings = (File) path.invoke(null, preference, activity);
        check(settings.getCanonicalPath().startsWith(sandbox.root.getCanonicalPath() + "/"),
                "Refusing to write real native preferences");
        Class<?> importer = loader.loadClass("com.andrewshu.android.reddit.settings.export.c");
        java.lang.reflect.Constructor<?> constructor = importer.getDeclaredConstructor(Uri.class, activityType);
        constructor.setAccessible(true);
        Object task = constructor.newInstance(Uri.fromFile(archive), activity);
        Method run = importer.getDeclaredMethod("A", Void[].class);
        run.setAccessible(true);
        check(Boolean.TRUE.equals(run.invoke(task, (Object) new Void[0])), "Native settings import failed");
        check(settings.isFile() && new String(java.nio.file.Files.readAllBytes(settings.toPath()), StandardCharsets.UTF_8)
                .equals(xml), "Native settings import did not continue after history");
    }

    private static byte[] zip(byte[] history, String next, String contents) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("browserhistory.db"));
            zip.write(history);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(next));
            zip.write(contents.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static final class Sandbox extends ContextWrapper {
        final File root;
        ContentResolver resolver;
        Sandbox(Context context) {
            super(context);
            File[] leftovers = context.getCacheDir().listFiles((dir, name) ->
                    name.matches("patcheddit-history-fixture-[0-9]+"));
            if (leftovers != null) for (File file : leftovers) remove(file);
            root = new File(context.getCacheDir(), "patcheddit-history-fixture-" + System.nanoTime());
            for (String directory : new String[]{"files", "cache", "databases", "shared_prefs"}) {
                check(new File(root, directory).mkdirs(), "Could not create fixture directories");
            }
        }
        @Override public Context getApplicationContext() { return this; }
        @Override public File getFilesDir() { return new File(root, "files"); }
        @Override public File getCacheDir() { return new File(root, "cache"); }
        @Override public File getDatabasePath(String name) { return new File(root, "databases/" + name); }
        public File getSharedPreferencesPath(String name) { return new File(root, "shared_prefs/" + name + ".xml"); }
        public File getSharedPrefsFile(String name) { return getSharedPreferencesPath(name); }
        @Override public ContentResolver getContentResolver() {
            return resolver == null ? super.getContentResolver() : resolver;
        }
        @Override public ApplicationInfo getApplicationInfo() {
            ApplicationInfo info = new ApplicationInfo(super.getApplicationInfo());
            info.dataDir = root.getPath();
            return info;
        }
        @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory) {
            return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory);
        }
        @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory,
                                                            DatabaseErrorHandler handler) {
            return SQLiteDatabase.openDatabase(getDatabasePath(name).getPath(), factory,
                    SQLiteDatabase.CREATE_IF_NECESSARY, handler);
        }
        @Override public boolean deleteDatabase(String name) { return SQLiteDatabase.deleteDatabase(getDatabasePath(name)); }
    }

    private static void remove(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) remove(child);
        check(file.delete(), "Could not delete fixture data");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
