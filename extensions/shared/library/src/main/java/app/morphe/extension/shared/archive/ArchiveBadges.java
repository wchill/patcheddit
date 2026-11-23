/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.shared.archive;

public final class ArchiveBadges {
    public static final String UNAVAILABLE = "\uD83D\uDEAB";
    public static final String ARCHIVED = "\uD83D\uDD52";
    public static final String WARNING = "\u26A0\uFE0F";

    private static final String REMOVED = "[removed]";
    private static final String DELETED = "[deleted]";

    private ArchiveBadges() {}

    public enum RemovalReason {
        ADMIN("\uD83D\uDE93"),
        SPAM("\uD83E\uDD16"),
        ANTI_EVIL("\uD83D\uDC7F"),
        COPYRIGHT("\u00A9\uFE0F"),
        MODERATOR("\uD83E\uDDF9"),
        USER_DELETION("\uD83D\uDDD1"),
        NONE(null);

        public final String emoji;

        RemovalReason(String emoji) {
            this.emoji = emoji;
        }
    }

    public static RemovalReason removalReason(String category, String reason, String body, String selfText) {
        if ("legal".equals(reason) || "content_takedown".equals(category)) return RemovalReason.ADMIN;
        if ("reddit".equals(category)) return RemovalReason.SPAM;
        if ("anti_evil_ops".equals(category)) return RemovalReason.ANTI_EVIL;
        if ("copyright_takedown".equals(category)) return RemovalReason.COPYRIGHT;
        if ("moderator".equals(category) || hasPlaceholder(REMOVED, body, selfText)) return RemovalReason.MODERATOR;
        if (hasPlaceholder(DELETED, body, selfText)) return RemovalReason.USER_DELETION;
        return RemovalReason.NONE;
    }

    public static boolean isContentRemoved(String category, String reason, String body, String selfText) {
        RemovalReason removal = removalReason(category, reason, body, selfText);
        // The copyright category alone assigns a badge but doesn't trigger automatic recovery.
        if (removal == RemovalReason.COPYRIGHT) {
            return hasPlaceholder(REMOVED, body, selfText) || hasPlaceholder(DELETED, body, selfText);
        }
        return removal != RemovalReason.NONE;
    }

    private static boolean hasPlaceholder(String placeholder, String body, String selfText) {
        return placeholder.equals(body) || placeholder.equals(selfText);
    }
}
