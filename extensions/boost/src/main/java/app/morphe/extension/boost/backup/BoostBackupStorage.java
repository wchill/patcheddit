/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.boost.backup;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.zip.CheckedInputStream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import app.morphe.extension.shared.settings.MatureContentLock;

final class BoostBackupStorage {
    private static final String FORMAT_VERSION = "1";
    private static final String MANIFEST = "manifest.properties";
    private static final long MAX_MANIFEST_SIZE = 4 * 1024;
    private static final long MAX_PAYLOAD_SIZE = 128L * 1024 * 1024;
    private static final long MAX_ZIP_SIZE = MAX_PAYLOAD_SIZE + 1024 * 1024;
    private static final String PREF_DIR = "shared_prefs/";
    private static final String DB_DIR = "databases/";
    private static final String ROOT = "patcheddit-full-backup";
    private static final String WORK = "staging";
    private static final String PENDING = "pending";
    private static final String[] PREFERENCES = {
            "_preferences", "VIEW_PER_SUBSCRIPTION", "SORT_PER_SUBSCRIPTION", "READ",
            "LAST_VISIT", "POST_HISTORY", "SUBREDDIT_HISTORY", "USER_TAGS", "DONE",
    };
    private static final String[] DATABASES = {"reddit.db", "room_database"};
    private static final String[] REQUIRED_TABLES = {"User", "room_master_table"};

    private BoostBackupStorage() {}

