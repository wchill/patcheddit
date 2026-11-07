/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.debug;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class FatalCrashLogs {
    private static final String TAG = "MorpheCrashLogs";
    // Naming and locations used by morphe-patches-library's CrashLogPatch.
    private static final String FOLDER = "Morphe";

    private FatalCrashLogs() {}

    public static final class Entry {
        public final String name;
        public final Uri uri;

        private Entry(String name, Uri uri) {
            this.name = name;
            this.uri = uri;
        }
    }

    public static final class Listing {
        public final List<Entry> entries = new ArrayList<>();
        public boolean incomplete;
    }

    public static Listing list(Context context) {
        Listing result = new Listing();
        try {
            File appDocuments = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            if (appDocuments != null) addFolder(result.entries, appDocuments, context.getPackageName());
        } catch (IOException | SecurityException failure) {
            result.incomplete = true;
            Log.w(TAG, "Could not list app-specific fatal crash reports.", failure);
        }
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                addMediaStore(result.entries, context);
            } else if (context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED
                    || context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                addFolder(result.entries, publicFolder(), context.getPackageName());
            } else {
                result.incomplete = true;
            }
        } catch (IOException | SecurityException | IllegalArgumentException failure) {
            result.incomplete = true;
            Log.w(TAG, "Could not list public fatal crash reports; use the document picker.", failure);
        }
        result.entries.sort((left, right) -> right.name.compareTo(left.name));
        return result;
    }

    private static void addFolder(List<Entry> entries, File folder, String packageName) throws IOException {
        if (!folder.exists()) return;
        File[] files = folder.listFiles();
        if (files == null) throw new IOException("Could not list the crash report folder.");
        for (File file : files) {
            if (file.isFile() && isCrashLogName(file.getName(), packageName)) {
                entries.add(new Entry(file.getName(), Uri.fromFile(file)));
            }
        }
    }

    private static void addMediaStore(List<Entry> entries, Context context) throws IOException {
        Uri collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        String folder = Environment.DIRECTORY_DOCUMENTS + "/" + FOLDER;
        String[] projection = {MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME};
        String selection = MediaStore.MediaColumns.RELATIVE_PATH + " IN (?, ?)";
        try (Cursor cursor = context.getContentResolver().query(
                collection, projection, selection, new String[]{folder, folder + "/"}, null)) {
            if (cursor == null) throw new IOException("The crash report provider is unavailable.");
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
            int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
            while (cursor.moveToNext()) {
                String name = cursor.getString(nameColumn);
                if (isCrashLogName(name, context.getPackageName())) {
                    entries.add(new Entry(name, ContentUris.withAppendedId(collection, cursor.getLong(idColumn))));
                }
            }
        }
    }

    static boolean isCrashLogName(String name, String packageName) {
        String prefix = "crash_" + packageName + "_";
        return name != null && name.startsWith(prefix)
                && name.substring(prefix.length()).matches(
                        "\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}(?: \\(\\d+\\))?\\.txt"
                );
    }

    private static File publicFolder() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), FOLDER);
    }

    private static InputStream open(Context context, Uri uri) throws IOException {
        if ("content".equals(uri.getScheme())) {
            InputStream stream = context.getContentResolver().openInputStream(uri);
            if (stream == null) throw new IOException("The selected crash report is unavailable.");
            return stream;
        }
        if ("file".equals(uri.getScheme()) && uri.getPath() != null) {
            File file = new File(uri.getPath()).getCanonicalFile();
            File appDocuments = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            File parent = file.getParentFile();
            boolean appFile = appDocuments != null && appDocuments.getCanonicalFile().equals(parent);
            if ((!appFile && !publicFolder().getCanonicalFile().equals(parent))
                    || !isCrashLogName(file.getName(), context.getPackageName())) {
                throw new IOException("The selected file is not a crash report for this app.");
            }
            return new FileInputStream(file);
        }
        throw new IOException("Unsupported crash report location.");
    }

    public static Preview preview(Context context, Uri uri, int limit) throws IOException {
        try (Reader reader = new InputStreamReader(open(context, uri), StandardCharsets.UTF_8)) {
            return readTail(reader, limit);
        }
    }

    static Preview readTail(Reader reader, int limit) throws IOException {
        if (limit <= 0) throw new IOException("Invalid crash preview limit.");
        StringBuilder tail = new StringBuilder();
        char[] buffer = new char[4096];
        boolean truncated = false;
        int count;
        while ((count = reader.read(buffer)) != -1) {
            checkInterrupted();
            tail.append(buffer, 0, count);
            if (tail.length() > limit) {
                tail.delete(0, tail.length() - limit);
                truncated = true;
            }
        }
        return new Preview(tail.toString(), truncated);
    }

    public static final class Preview {
        public final String text;
        public final boolean truncated;

        private Preview(String text, boolean truncated) {
            this.text = text;
            this.truncated = truncated;
        }
    }

    public static void copy(Context context, Uri source, OutputStream destination) throws IOException {
        try (InputStream stream = open(context, source)) {
            copy(stream, destination);
        }
    }

    static void copy(InputStream source, OutputStream destination) throws IOException {
        byte[] buffer = new byte[8192];
        int count;
        while ((count = source.read(buffer)) != -1) {
            checkInterrupted();
            destination.write(buffer, 0, count);
        }
    }

    private static void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Crash report operation cancelled.");
    }
}
