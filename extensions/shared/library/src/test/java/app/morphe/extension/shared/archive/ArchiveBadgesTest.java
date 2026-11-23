/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.archive;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ArchiveBadgesTest {
    @Test
    public void explicitReasonsTakePrecedenceOverBodyAndPostPlaceholders() {
        assertEquals(ArchiveBadges.RemovalReason.ADMIN,
                ArchiveBadges.removalReason("reddit", "legal", "[deleted]", null));
        assertEquals(ArchiveBadges.RemovalReason.SPAM,
                ArchiveBadges.removalReason("reddit", null, null, "[removed]"));
        assertEquals(ArchiveBadges.RemovalReason.ANTI_EVIL,
                ArchiveBadges.removalReason("anti_evil_ops", null, "[removed]", null));
        assertEquals(ArchiveBadges.RemovalReason.MODERATOR,
                ArchiveBadges.removalReason("moderator", null, "[deleted]", null));
    }

    @Test
    public void distinguishesPlaceholdersFromTextAndUnrecognizedCategories() {
        assertEquals(ArchiveBadges.RemovalReason.MODERATOR,
                ArchiveBadges.removalReason(null, null, "[removed]", null));
        assertEquals(ArchiveBadges.RemovalReason.USER_DELETION,
                ArchiveBadges.removalReason(null, null, null, "[deleted]"));
        assertFalse(ArchiveBadges.isContentRemoved("deleted", null, "A post about [deleted]", null));
    }

    @Test
    public void copyrightAloneDoesNotTriggerAutomaticRecovery() {
        assertEquals(ArchiveBadges.RemovalReason.COPYRIGHT,
                ArchiveBadges.removalReason("copyright_takedown", null, null, "[removed]"));
        assertFalse(ArchiveBadges.isContentRemoved("copyright_takedown", null, null, "live"));
        assertTrue(ArchiveBadges.isContentRemoved("copyright_takedown", null, null, "[removed]"));
    }
}
