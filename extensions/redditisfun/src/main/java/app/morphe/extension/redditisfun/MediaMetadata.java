/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.redditisfun;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import okio.AsyncTimeout;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;

final class MediaMetadata {
    private MediaMetadata() {}

    static void checkJsonDepth(String text, int limit, String description) throws IOException {
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (character == '\\') escaped = true;
                else if (character == '"') quoted = false;
            } else if (character == '"') {
                quoted = true;
            } else if (character == '{' || character == '[') {
                if (++depth > limit) throw new IOException(description + " metadata nesting exceeds the limit");
            } else if (character == '}' || character == ']') {
                depth--;
            }
        }
    }

    static final class Load {
        private final Call call;
        private final Semaphore slots;
        private final AtomicBoolean finished = new AtomicBoolean();
        private final AsyncTimeout timeout;
        private final long maxBytes;
        private final String description;
        private volatile boolean expired;

        Load(Call call, Semaphore slots, int timeoutMillis, long maxBytes, String description) throws IOException {
            if (!slots.tryAcquire()) throw new IOException("Too many " + description + " loads; try again");
            this.call = call;
            this.slots = slots;
            this.maxBytes = maxBytes;
            this.description = description;
            timeout = new AsyncTimeout() {
                @Override
                protected void timedOut() {
                    expired = true;
                    Load.this.call.cancel();
                    finish();
                }
            };
            timeout.timeout(timeoutMillis, TimeUnit.MILLISECONDS);
            timeout.enter();
        }

        void finish() {
            if (finished.compareAndSet(false, true)) {
                timeout.exit();
                slots.release();
            }
        }

        IOException failure(IOException cause) {
            if (!expired) return cause;
            InterruptedIOException failure = new InterruptedIOException(description + " load timed out");
            failure.initCause(cause);
            return failure;
        }

        ResponseBody bound(ResponseBody body) {
            BufferedSource source = Okio.buffer(new ForwardingSource(body.source()) {
                private long bytesRead;

                @Override
                public long read(@NonNull Buffer sink, long byteCount) throws IOException {
                    try {
                        if (expired || call.isCanceled()) throw new IOException(description + " load was canceled");
                        long count = super.read(sink, Math.min(byteCount, maxBytes + 1 - bytesRead));
                        if (expired) throw new IOException(description + " load timed out");
                        if (count == -1) finish();
                        else if ((bytesRead += count) > maxBytes) {
                            call.cancel();
                            throw new IOException(description + " metadata exceeds the size limit");
                        }
                        return count;
                    } catch (IOException exception) {
                        call.cancel();
                        finish();
                        throw failure(exception);
                    }
                }

                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        finish();
                    }
                }
            });
            return new ResponseBody() {
                @Override public MediaType contentType() { return body.contentType(); }
                @Override public long contentLength() { return body.contentLength(); }
                @NonNull @Override public BufferedSource source() { return source; }
            };
        }
    }
}
