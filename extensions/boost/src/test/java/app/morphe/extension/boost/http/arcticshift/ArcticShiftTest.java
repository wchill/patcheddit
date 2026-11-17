/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.boost.http.arcticshift;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ArcticShiftTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void emptySuccessfulLookupIsAnArchiveMiss() throws Exception {
        assertEquals(0, ArcticShift.idsData(mapper.readTree("{\"data\":[]}")).size());
        assertEquals(1, ArcticShift.idsData(mapper.readTree("{\"data\":[{\"id\":\"abc\"}]}")).size());
    }

    @Test
    public void errorsAndMalformedResponsesAreNotArchiveMisses() throws Exception {
        for (String json : new String[] {
                "null", "{}", "{\"data\":null}", "{\"data\":{}}",
                "{\"error\":\"Timeout\"}", "{\"data\":[],\"error\":\"Timeout\"}"
        }) {
            assertThrows(json, IOException.class, () -> ArcticShift.idsData(mapper.readTree(json)));
        }
        assertThrows(IOException.class, () -> ArcticShift.idsData(null));
    }
}
