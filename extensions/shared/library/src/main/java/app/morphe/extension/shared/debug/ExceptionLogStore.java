/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.debug;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ExceptionLogStore {
    public static final int MAX_FILE_BYTES = 4 * 1024 * 1024;
    static final int MAX_ENTRY_CHARACTERS = 16 * 1024;

    private static final Pattern CREDENTIAL_LINE = Pattern.compile(
            "(?i)(\\b(?:access[ _-]?token|refresh[ _-]?token|client[ _-]?(?:id|secret)"
                    + "|password|api[ _-]?key|morphe_boost_imgbb_api_key|authorization)"
                    + "[\\\\\"']*\\s*[:=]\\s*)[^\\r\\n]*"
    );
    private static final Pattern BEARER_LINE =
            Pattern.compile("(?i)(\\bbearer[ \\t]+)[^\\r\\n]*");
    private static final String CLASS_PREFIX = "Exception class: ";
    private static final String NO_THROWABLE = "[no throwable]";
    private static final Pattern REPORT_HEADER = Pattern.compile(
            "(?m)^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z)\\r?\\n"
                    + "(?:Entry length: ([0-9]{1,6})\\r?\\n"
                    + "|(?=Thread: [^\\r\\n]*\\r?\\nContext: [^\\r\\n]*(?:\\r?\\n|$)))"
    );
    private static final Pattern CLASS_NAME = Pattern.compile("[\\w$]+(?:\\.[\\w$]+)*");

    private final File directory;
    private final File current;
    private final File previous;

    public ExceptionLogStore(File directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
        current = new File(directory, "exceptions.log");
        previous = new File(directory, "exceptions.previous.log");
    }

    public synchronized void append(Throwable error, String context) throws IOException {
        LimitedWriter output = new LimitedWriter();
        PrintWriter writer = new PrintWriter(output);
        SimpleDateFormat timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT);
        timestamp.setTimeZone(TimeZone.getTimeZone("UTC"));
        writer.println(timestamp.format(new Date()));
        writer.println(CLASS_PREFIX + (error == null ? NO_THROWABLE : error.getClass().getName()));
        writer.println("Thread: " + Thread.currentThread().getName());
        writer.println("Context: " + context);
        if (error == null) writer.println("The app reported an error without a throwable.");
        else error.printStackTrace(writer);
        writer.flush();
        String text = CREDENTIAL_LINE.matcher(output.text).replaceAll("$1[redacted]");
        text = BEARER_LINE.matcher(text).replaceAll("$1[redacted]");
        boolean truncated = output.truncated || text.length() > MAX_ENTRY_CHARACTERS;
        if (text.length() > MAX_ENTRY_CHARACTERS) text = text.substring(0, MAX_ENTRY_CHARACTERS);
        if (truncated) text += "\n[Exception entry truncated]\n";
        int bodyStart = text.indexOf('\n') + 1;
        // Length framing prevents timestamp-like exception messages from becoming separate reports.
        String framed = text.substring(0, bodyStart) + "Entry length: " + (text.length() - bodyStart)
                + "\n" + text.substring(bodyStart) + "\n";
        byte[] entry = framed.getBytes(StandardCharsets.UTF_8);
        if (entry.length > MAX_FILE_BYTES) throw new IOException("Exception entry exceeds the log size limit.");

        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("Could not create the exception log directory.");
        }
        if (current.length() + entry.length > MAX_FILE_BYTES) {
            delete(previous);
            if (current.exists() && !current.renameTo(previous)) {
                throw new IOException("Could not rotate the exception log.");
            }
        }
        try (FileOutputStream stream = new FileOutputStream(current, true)) {
            stream.write(entry);
        }
    }

    public synchronized String read() throws IOException {
        requireDirectory();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        read(previous, output);
        read(current, output);
        return output.toString(StandardCharsets.UTF_8.name());
    }

    public List<Report> reports() throws IOException {
        String text = read();
        List<Report> reports = new ArrayList<>();
        Matcher header = REPORT_HEADER.matcher(text);
        int cursor = 0;
        while (header.find(cursor)) {
            requireWhitespace(text, cursor, header.start());
            String timestamp = header.group(1);
            String length = header.group(2);
            int bodyStart = header.end();
            int end;
            boolean incomplete = false;
            String exceptionClass;
            if (length != null) {
                int characters = Integer.parseInt(length);
                if (characters <= 0 || characters > MAX_ENTRY_CHARACTERS + 100) {
                    throw new IOException("Saved exception entry has an invalid length.");
                }
                int expectedEnd = bodyStart + characters;
                incomplete = expectedEnd > text.length();
                end = Math.min(expectedEnd, text.length());
                String firstLine = text.substring(bodyStart, lineEnd(text, bodyStart, end)).trim();
                if (!firstLine.startsWith(CLASS_PREFIX)) {
                    if (!incomplete) throw new IOException("Saved exception entry is missing its class.");
                    exceptionClass = "Exception type unavailable";
                } else {
                    String name = firstLine.substring(CLASS_PREFIX.length());
                    if (name.isEmpty() && !incomplete) {
                        throw new IOException("Saved exception entry has an empty class.");
                    }
                    exceptionClass = name.isEmpty() ? "Exception type unavailable"
                            : NO_THROWABLE.equals(name) ? "No throwable supplied" : name;
                }
            } else {
                end = header.find(bodyStart) ? header.start() : text.length();
                exceptionClass = legacyClass(text, bodyStart, end);
            }
            reports.add(new Report(timestamp, exceptionClass, text, bodyStart, end, incomplete));
            cursor = end;
        }
        requireWhitespace(text, cursor, text.length());
        Collections.reverse(reports);
        reports.sort((left, right) -> right.timestamp.compareTo(left.timestamp));
        return Collections.unmodifiableList(reports);
    }

    private static int lineEnd(String text, int start, int end) {
        int newline = text.indexOf('\n', start);
        return newline < 0 || newline > end ? end : newline;
    }

    private static String legacyClass(String text, int bodyStart, int end) {
        int threadEnd = lineEnd(text, bodyStart, end);
        int contextStart = Math.min(threadEnd + 1, end);
        int contextEnd = lineEnd(text, contextStart, end);
        int traceStart = Math.min(contextEnd + 1, end);
        String line = text.substring(traceStart, lineEnd(text, traceStart, end)).trim();
        if ("Boost reported an error without a throwable.".equals(line)
                || "The app reported an error without a throwable.".equals(line)) return "No throwable supplied";
        int colon = line.indexOf(':');
        String name = colon < 0 ? line : line.substring(0, colon);
        return CLASS_NAME.matcher(name).matches() ? name : "Exception type unavailable";
    }

    private static void requireWhitespace(String text, int start, int end) throws IOException {
        for (int i = start; i < end; i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                throw new IOException("Unrecognized saved exception log content. Export the full log to inspect it.");
            }
        }
    }

    public static final class Report {
        public final String timestamp;
        public final String exceptionClass;
        public final boolean incomplete;
        private final String snapshot;
        private final int start;
        private final int end;

        private Report(String timestamp, String exceptionClass, String snapshot, int start, int end,
                       boolean incomplete) {
            this.timestamp = timestamp;
            this.exceptionClass = exceptionClass;
            this.snapshot = snapshot;
            this.start = start;
            this.end = end;
            this.incomplete = incomplete;
        }

        public String details(int maxCharacters) {
            if (maxCharacters <= 0) throw new IllegalArgumentException("Invalid exception preview limit.");
            int limit = start + Math.min(end - start, maxCharacters);
            String text = timestamp + "\n" + snapshot.substring(start, limit);
            if (limit < end) text += "\n[Preview truncated. Export the full exception log for the complete entry.]";
            if (incomplete) text = "[This entry is incomplete in the saved log.]\n\n" + text;
            return text;
        }
    }

    private static void read(File file, ByteArrayOutputStream output) throws IOException {
        if (!file.exists()) return;
        int total = 0;
        try (FileInputStream stream = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                total += count;
                if (total > MAX_FILE_BYTES) throw new IOException("Saved exception log exceeds its size limit.");
                output.write(buffer, 0, count);
            }
        }
    }

    public synchronized void clear() throws IOException {
        requireDirectory();
        delete(previous);
        delete(current);
    }

    private static void delete(File file) throws IOException {
        if (file.exists() && !file.delete()) throw new IOException("Could not remove a saved exception log.");
    }

    private void requireDirectory() throws IOException {
        if (directory.exists() && !directory.isDirectory()) {
            throw new IOException("The exception log path is not a directory.");
        }
    }

    private static final class LimitedWriter extends Writer {
        final StringBuilder text = new StringBuilder();
        boolean truncated;

        @Override
        public void write(char[] buffer, int offset, int length) {
            int count = Math.min(length, MAX_ENTRY_CHARACTERS - text.length());
            text.append(buffer, offset, count);
            truncated |= count != length;
        }

        @Override
        public void write(String value, int offset, int length) {
            int count = Math.min(length, MAX_ENTRY_CHARACTERS - text.length());
            text.append(value, offset, offset + count);
            truncated |= count != length;
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
    }
}
