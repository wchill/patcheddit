/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.youtube;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class YouTubeLinkTest {
    private static final String ID = "bHQqvYy5KYo";

    @Test
    public void preservesStartTimeAcrossYouTubeLinkForms() {
        assertEquals(new YouTubeLink(ID, 3723), YouTubeLink.parse("https://youtu.be/" + ID + "?t=1h2m3s"));
        assertEquals(new YouTubeLink(ID, 61), YouTubeLink.parse("https://m.youtube.com/watch?v=" + ID + "#t=61"));
        assertEquals(new YouTubeLink(ID, 12), YouTubeLink.parse("https://www.youtube.com/embed/" + ID + "?start=12"));
        assertEquals(new YouTubeLink(ID, 0), YouTubeLink.parse("https://www.youtube.com/shorts/" + ID));
        assertEquals("https://www.youtube.com/watch?v=" + ID + "&t=12s", new YouTubeLink(ID, 12).watchUrl());
    }

    @Test
    public void leavesUnsupportedLinksOnTheirNativeRoute() {
        assertNull(YouTubeLink.parse("https://youtube.com.example/watch?v=" + ID));
        assertNull(YouTubeLink.parse("https://www.youtube.com/playlist?list=PL0123"));
        assertNull(YouTubeLink.parse("https://www.youtube.com/watch?v=invalid%27"));
        assertNull(YouTubeLink.parse("https://you%20tube.com/watch?v=" + ID));
    }

    @Test
    public void recognizesOnlyValidYouTubeShortsPaths() {
        assertTrue(YouTubeLink.isShortsLink("https://m.youtube.com/shorts/" + ID + "?si=example"));
        assertFalse(YouTubeLink.isShortsLink("https://www.youtube.com/shorts/"));
        assertFalse(YouTubeLink.isShortsLink("https://youtube.com.example/shorts/" + ID));
        assertFalse(YouTubeLink.isShortsLink("https://www.youtube.com/watch?v=" + ID + "&q=/shorts/"));
    }
}
