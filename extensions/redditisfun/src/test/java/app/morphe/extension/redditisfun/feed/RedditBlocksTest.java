/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.redditisfun.feed;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.*;

public class RedditBlocksTest {
    private static final String HTML = "<html><title>Blocked</title><p>network security</p></html>";

    private static final class Request implements RedditBlocks.Request {
        @Override public boolean morpheIsFeed() { return true; }
    }

    private static final class Loader implements RedditBlocks.Loader {
        Object current;
        Loader(Object request) { current = request; }
        @Override public Object morpheRequest() { return current; }
    }

    @Test
    public void classifiesSignedInHtmlOnceAndPreservesEveryByte() throws Exception {
        for (String page : new String[]{HTML, "<HTML><TITLE> BLOCKED </TITLE></HTML>",
                "<p>You&apos;ve been blocked by network security.</p>", "<p>Whoa there, pardner!</p>"}) {
            Request request = new Request();
            byte[] original = page.getBytes(StandardCharsets.UTF_8);
            RedditBlocks.begin(request, true);
            assertArrayEquals(original, RedditBlocks.inspect(request, new ByteArrayInputStream(original)).readAllBytes());
            Loader loader = new Loader(request);
            assertTrue(RedditBlocks.take(loader));
            assertFalse(RedditBlocks.take(loader));
        }
    }

    @Test
    public void excludesLoggedOutStructuredErrorsAndUnrelatedHtml() throws Exception {
        Request guest = new Request();
        InputStream input = new ByteArrayInputStream(HTML.getBytes(StandardCharsets.UTF_8));
        RedditBlocks.begin(guest, false);
        assertSame(input, RedditBlocks.inspect(guest, input));
        assertFalse(RedditBlocks.take(new Loader(guest)));
        for (String body : new String[]{"", "{\"reason\":\"private\"}", "{\"reason\":\"quarantined\"}",
                "{\"reason\":\"banned\"}", "{\"reason\":\"gated\"}",
                "{\"message\":\"<title>Blocked</title>\"}", "\"<title>Blocked</title>\"",
                "<html><title>Forbidden</title><p>Private community</p></html>"}) {
            Request request = new Request();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            RedditBlocks.begin(request, true);
            assertArrayEquals(bytes, RedditBlocks.inspect(request, new ByteArrayInputStream(bytes)).readAllBytes());
            assertFalse(RedditBlocks.take(new Loader(request)));
        }
    }

    @Test
    public void findingsFollowRequestsAcrossLoadersRetriesAndOutOfOrderCallbacks() throws Exception {
        Request first = new Request(), second = new Request();
        Loader left = new Loader(first), right = new Loader(second);
        RedditBlocks.begin(first, true);
        RedditBlocks.inspect(first, new ByteArrayInputStream(HTML.getBytes(StandardCharsets.UTF_8)));
        RedditBlocks.begin(second, false);
        assertFalse(RedditBlocks.take(right));
        left.current = second;
        assertFalse(RedditBlocks.take(left));
        left.current = first;
        assertTrue(RedditBlocks.take(left));
        RedditBlocks.begin(first, true);
        RedditBlocks.inspect(first, new ByteArrayInputStream(HTML.getBytes(StandardCharsets.UTF_8)));
        RedditBlocks.begin(first, false);
        assertFalse(RedditBlocks.take(left));
    }

    @Test
    public void inspectionHasAnExactByteCeilingAndReplaysTheUnreadTail() throws Exception {
        byte[] bytes = new byte[RedditBlocks.MAX_BYTES + 321];
        Arrays.fill(bytes, (byte) 'x');
        byte[] header = HTML.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(header, 0, bytes, 0, header.length);
        ByteArrayInputStream input = new ByteArrayInputStream(bytes);
        Request request = new Request();
        RedditBlocks.begin(request, true);
        InputStream replay = RedditBlocks.inspect(request, input);
        assertEquals(321, input.available());
        assertArrayEquals(bytes, replay.readAllBytes());
        assertTrue(RedditBlocks.take(new Loader(request)));
    }

    @Test
    public void partialReadFailureIsReplayedAtTheOriginalBoundaryWithoutLosingTheTail() throws Exception {
        byte[] bytes = HTML.getBytes(StandardCharsets.UTF_8);
        IOException error = new IOException("fixture read failure");
        int boundary = 12;
        InputStream input = new ByteArrayInputStream(bytes) {
            boolean failed;
            @Override public synchronized int read(byte[] buffer, int offset, int length) {
                if (pos == boundary && !failed) {
                    failed = true;
                    throw new java.io.UncheckedIOException(error);
                }
                return super.read(buffer, offset, failed ? length : Math.min(length, boundary - pos));
            }
        };
        InputStream checked = new java.io.FilterInputStream(input) {
            @Override public int read(byte[] buffer, int offset, int length) throws IOException {
                try {
                    return in.read(buffer, offset, length);
                } catch (java.io.UncheckedIOException failure) {
                    throw failure.getCause();
                }
            }
        };
        Request request = new Request();
        RedditBlocks.begin(request, true);
        InputStream replay = RedditBlocks.inspect(request, checked);
        assertArrayEquals(Arrays.copyOf(bytes, boundary), replay.readNBytes(boundary));
        assertSame(error, assertThrows(IOException.class, replay::read));
        assertArrayEquals(Arrays.copyOfRange(bytes, boundary, bytes.length), replay.readAllBytes());
        assertFalse(RedditBlocks.take(new Loader(request)));
    }
}
