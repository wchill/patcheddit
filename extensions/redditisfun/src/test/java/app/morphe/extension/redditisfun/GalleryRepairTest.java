/*
 * Gallery repair by MojiRS, from https://github.com/MojiRS/morphe-rif-patches v1.9.0.
 * Adapted for this bundle. Licensed under GPL-3.0; see the repository LICENSE.
 */

package app.morphe.extension.redditisfun;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * JVM tests for {@link GalleryRepair}'s JSON logic with a fake probe. They need a real org.json,
 * which the Android stubs on the unit-test classpath are not (they return defaults): the
 * build adds org.json:json as a test dependency, so they run, and fail if it is missing.
 */
public class GalleryRepairTest {
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    @Before
    public void reset() {
        GalleryRepair.forgetLastHit();
    }

    private static String gallery(String meta, String items) {
        return "{\"data\":{\"children\":[{\"data\":{\"media_metadata\":{" + meta
                + "},\"gallery_data\":{\"items\":[" + items + "]}}}]}}";
    }

    private static final String OK = "{\"status\":\"valid\",\"e\":\"Image\",\"s\":{\"u\":\"x\",\"x\":1,\"y\":1}}";
    private static final String FAILED = "{\"status\":\"failed\"}";

    private static JSONObject run(String json, GalleryRepair.Probe probe) throws Exception {
        byte[] out = GalleryRepair.repair(json.getBytes(UTF_8), probe);
        return new JSONObject(new String(out, UTF_8));
    }

    private static JSONObject meta(JSONObject root) throws Exception {
        return root.getJSONObject("data").getJSONArray("children").getJSONObject(0)
                .getJSONObject("data").getJSONObject("media_metadata");
    }

    private static int itemCount(JSONObject root) throws Exception {
        return root.getJSONObject("data").getJSONArray("children").getJSONObject(0)
                .getJSONObject("data").getJSONObject("gallery_data").getJSONArray("items").length();
    }

    @Test
    public void normalGalleryIsReturnedUntouchedWithoutProbing() {
        byte[] in = gallery("\"a\":" + OK, "{\"media_id\":\"a\"}").getBytes(UTF_8);
        byte[] out = GalleryRepair.repair(in, url -> {
            throw new AssertionError("no network call expected");
        });
        assertArrayEquals(in, out);
    }

    @Test
    public void failedImagesAreFoundWithTheFirstExtensionThatHits() throws Exception {
        List<String> asked = new ArrayList<>();
        JSONObject root = run(gallery("\"abc1\":" + FAILED + ",\"abc2\":" + FAILED,
                "{\"media_id\":\"abc1\"},{\"media_id\":\"abc2\"}"), url -> {
            asked.add(url);
            return url.endsWith("abc1.png") || url.endsWith("abc2.png");
        });
        JSONObject e = meta(root).getJSONObject("abc1");
        assertEquals("valid", e.getString("status"));
        assertEquals("Image", e.getString("e"));
        assertEquals("image/png", e.getString("m"));
        assertEquals("https://i.redd.it/abc1.png", e.getJSONObject("s").getString("u"));
        assertEquals(0, e.getJSONObject("s").getInt("x"));
        assertEquals(2, itemCount(root));
        // abc1: jpeg, png (hit); abc2 starts with the last hit, png.
        assertEquals("https://i.redd.it/abc1.jpeg", asked.get(0));
        assertEquals("https://i.redd.it/abc1.png", asked.get(1));
        assertEquals("https://i.redd.it/abc2.png", asked.get(2));
        assertEquals(3, asked.size());
    }

    @Test
    public void gifsAreAnimatedAndJpgIsJpeg() throws Exception {
        JSONObject root = run(gallery("\"g1\":" + FAILED, "{\"media_id\":\"g1\"}"),
                url -> url.endsWith(".gif"));
        JSONObject e = meta(root).getJSONObject("g1");
        assertEquals("AnimatedImage", e.getString("e"));
        assertEquals("image/gif", e.getString("m"));
        assertEquals("https://i.redd.it/g1.gif", e.getJSONObject("s").getString("gif"));
        assertEquals("image/jpeg", GalleryRepair.entry("https://i.redd.it/x.jpg", "jpg").getString("m"));
    }

