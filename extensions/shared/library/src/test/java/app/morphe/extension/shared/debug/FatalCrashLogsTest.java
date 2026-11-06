/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.debug;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class FatalCrashLogsTest {
    @Test
    public void recognizesOnlyThisAppsSdkReportNames() {
        String packageName = "com.example.boost";
        assertTrue(FatalCrashLogs.isCrashLogName("crash_com.example.boost_2026-10-26_12-00-01.txt", packageName));
        assertTrue(FatalCrashLogs.isCrashLogName("crash_com.example.boost_2026-10-26_12-00-01 (1).txt", packageName));
        assertTrue(FatalCrashLogs.isCrashLogName("crash_com.example_boost.app_2026-10-26_12-00-01.txt",
                "com.example_boost.app"));
        for (String name : new String[]{
                null, "", "exceptions.log", "crash_com.other.boost_2026-10-26_12-00-01.txt",
                "crash_comXexampleXboost_2026-10-26_12-00-01.txt",
                "crash_com.example.boost.extra_2026-10-26_12-00-01.txt",
                "crash_com.example.boost_2026-10-26_12-00-01.txt/another",
                "crash_com.example.boost_not-a-date.txt",
                "../crash_com.example.boost_2026-10-26_12-00-01.txt",
                "crash_com.example.boost_2026-10-26_12-00-01.png",
        }) assertFalse(FatalCrashLogs.isCrashLogName(name, packageName));
    }

    @Test
    public void keepsCompleteShortAndExactBoundaryPreviews() throws Exception {
        FatalCrashLogs.Preview empty = FatalCrashLogs.readTail(new StringReader(""), 16);
        assertEquals("", empty.text);
        assertFalse(empty.truncated);
        FatalCrashLogs.Preview exact = FatalCrashLogs.readTail(new StringReader("0123456789abcdef"), 16);
        assertEquals("0123456789abcdef", exact.text);
        assertFalse(exact.truncated);
    }

    @Test
    public void boundsLargePreviewsAndPreservesTheEndOfTheReport() throws Exception {
        StringBuilder input = new StringBuilder();
        for (int i = 0; i < 200000; i++) input.append("frame-\u20ac\n");
        input.append("final crash detail");
        int limit = 64 * 1024;
        FatalCrashLogs.Preview preview = FatalCrashLogs.readTail(new StringReader(input.toString()), limit);
        assertTrue(preview.truncated);
        assertEquals(limit, preview.text.length());
        assertEquals(input.substring(input.length() - limit), preview.text);
    }

    @Test
    public void copiesTheEntireReportRatherThanThePreview() throws Exception {
        byte[] report = new byte[9 * 1024 * 1024];
        for (int i = 0; i < report.length; i++) report[i] = (byte) (i % 251);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        FatalCrashLogs.copy(new ByteArrayInputStream(report), output);
        assertArrayEquals(report, output.toByteArray());
    }

    @Test
    public void surfacesReadFailuresAndInvalidLimits() {
        assertThrows(IOException.class, () -> FatalCrashLogs.readTail(new StringReader("x"), 0));
        Reader unavailable = new Reader() {
            @Override public int read(char[] buffer, int offset, int count) throws IOException {
                throw new IOException("Fixture read failure");
            }
            @Override public void close() {}
        };
        assertThrows(IOException.class, () -> FatalCrashLogs.readTail(unavailable, 16));
    }

    @Test
    public void cancelsBothPreviewAndExportWork() {
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedIOException.class,
                    () -> FatalCrashLogs.readTail(new StringReader("fixture"), 16));
            assertThrows(InterruptedIOException.class,
                    () -> FatalCrashLogs.copy(
                            new ByteArrayInputStream("fixture".getBytes(StandardCharsets.UTF_8)),
                            new ByteArrayOutputStream()));
        } finally {
            Thread.interrupted();
        }
    }
}
