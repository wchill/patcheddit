/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.debug;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ExceptionLogStoreTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void persistsTimestampThreadContextAndCompleteCauseInformation() throws Exception {
        File directory = temporary.newFolder();
        ExceptionLogStore store = new ExceptionLogStore(directory);
        IOException cause = new IOException("fixture-cause");
        IllegalStateException error = new IllegalStateException("fixture-error", cause);
        error.addSuppressed(new IOException("fixture-suppressed"));
        store.append(error, "fixture-context");
        String log = new ExceptionLogStore(directory).read();
        assertTrue(log.matches("(?s)\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z\\R.*"));
        assertTrue(log.contains("Thread: " + Thread.currentThread().getName()));
        assertTrue(log.contains("Context: fixture-context"));
        assertTrue(log.contains("java.lang.IllegalStateException: fixture-error"));
        assertTrue(log.contains("Caused by: java.io.IOException: fixture-cause"));
        assertTrue(log.contains("Suppressed: java.io.IOException: fixture-suppressed"));
        assertTrue(log.contains("ExceptionLogStoreTest.java:"));
    }

    @Test
    public void redactsCommonCredentialLinesIncludingTruncatedValues() throws Exception {
        ExceptionLogStore store = new ExceptionLogStore(temporary.newFolder());
        String[] messages = {
                "{\"access_token\":\"fixture-secret\",\"refresh_token\":\"fixture-refresh\"}",
                "refreshToken='fixture-secret'",
                "?access_token=fixture-secret&other=value",
                "Authorization: Bearer fixture-secret",
                "authorization=Basic fixture-secret",
                "Bearer fixture-secret",
                "client_id=fixture-secret",
                "clientSecret: fixture-secret",
                "password=\"fixture-secret with spaces\"",
                "api-key=fixture-secret",
                "morphe_boost_imgbb_api_key=fixture-secret",
                "{\\\"refresh_token\\\":\\\"fixture-secret\\\"}",
                "refresh_token=\"" + repeat("fixture-secret", 3000),
        };
        for (String message : messages) {
            store.clear();
            store.append(new IOException(message), "fixture-context");
            String log = store.read();
            assertFalse(log.contains("fixture-secret"));
            assertFalse(log.contains("fixture-refresh"));
            assertTrue(log.contains("[redacted]"));
            assertTrue(log.contains("Context: fixture-context"));
        }
    }

    @Test
    public void boundsOversizedUtf8EntriesAndMarksTruncation() throws Exception {
        File directory = temporary.newFolder();
        ExceptionLogStore store = new ExceptionLogStore(directory);
        store.append(new IOException(repeat("\u20ac", 30000)), "fixture-context");
        String log = store.read();
        assertTrue(log.contains("[Exception entry truncated]"));
        assertTrue(log.contains("\u20ac"));
        assertTrue(new File(directory, "exceptions.log").length() <= ExceptionLogStore.MAX_FILE_BYTES);
        assertTrue(log.length() <= ExceptionLogStore.MAX_ENTRY_CHARACTERS + 100);
    }

    @Test
    public void rotatesWithinExactDiskLimitAndRetainsNewestEntriesInOrder() throws Exception {
        File directory = temporary.newFolder();
        ExceptionLogStore store = new ExceptionLogStore(directory);
        String detail = repeat("x", 12000);
        int entries = 2 * ExceptionLogStore.MAX_FILE_BYTES / detail.length() + 3;
        for (int i = 0; i < entries; i++) {
            store.append(new IOException("entry-" + i + " " + detail), "fixture-context");
            long total = 0;
            for (File file : directory.listFiles()) {
                assertTrue(file.length() <= ExceptionLogStore.MAX_FILE_BYTES);
                total += file.length();
            }
            assertTrue(total <= 2L * ExceptionLogStore.MAX_FILE_BYTES);
        }
        String log = store.read();
        assertTrue(new File(directory, "exceptions.previous.log").isFile());
        assertFalse(log.contains("entry-0 "));
        String secondLast = "entry-" + (entries - 2) + " ";
        String last = "entry-" + (entries - 1) + " ";
        assertTrue(log.contains(secondLast));
        assertTrue(log.indexOf(secondLast) < log.indexOf(last));
    }

    @Test
    public void rotatesOnlyAfterTheExactFourMiBBoundary() throws Exception {
        assertEquals(4 * 1024 * 1024, ExceptionLogStore.MAX_FILE_BYTES);
        File directory = temporary.newFolder();
        File current = new File(directory, "exceptions.log");
        File previous = new File(directory, "exceptions.previous.log");
        ExceptionLogStore store = new ExceptionLogStore(directory);
        IOException error = new IOException("boundary-event");
        store.append(error, "fixture-context");
        long entryBytes = current.length();
        try (RandomAccessFile file = new RandomAccessFile(current, "rw")) {
            file.setLength(ExceptionLogStore.MAX_FILE_BYTES - entryBytes);
        }
        store.append(error, "fixture-context");
        assertEquals(ExceptionLogStore.MAX_FILE_BYTES, current.length());
        assertFalse(previous.exists());
        assertTrue(store.read().contains("boundary-event"));
        store.append(error, "fixture-context");
        assertEquals(ExceptionLogStore.MAX_FILE_BYTES, previous.length());
        assertEquals(entryBytes, current.length());
        assertTrue(previous.length() + current.length() <= 8L * 1024 * 1024);
    }

    @Test
    public void appendsConcurrentEventsWithoutInterleavingOrLosingEntries() throws Exception {
        ExceptionLogStore store = new ExceptionLogStore(temporary.newFolder());
        ExecutorService workers = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int i = 0; i < 80; i++) {
                final int id = i;
                tasks.add(() -> {
                    store.append(new IOException("unique-event-" + id + "!"), "fixture-context");
                    return null;
                });
            }
            for (Future<Void> result : workers.invokeAll(tasks)) result.get();
        } finally {
            workers.shutdownNow();
        }
        String log = store.read();
        for (int i = 0; i < 80; i++) {
            assertEquals(1, log.split("unique-event-" + i + "!", -1).length - 1);
        }
    }

    @Test
    public void clearRemovesBothFilesAndAllowsLoggingAgain() throws Exception {
        File directory = temporary.newFolder();
        ExceptionLogStore store = new ExceptionLogStore(directory);
        String detail = repeat("x", 12000);
        for (int i = 0; i < ExceptionLogStore.MAX_FILE_BYTES / detail.length() + 2; i++) {
            store.append(new IOException(detail), "fixture-context");
        }
        assertTrue(new File(directory, "exceptions.previous.log").exists());
        store.clear();
        assertEquals("", store.read());
        assertEquals(0, directory.listFiles().length);
        store.clear();
        store.append(new IOException("new-event"), "fixture-context");
        assertTrue(store.read().contains("new-event"));
    }

    @Test
    public void handlesEmptyLogsAndNullThrowables() throws Exception {
        ExceptionLogStore store = new ExceptionLogStore(new File(temporary.newFolder(), "new-directory"));
        assertEquals("", store.read());
        store.clear();
        store.append(null, "fixture-context");
        assertTrue(store.read().contains("without a throwable"));
    }

    @Test
    public void surfacesStorageFailuresAndRejectsUnexpectedlyOversizedFiles() throws Exception {
        File notDirectory = temporary.newFile();
        ExceptionLogStore unavailable = new ExceptionLogStore(notDirectory);
        assertThrows(IOException.class, () -> unavailable.append(new IOException("fixture"), "context"));
        assertThrows(IOException.class, unavailable::read);
        assertThrows(IOException.class, unavailable::clear);

        File directory = temporary.newFolder();
        try (RandomAccessFile file = new RandomAccessFile(new File(directory, "exceptions.log"), "rw")) {
            file.setLength(ExceptionLogStore.MAX_FILE_BYTES + 1L);
        }
        assertThrows(IOException.class, () -> new ExceptionLogStore(directory).read());
    }

    private static String repeat(String value, int count) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }
}