    @Test
    public void missesAreRemovedAndTheRestKept() throws Exception {
        JSONObject root = run(gallery("\"a\":" + OK + ",\"b\":" + FAILED,
                "{\"media_id\":\"a\"},{\"media_id\":\"b\"}"), url -> false);
        assertTrue(meta(root).has("a"));
        assertFalse(meta(root).has("b"));
        assertEquals(1, itemCount(root));
    }

    @Test
    public void probingStopsAfterTwoMissedItems() throws Exception {
        int[] calls = {0};
        run(gallery("\"a\":" + OK + ",\"b\":" + FAILED + ",\"c\":" + FAILED + ",\"d\":" + FAILED,
                "{\"media_id\":\"a\"},{\"media_id\":\"b\"},{\"media_id\":\"c\"},{\"media_id\":\"d\"}"),
                url -> {
                    calls[0]++;
                    return false;
                });
        assertEquals(10, calls[0]); // 2 items x 5 extensions; d is never probed
    }

    @Test
    public void nothingUsableKeepsTheOriginalBytes() {
        byte[] in = gallery("\"a\":" + FAILED, "{\"media_id\":\"a\"}").getBytes(UTF_8);
        assertArrayEquals(in, GalleryRepair.repair(in, url -> false));
    }

    @Test
    public void unreferencedBrokenEntryIsRemoved() throws Exception {
        JSONObject root = run(gallery("\"a\":" + OK + ",\"z\":" + FAILED + ",\"b\":" + FAILED,
                "{\"media_id\":\"a\"},{\"media_id\":\"b\"}"), url -> url.endsWith("b.jpeg"));
        assertFalse(meta(root).has("z"));
        assertTrue(meta(root).has("b"));
        assertEquals(2, itemCount(root));
    }

    @Test
    public void mediaIdWithOddCharactersIsNotProbed() throws Exception {
        List<String> asked = new ArrayList<>();
        byte[] in = gallery("\"a/../b\":" + FAILED, "{\"media_id\":\"a/../b\"}").getBytes(UTF_8);
        GalleryRepair.repair(in, url -> {
            asked.add(url);
            return true;
        });
        assertTrue(asked.isEmpty());
    }

    @Test
    public void arrayRootIsRepairedToo() throws Exception {
        String obj = gallery("\"abc1\":" + FAILED, "{\"media_id\":\"abc1\"}");
        byte[] out = GalleryRepair.repair(("[" + obj + "]").getBytes(UTF_8), url -> url.endsWith(".png"));
        JSONObject root = new org.json.JSONArray(new String(out, UTF_8)).getJSONObject(0);
        assertEquals("https://i.redd.it/abc1.png",
                meta(root).getJSONObject("abc1").getJSONObject("s").getString("u"));
        assertEquals(1, itemCount(root));
    }

    @Test
    public void arrayRootWithoutRepairNeedIsReturnedAsIs() {
        byte[] in = ("[" + gallery("\"a\":" + OK, "{\"media_id\":\"a\"}") + "]").getBytes(UTF_8);
        assertArrayEquals(in, GalleryRepair.repair(in, url -> {
            throw new AssertionError("no network call expected");
        }));
    }

    @Test
    public void otherShapesAreReturnedAsIs() {
        for (String s : new String[]{"[]", "[1]", "\"x\"", "42", "{}"}) {
            byte[] in = s.getBytes(UTF_8);
            assertArrayEquals(in, GalleryRepair.repair(in, url -> true));
        }
    }

    @Test
    public void garbageIsReturnedAsIs() {
        byte[] in = "not json".getBytes(UTF_8);
        assertArrayEquals(in, GalleryRepair.repair(in, url -> true));
    }
}
