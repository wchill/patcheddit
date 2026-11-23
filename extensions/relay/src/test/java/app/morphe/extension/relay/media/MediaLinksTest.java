package app.morphe.extension.relay.media;

import org.junit.Test;

import static org.junit.Assert.*;

public class MediaLinksTest {
    @Test public void inlineMediaUsesPublicImagesNotVideosOrPages() {
        String signed = "https://preview.redd.it/image.jpeg?format=pjpg&s=a%2Fb";
        assertEquals(signed, MediaLinks.inlineImage(signed));
        assertEquals("https://media.giphy.com/media/AbC123/giphy.gif",
                MediaLinks.inlineImage("https://giphy.com/gifs/some-title-AbC123"));
        assertEquals("https://i.imgur.com/image.gif", MediaLinks.inlineImage("https://i.imgur.com/image.gif"));
        for (String url : new String[]{"https://preview.redd.it/image.gif?format=mp4",
                "https://v.redd.it/abc", "https://i.imgur.com/a/abc", "https://localhost/image.png",
                "https://media.giphy.com.attacker.test/image.gif", "https://user@i.imgur.com/image.png"}) {
            assertNull(url, MediaLinks.inlineImage(url));
        }
    }
    @Test public void imageCdnFamiliesAndFormats() {
        for (String host : new String[]{"i.redd.it", "preview.redd.it", "external-preview.redd.it",
                "a.thumbs.redditmedia.com", "b.thumbs.redditmedia.com", "styles.redditmedia.com",
                "emoji.redditmedia.com", "i.redditmedia.com", "www.redditstatic.com"}) {
            for (String extension : new String[]{"jpg", "JPEG", "png", "avif", "bmp"}) {
                assertEquals(host + "/" + extension, MediaLinks.IMAGE,
                        MediaLinks.kind("https://" + host + "/image." + extension + "?s=a%2Fb%3D&width=640"));
            }
            assertEquals(MediaLinks.ANIMATED, MediaLinks.kind("https://" + host + "/image.webp"));
            assertEquals(MediaLinks.ANIMATED, MediaLinks.kind("https://" + host + "/image.gif?auto=webp"));
        }
        assertEquals(MediaLinks.IMAGE, MediaLinks.kind("https://emoji.redditmedia.com/abc/xyz"));
        assertEquals(MediaLinks.IMAGE, MediaLinks.kind("https://PREVIEW.REDD.IT/image%2Ejpeg?s=a"));
    }

    @Test public void inlineGiphyCdnKeepsTheOriginalUrl() {
        String cdn = "https://media2.giphy.com/media/AbC123/giphy.gif?cid=a%2Fb&rid=giphy.gif";
        assertEquals(cdn, MediaLinks.inlineImage(cdn));
        assertNull(MediaLinks.inlineImage("https://media2.giphy.com.example.org/media/AbC123/giphy.gif"));
        assertNull(MediaLinks.inlineImage("https://media2.giphy.com/media/AbC123/giphy.mp4"));
    }

    @Test public void requestedFormatOverridesFilename() {
        assertEquals(MediaLinks.IMAGE, MediaLinks.kind("https://preview.redd.it/image.gif?format=pjpg&s=x"));
        assertEquals(MediaLinks.ANIMATED, MediaLinks.kind("https://preview.redd.it/image.gif?format=webp&s=x"));
        assertEquals(MediaLinks.VIDEO, MediaLinks.kind("https://preview.redd.it/image.gif?format=mp4&s=x"));
        assertEquals(MediaLinks.IMAGE, MediaLinks.kind("https://external-preview.redd.it/abc?format=png"));
        assertFalse(MediaLinks.isHls("https://preview.redd.it/clip.m3u8?format=mp4"));
        assertTrue(MediaLinks.isHls("https://preview.redd.it/clip?format=m3u8"));
        assertEquals(MediaLinks.NONE, MediaLinks.kind("https://preview.redd.it/image.jpeg?format=html"));
        assertEquals(MediaLinks.NONE, MediaLinks.kind("https://preview.redd.it/image?format=%zz"));
    }

    @Test public void videoCdnFamiliesAndManifests() {
        for (String host : new String[]{"v.redd.it", "vcf.redd.it", "packaged-media.redd.it"}) {
            for (String extension : new String[]{"mp4", "webm", "m3u8", "m4a"}) {
                assertEquals(MediaLinks.VIDEO,
                        MediaLinks.kind("https://" + host + "/abc/pb/video." + extension + "?s=x"));
            }
            assertEquals(MediaLinks.DASH, MediaLinks.kind("https://" + host + "/abc/DASHPlaylist.mpd?s=x"));
            assertTrue(MediaLinks.isHls("https://" + host + "/abc/HLSPlaylist.m3u8?s=x"));
        }
        String signed = "https://vcf.redd.it/link/post/asset/video/?s=a%2Fb%3D#fragment";
        assertEquals(MediaLinks.DASH, MediaLinks.kind(signed));
        assertEquals("https://vcf.redd.it/link/post/asset/video/DASHPlaylist.mpd?s=a%2Fb%3D#fragment",
                MediaLinks.manifestUrl(signed));
        String direct = "https://packaged-media.redd.it/abc/pb/manifest.mpd?m=DASH&s=a%2Fb";
        assertEquals(direct, MediaLinks.manifestUrl(direct));
        assertEquals("https://v.redd.it/abc/DASHPlaylist.mpd#fragment?not-a-query",
                MediaLinks.manifestUrl("https://v.redd.it/abc#fragment?not-a-query"));
    }

    @Test public void leavesPagesAndUnrelatedResourcesAlone() {
        for (String url : new String[]{null, "", "https://redd.it/abc", "https://www.reddit.com/media?url=x",
                "https://preview.redd.it.attacker.test/image.jpeg", "https://attacker.test/preview.redd.it/image.jpeg",
                "https://user@preview.redd.it/image.jpeg", "https://preview.redd.it:8443/image.jpeg",
                "file://preview.redd.it/image.jpeg", "https://redditstatic.com/site.js",
                "https://redditstatic.com/style.css", "https://styles.redditmedia.com/data.json",
                "https://redditstatic.com/unknown", "https://i.redd.it/",
                "https://v.redd.it/abc/unknown", "https://packaged-media.redd.it/abc"}) {
            assertEquals(String.valueOf(url), MediaLinks.NONE, MediaLinks.kind(url));
        }
    }
}
