/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.fixes.redgifs;

import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.*;

public class RedgifsPlaybackTest {
    private static final String API = "https://api.redgifs.com/v2/gifs/fixture";
    private static final String HD = "https://thumbs4.redgifs.com/Fixture.mp4?token=hd%2B1";
    private static final String SD = "https://media.redgifs.com/Fixture-mobile.mp4?token=sd%2B1";
    private static final String SILENT = "https://thumbs4.redgifs.com/Fixture-silent.mp4?token=preview";

    @Test
    public void resolvesOnlyRecognizedSourcesToNativeWatchLinks() {
        String watch = "https://www.redgifs.com/watch/fixture";
        assertEquals(watch, RedgifsPlayback.preferOriginal("https://v.redd.it/preview.gif",
                "https://redgifs.com/ifr/Fixture/?autoplay=1#fragment"));
        assertEquals(watch, RedgifsPlayback.normalizeSourceUrl(SILENT));
        for (String url : List.of(HD, SD, "https://thumbs.redgifs.com.example.org/Fixture-silent.mp4",
                "https://thumbs.redgifs.com:8443/Fixture-silent.mp4",
                "https://thumbs.redgifs.com/other/Fixture-silent.mp4",
                "https://example.org/redgifs.com/watch/Fixture")) {
            assertEquals(url, RedgifsPlayback.normalizeSourceUrl(url));
            assertNull(RedgifsPlayback.clipId(url));
        }
    }

    @Test
    public void rejectsStalePreviewCacheForRedgifsOnly() {
        String watch = "https://www.redgifs.com/watch/fixture";
        String preview = "https://v.redd.it/fixture/HLSPlaylist.m3u8";
        assertTrue(RedgifsPlayback.shouldBypassCachedSource(watch, preview));
        assertTrue(RedgifsPlayback.shouldBypassCachedSource(watch, SILENT));
        assertFalse(RedgifsPlayback.shouldBypassCachedSource(watch, SD));
        assertFalse(RedgifsPlayback.shouldBypassCachedSource("https://www.reddit.com/r/test", preview));
    }

    @Test
    public void prefersUsableHdThenSdWithoutChangingSignedStreams() throws Exception {
        assertEquals(HD, selected(API, HD, SD));
        assertEquals(SD, selected(API, SILENT, SD));
        assertEquals(SD, selected(API, null, SD));
        assertEquals(SD, selected(API, "", SD));
        assertEquals(SILENT, selected(API, SILENT, null));
        assertEquals(SILENT, selected("https://example.org/v2/gifs/fixture", SILENT, SD));
        assertEquals(SILENT, selected(API + "/related", SILENT, SD));
    }

    private static String selected(String api, String hd, String sd) throws Exception {
        JSONObject urls = new JSONObject().put("hd", hd).put("sd", sd);
        String json = new JSONObject().put("gif", new JSONObject().put("urls", urls)).toString();
        try (Response response = RedgifsPlayback.preferAudioMetadata(new Response.Builder()
                .request(new Request.Builder().url(api).build())
                .protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(ResponseBody.create(json, MediaType.get("application/json")))
                .build())) {
            return new JSONObject(response.body().string()).getJSONObject("gif")
                    .getJSONObject("urls").optString("hd");
        }
    }
}
