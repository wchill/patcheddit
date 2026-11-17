/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.fixes.media;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CommentVideoLinksTest {
    @Test
    public void derivesBothNativeManifestsWithoutDroppingTheCommentContainer() {
        for (String host : new String[]{"reddit.com", "www.reddit.com", "old.reddit.com", "np.reddit.com"}) {
            for (String suffix : new String[]{"/player", "/player/", "", "/", "/player?utm_source=share#video"}) {
                String link = "https://" + host + "/link/Abc123/video/Def456" + suffix;
                assertEquals("https://v.redd.it/link/Abc123/asset/Def456/DASHPlaylist.mpd",
                        CommentVideoLinks.dashUrl(link));
                assertEquals("https://v.redd.it/link/Abc123/asset/Def456/HLSPlaylist.m3u8",
                        CommentVideoLinks.hlsUrl(link));
            }
        }
        assertEquals("https://v.redd.it/link/abc/asset/def",
                CommentVideoLinks.baseUrl("HTTP://WWW.REDDIT.COM:80/link/abc/video/def/player"));
    }

    @Test
    public void rejectsUnrelatedHostsSchemesCredentialsPortsAndMalformedOrEncodedIdentifiers() {
        for (String link : new String[]{
                null, "", "https://v.redd.it/ordinary/DASHPlaylist.mpd",
                "https://www.reddit.com/r/test/comments/abc/post",
                "https://example.com/link/abc/video/def/player",
                "https://reddit.com.evil.example/link/abc/video/def/player",
                "https://notreddit.com/link/abc/video/def/player",
                "https://reddit.com@evil.example/link/abc/video/def/player",
                "https://evil@reddit.com/link/abc/video/def/player",
                "https://reddit.com:8443/link/abc/video/def/player",
                "ftp://reddit.com/link/abc/video/def/player",
                "//reddit.com/link/abc/video/def/player",
                "https://reddit.com/link//video/def/player",
                "https://reddit.com/link/abc/video//player",
                "https://reddit.com/link/../video/def/player",
                "https://reddit.com/link/%61bc/video/def/player",
                "https://reddit.com/link/abc/video/de-f/player",
                "https://reddit.com/link/abc/video/def/player/extra",
                "https://reddit.com/link/" + "a".repeat(65) + "/video/def/player",
                "https://reddit.com/link/abc/video/def/player?x=" + "a".repeat(4096)}) {
            assertNull(link, CommentVideoLinks.dashUrl(link));
            assertNull(link, CommentVideoLinks.hlsUrl(link));
        }
    }
}
