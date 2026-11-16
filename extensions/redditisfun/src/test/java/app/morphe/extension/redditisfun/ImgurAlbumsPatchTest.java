/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.redditisfun;

import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ImgurAlbumsPatchTest {
    @Test
    public void normalizesTitledAlbumIdsOfBothLengths() throws Exception {
        assertEquals("XICYO", ImgurAlbumsPatch.normalizeId("album-title-here-XICYO"));
        assertEquals("Ab12Cd3", ImgurAlbumsPatch.normalizeId("album-title-here-Ab12Cd3"));
    }

    @Test
    public void preservesPlainAlbumIds() throws Exception {
        assertEquals("XICYO", ImgurAlbumsPatch.normalizeId("XICYO"));
        assertEquals("Ab12Cd3", ImgurAlbumsPatch.normalizeId("Ab12Cd3"));
    }

    @Test
    public void rejectsMalformedTitledIdentifiers() {
        for (String value : List.of("album-", "-XICYO", "album--XICYO", "album-ABCD",
                "album-ABCDEFGH", "album-XICYO/extra", "album-XICYO?foo=bar",
                "a".repeat(251) + "-XICYO")) {
            assertThrows(value, IOException.class, () -> ImgurAlbumsPatch.normalizeId(value));
        }
    }
}
