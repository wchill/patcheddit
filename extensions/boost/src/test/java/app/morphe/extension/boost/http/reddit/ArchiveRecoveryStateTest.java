/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.boost.http.reddit;

import app.morphe.extension.vendored.com.fasterxml.jackson.databind.ObjectMapper;
import app.morphe.extension.vendored.com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;

import app.morphe.extension.shared.archive.ArchiveBadges;

import static app.morphe.extension.boost.http.reddit.ArchiveRecoveryState.EXTRA_EMOJI_CONTEXT_KEY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ArchiveRecoveryStateTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void failureOnlyAddsWarning() throws Exception {
        ObjectNode data = (ObjectNode) mapper.readTree(
                "{\"id\":\"abc\",\"body\":\"[removed]\",\"likes\":true}");
        ObjectNode expected = data.deepCopy().put(EXTRA_EMOJI_CONTEXT_KEY, ArchiveBadges.WARNING);
        ArchiveRecoveryState.failed(data);
        ArchiveRecoveryState.failed(data);

        assertEquals(expected, data);
    }

    @Test
    public void loadedStateSelectsRefreshInsteadOfReload() throws Exception {
        ObjectNode data = mapper.createObjectNode().put("id", "abc").put("selftext", "text");
        assertFalse(ArchiveRecoveryState.isLoaded(mapper.readTree(ArchiveRecoveryState.snapshot(data.fields()))));
        data.put(ArchiveRecoveryState.LOADED_KEY, true);
        assertTrue(ArchiveRecoveryState.isLoaded(mapper.readTree(ArchiveRecoveryState.snapshot(data.fields()))));
    }

    @Test
    public void loadedContentShowsClockOnlyWithoutAnotherIcon() {
        ObjectNode live = mapper.createObjectNode().put("body", "text");
        ArchiveRecoveryState.loaded(live);
        assertTrue(live.path(ArchiveRecoveryState.LOADED_KEY).asBoolean());
        assertEquals(ArchiveBadges.ARCHIVED, live.path(EXTRA_EMOJI_CONTEXT_KEY).asText());

        String moderator = ArchiveBadges.RemovalReason.MODERATOR.emoji;
        ObjectNode removed = mapper.createObjectNode().put(EXTRA_EMOJI_CONTEXT_KEY, moderator);
        ArchiveRecoveryState.loaded(removed);
        assertEquals(moderator, removed.path(EXTRA_EMOJI_CONTEXT_KEY).asText());
    }

    @Test
    public void onlyRemovedContentNeedsFullRecovery() {
        assertTrue(ArchiveRecoveryState.isBodyOnly(mapper.createObjectNode().put("body", "live")));
        assertTrue(ArchiveRecoveryState.isBodyOnly(mapper.createObjectNode().put("selftext", "live")));
        assertFalse(ArchiveRecoveryState.isBodyOnly(mapper.createObjectNode().put("body", "[removed]")));
        assertFalse(ArchiveRecoveryState.isBodyOnly(mapper.createObjectNode().put("selftext", "[deleted]")));
        assertFalse(ArchiveRecoveryState.isBodyOnly(
                mapper.createObjectNode().put("selftext", "").put("removed_by_category", "deleted")));
    }

    @Test
    public void commentSnapshotsPreserveDataButExcludeRepliesAndNestedSnapshots() throws Exception {
        ObjectNode data = (ObjectNode) mapper.readTree(
                "{\"id\":\"abc\",\"body\":\"quotes: \\\"\\\\\\n\","
                        + "\"likes\":true,\"score\":42,\"author_flair_text\":null,"
                        + "\"replies\":{\"data\":{\"children\":[{\"kind\":\"more\"}]}}}");
        data.put(ArchiveRecoveryState.RETRY_KEY, "old snapshot");

        ObjectNode snapshot = (ObjectNode) mapper.readTree(ArchiveRecoveryState.snapshot(data.fields()));

        data.remove("replies");
        data.remove(ArchiveRecoveryState.RETRY_KEY);
        assertEquals(data, snapshot);
    }
}
