/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.archive.arcticshift;

import app.morphe.extension.vendored.com.fasterxml.jackson.databind.ObjectMapper;
import app.morphe.extension.vendored.com.fasterxml.jackson.databind.node.ObjectNode;
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

    @Test
    public void rendersRecoveredPostAndCommentWithTheSuppliedRenderer() {
        ObjectNode post = mapper.createObjectNode().put("selftext", "[removed]").put("selftext_html", "old");
        ArcticShift.updateSubmissionNode(post, mapper.createObjectNode().put("selftext", "**post**"),
                markdown -> "rendered post: " + markdown);
        assertEquals("**post**", post.path("selftext").asText());
        assertEquals("rendered post: **post**", post.path("selftext_html").asText());

        ObjectNode comment = mapper.createObjectNode().put("body", "[removed]").put("body_html", "old");
        ArcticShift.updateCommentNode(comment, mapper.createObjectNode().put("body", "*comment*"),
                markdown -> "rendered comment: " + markdown);
        assertEquals("*comment*", comment.path("body").asText());
        assertEquals("rendered comment: *comment*", comment.path("body_html").asText());
    }

    @Test
    public void keepsExistingHtmlWhenArchiveHasNoMarkdown() {
        ObjectNode comment = mapper.createObjectNode().put("body", "live").put("body_html", "<p>live</p>");
        ArcticShift.updateCommentNode(comment, mapper.createObjectNode().put("author", "restored"), markdown -> {
            throw new AssertionError("A missing archived body should not be rendered");
        });
        assertEquals("restored", comment.path("author").asText());
        assertEquals("live", comment.path("body").asText());
        assertEquals("<p>live</p>", comment.path("body_html").asText());
    }
}