    static synchronized void exportBackup(Context context, OutputStream document) throws IOException {
        Path root = root(context);
        Path stage = Files.createDirectory(root.resolve(WORK));
        try {
            File[] stores = stores(context);
            for (int i = 0; i < stores.length; i++) {
                File target = entry(stage, i).toFile();
                if (i < PREFERENCES.length) {
                    snapshotPreferences(context, i, target);
                } else if (stores[i].isFile()) {
                    BackupDatabase.snapshot(stores[i], target);
                } else if (i == PREFERENCES.length) {
                    throw new IOException("Boost's account and draft database is unavailable.");
                }
            }
            Properties manifest = new Properties();
            manifest.setProperty("formatVersion", FORMAT_VERSION);
            manifest.setProperty("packageName", context.getPackageName());
            manifest.setProperty("versionCode", Long.toString(version(context)));
            try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(document))) {
                zip.putNextEntry(new ZipEntry(MANIFEST));
                manifest.store(zip, "Boost full backup");
                zip.closeEntry();
                long remaining = MAX_PAYLOAD_SIZE;
                for (int i = 0; i < stores.length; i++) {
                    Path payload = entry(stage, i);
                    if (!Files.exists(payload)) continue;
                    long length = Files.size(payload);
                    if (length > remaining) throw new IOException("Backup is too large.");
                    remaining -= length;
                    zip.putNextEntry(new ZipEntry(archiveName(stores, i)));
                    Files.copy(payload, zip);
                    zip.closeEntry();
                }
            }
        } finally {
            clear(stage);
        }
    }

    static synchronized void stageRestore(Context context, InputStream document) throws IOException {
        Path root = root(context);
        Path pending = root.resolve(PENDING);
        if (Files.exists(pending)) throw new IOException("Reopen Boost to finish the previous restore first.");
        Path stage = Files.createDirectory(root.resolve(WORK));
        try {
            Path archive = stage.resolve("backup.zip");
            try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(archive))) {
                copy(document, output, MAX_ZIP_SIZE, "Backup is too large.");
            }
            try (ZipFile zip = new ZipFile(archive.toFile())) {
                unpack(context, zip, stage);
            }
            Files.delete(archive);
            Files.move(stage, pending, StandardCopyOption.ATOMIC_MOVE);
        } catch (ZipException exception) {
            throw new IOException("The backup ZIP is invalid or damaged.", exception);
        } finally {
            clear(stage);
        }
    }

    private static void unpack(Context context, ZipFile zip, Path stage) throws IOException {
        File[] stores = stores(context);
        Set<String> names = new HashSet<>(Set.of(MANIFEST, PREF_DIR, DB_DIR));
        for (int i = 0; i < stores.length; i++) names.add(archiveName(stores, i));
        var entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry item = entries.nextElement();
            if (!names.remove(item.getName()) || (item.isDirectory() && item.getSize() != 0)) {
                throw new IOException("Unexpected or duplicate entry in the backup ZIP.");
            }
        }
        checkManifest(context, zip);
        long remaining = MAX_PAYLOAD_SIZE;
        for (int i = 0; i < stores.length; i++) {
            ZipEntry item = zip.getEntry(archiveName(stores, i));
            Path file = entry(stage, i); // Never resolve a path supplied by the archive.
            if (item == null && i > PREFERENCES.length) {
                Files.createFile(file); // The source did not have this optional database.
                continue;
            }
            long size = entrySize(item, remaining);
            remaining -= size;
            try (FileOutputStream output = new FileOutputStream(file.toFile())) {
                extract(zip, item, size, output);
                output.getFD().sync();
            }
            if (i < PREFERENCES.length) {
                readPreferences(file);
            } else {
                BackupDatabase.validate(file.toFile(), REQUIRED_TABLES[i - PREFERENCES.length]);
            }
        }
    }

    private static void checkManifest(Context context, ZipFile zip) throws IOException {
        ZipEntry item = zip.getEntry(MANIFEST);
        long size = entrySize(item, MAX_MANIFEST_SIZE);
        var bytes = new ByteArrayOutputStream((int) size);
        extract(zip, item, size, bytes);
        Properties manifest = new Properties();
        long sourceVersion;
        try {
            manifest.load(new ByteArrayInputStream(bytes.toByteArray()));
            sourceVersion = Long.parseLong(manifest.getProperty("versionCode", ""));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid manifest in the backup ZIP.", exception);
        }
        if (!FORMAT_VERSION.equals(manifest.getProperty("formatVersion")) || sourceVersion <= 0) {
            throw new IOException("This is not a supported full Boost backup.");
        }
        if (!context.getPackageName().equals(manifest.getProperty("packageName"))) {
            throw new IOException("This full backup belongs to a different app.");
        }
        if (sourceVersion > version(context)) {
            throw new IOException("This backup is from a newer version of Boost. Update Boost to restore it.");
        }
    }

    private static long entrySize(ZipEntry item, long limit) throws IOException {
        if (item == null || item.getSize() <= 0 || item.getSize() > limit) {
            throw new IOException("Missing or invalid entry in the backup ZIP.");
        }
        return item.getSize();
    }

    private static void extract(ZipFile zip, ZipEntry item, long size, OutputStream output) throws IOException {
        try (CheckedInputStream input = new CheckedInputStream(zip.getInputStream(item), new CRC32())) {
            long copied = copy(input, output, size, "Invalid entry size in the backup ZIP.");
            if (copied != size || input.getChecksum().getValue() != item.getCrc()) {
                throw new IOException("The backup ZIP is damaged.");
            }
        }
    }

    private static String archiveName(File[] stores, int index) {
        return (index < PREFERENCES.length ? PREF_DIR : DB_DIR) + stores[index].getName();
    }

    static synchronized boolean applyPendingRestore(Context context) throws IOException {
        Path root = root(context);
        Path pending = root.resolve(PENDING);
        if (!Files.exists(pending)) return false;
        File[] stores = stores(context);
        for (int i = 0; i < stores.length; i++) {
            Path staged = entry(pending, i);
            if (!Files.exists(staged)) continue; // Already installed before an interrupted startup.
            File target = stores[i];
            if (i == 0) preserveMatureContentLock(target.toPath(), staged);
            Files.createDirectories(target.toPath().getParent());
            String[] sidecars = i < PREFERENCES.length ? new String[]{".bak"}
                    : new String[]{"-wal", "-shm", "-journal"};
            for (String suffix : sidecars) Files.deleteIfExists(new File(target + suffix).toPath());
            if (Files.size(staged) == 0) {
                Files.deleteIfExists(target.toPath());
                Files.delete(staged);
            } else {
                Files.move(staged, target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Files.delete(pending);
        return true;
    }

    static void clearInterruptedStage(Context context) throws IOException {
        clear(new File(context.getNoBackupFilesDir(), ROOT + "/" + WORK).toPath());
    }

    private static Path root(Context context) throws IOException {
        return Files.createDirectories(new File(context.getNoBackupFilesDir(), ROOT).toPath());
    }

    private static void clear(Path stage) throws IOException {
        if (!Files.isDirectory(stage)) return;
        try (var files = Files.newDirectoryStream(stage)) {
            for (Path file : files) Files.delete(file);
        }
        Files.delete(stage);
    }

    private static Path entry(Path stage, int index) {
        return stage.resolve(Integer.toString(index));
    }

    @SuppressWarnings("deprecation")
    private static long version(Context context) throws IOException {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        } catch (PackageManager.NameNotFoundException exception) {
            throw new IOException("Could not determine Boost's version.", exception);
        }
    }

    private static String prefName(Context context, int index) {
        return context.getPackageName() + (index == 0 ? "" : ".") + PREFERENCES[index];
    }

    private static File[] stores(Context context) {
        File[] result = new File[PREFERENCES.length + DATABASES.length];
        File preferences = new File(context.getDataDir(), "shared_prefs");
        for (int i = 0; i < PREFERENCES.length; i++) {
            result[i] = new File(preferences, prefName(context, i) + ".xml");
        }
        for (int i = 0; i < DATABASES.length; i++) {
            result[PREFERENCES.length + i] = context.getDatabasePath(DATABASES[i]);
        }
        return result;
    }

    // Snapshot the in-memory preferences, including edits that Android hasn't flushed to disk yet.
    private static void snapshotPreferences(Context context, int index, File file) throws IOException {
        Map<String, ?> values = context.getSharedPreferences(prefName(context, index), Context.MODE_PRIVATE).getAll();
        writePreferences(values, file.toPath());
    }

    private static void writePreferences(Map<String, ?> values, Path file) throws IOException {
        try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(file))) {
            XmlSerializer xml = Xml.newSerializer();
            xml.setOutput(output, "UTF-8");
            xml.startDocument("UTF-8", true);
            xml.startTag(null, "map");
            for (Map.Entry<String, ?> entry : values.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (value instanceof String) {
                    element(xml, "string", key, (String) value, null);
                } else if (value instanceof Set<?>) {
                    xml.startTag(null, "set").attribute(null, "name", key);
                    for (Object string : (Set<?>) value) element(xml, "string", null, (String) string, null);
                    xml.endTag(null, "set");
                } else {
                    String type;
                    if (value instanceof Integer) type = "int";
                    else if (value instanceof Long) type = "long";
                    else if (value instanceof Float) type = "float";
                    else if (value instanceof Boolean) type = "boolean";
                    else throw new IOException("Unsupported Boost setting type.");
                    element(xml, type, key, null, value.toString());
                }
            }
            xml.endTag(null, "map");
            xml.endDocument();
        }
    }

    private static void element(XmlSerializer xml, String type, String name, String text, String value)
            throws IOException {
        xml.startTag(null, type);
        if (name != null) xml.attribute(null, "name", name);
        if (text != null) xml.text(text);
        if (value != null) xml.attribute(null, "value", value);
        xml.endTag(null, type);
    }

    private static void preserveMatureContentLock(Path target, Path staged) throws IOException {
        Path rollback = target.resolveSibling(target.getFileName() + ".bak");
        Path current = Files.exists(rollback) ? rollback : target;
        if (!Files.exists(current) || !Boolean.TRUE.equals(readPreferences(current).get(MatureContentLock.KEY))) {
            return;
        }
        Map<String, Object> imported = readPreferences(staged);
        if (Boolean.TRUE.equals(imported.put(MatureContentLock.KEY, true))) return;
        Path replacement = staged.resolveSibling("lock-preserved.xml");
        writePreferences(imported, replacement);
        Files.move(replacement, staged, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private static Map<String, Object> readPreferences(Path file) throws IOException {
        Map<String, Object> values = new LinkedHashMap<>();
        try (InputStream input = Files.newInputStream(file)) {
            XmlPullParser xml = Xml.newPullParser();
            xml.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false);
            xml.setInput(input, "UTF-8");
            if (xml.nextTag() != XmlPullParser.START_TAG || !"map".equals(xml.getName())) {
                throw new IOException("Invalid settings in the backup.");
            }
            while (xml.nextTag() == XmlPullParser.START_TAG) {
                String name = xml.getAttributeValue(null, "name");
                if (name == null) {
                    throw new IOException("Invalid settings in the backup.");
                }
                String value = xml.getAttributeValue(null, "value");
                String type = xml.getName();
                Object parsed = switch (type) {
                    case "string" -> xml.nextText();
                    case "set" -> {
                        Set<String> items = new HashSet<>();
                        while (xml.nextTag() == XmlPullParser.START_TAG) {
                            xml.require(XmlPullParser.START_TAG, null, "string");
                            items.add(xml.nextText());
                        }
                        yield items;
                    }
                    case "int", "long", "float", "boolean" -> {
                        if (value == null || !xml.nextText().isEmpty()) {
                            throw new IOException("Invalid settings in the backup.");
                        }
                        yield parsePreferenceValue(type, value);
                    }
                    default -> throw new IOException("Invalid settings in the backup.");
                };
                if (MatureContentLock.KEY.equals(name) && !(parsed instanceof Boolean)) {
                    throw new IOException("Invalid mature content lock in the backup.");
                }
                values.put(name, parsed);
            }
            if (xml.next() != XmlPullParser.END_DOCUMENT) {
                throw new IOException("Invalid settings in the backup.");
            }
        } catch (XmlPullParserException | NumberFormatException exception) {
            throw new IOException("Invalid settings in the backup.", exception);
        }
        return values;
    }

    private static Object parsePreferenceValue(String type, String value) throws IOException {
        return switch (type) {
            case "int" -> Integer.parseInt(value);
            case "long" -> Long.parseLong(value);
            case "float" -> Float.parseFloat(value);
            case "boolean" -> {
                if (!"true".equals(value) && !"false".equals(value)) {
                    throw new IOException("Invalid Boolean setting in the backup.");
                }
                yield Boolean.parseBoolean(value);
            }
            default -> throw new IOException("Unsupported preference type in the backup.");
        };
    }

    private static long copy(InputStream input, OutputStream output, long limit, String overflow) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        while (total < limit) {
            int read = input.read(buffer, 0, (int) Math.min(limit - total, buffer.length));
            if (read < 0) return total;
            output.write(buffer, 0, read);
            total += read;
        }
        if (input.read() != -1) throw new IOException(overflow);
        return total;
    }
}
