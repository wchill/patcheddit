/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.boost.http.reddit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ArchiveRetryMergeTest {
    @SuppressWarnings("unused")
    static class Contribution {
        String extraEmoji;
        String morpheArchiveRetry;
        String author = "[deleted]";
    }

    static final class Model extends Contribution {
        String body = "[removed]";
        String bodyHtml = "<p>[removed]</p>";
        boolean saved;
        long score = 10;
        double ratio = 0.5;
        boolean collapsed = true;
    }

    private static final String OWNER = "L" + Model.class.getName().replace('.', '/') + ";";
    private static final String FIELDS = String.join(" ",
            OWNER + "->body:Ljava/lang/String;",
            "L" + Contribution.class.getName().replace('.', '/') + ";->author:Ljava/lang/String;",
            OWNER + "->saved:Z", OWNER + "->score:J", OWNER + "->ratio:D", OWNER + "->collapsed:Z");

    private static Model failed() {
        Model model = new Model();
        model.extraEmoji = "warning";
        model.morpheArchiveRetry = "snapshot";
        return model;
    }

    @Test
    public void recoveredContentDoesNotResetInFlightUserOrTreeState() throws Exception {
        Model current = failed();
        current.saved = true;
        current.score = 11;
        current.collapsed = false;
        Model after = new Model();
        after.body = "Recovered content";
        after.author = "original_author";
        after.ratio = 0.75;
        after.extraEmoji = "removed";

        ArchiveRetryMerge.apply(current, new Model(), after, FIELDS);

        assertEquals("Recovered content", current.body);
        assertEquals("original_author", current.author);
        assertEquals(0.75, current.ratio, 0);
        assertEquals(true, current.saved);
        assertEquals(11, current.score);
        assertEquals(false, current.collapsed);
        assertEquals("removed", current.extraEmoji);
        assertNull(current.morpheArchiveRetry);
    }

    @Test
    public void confirmedArchiveMissClearsFailureStateWithoutInventingContent() throws Exception {
        Model current = failed();

        ArchiveRetryMerge.apply(current, new Model(), new Model(), FIELDS);

        assertEquals("[removed]", current.body);
        assertEquals("[deleted]", current.author);
        assertEquals(10, current.score);
        assertNull(current.extraEmoji);
        assertNull(current.morpheArchiveRetry);
    }

    @Test
    public void liveCommentReloadChangesOnlyBodyAndClearsRetryEligibility() throws Exception {
        Model current = new Model();
        current.body = "Live text";
        current.bodyHtml = "<p>Live text</p>";
        current.author = "live_author";
        current.saved = true;
        current.score = 123;
        current.collapsed = false;
        current.morpheArchiveRetry = "snapshot";
        Model archived = new Model();
        archived.body = "Archived text";
        archived.bodyHtml = "<p>Archived text</p>";
        archived.author = "archived_author";
        archived.score = 1;
        archived.ratio = 0.1;

        ArchiveRetryMerge.apply(current, new Model(), archived,
                OWNER + "->body:Ljava/lang/String; " + OWNER + "->bodyHtml:Ljava/lang/String;");

        assertEquals("Archived text", current.body);
        assertEquals("<p>Archived text</p>", current.bodyHtml);
        assertEquals("live_author", current.author);
        assertEquals(123, current.score);
        assertEquals(0.5, current.ratio, 0);
        assertEquals(true, current.saved);
        assertEquals(false, current.collapsed);
        assertNull(current.extraEmoji);
        assertNull(current.morpheArchiveRetry);
    }
}
