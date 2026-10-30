/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.morecomments

import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.fixMoreCommentsPatch

@Suppress("unused")
val fixMoreComments = fixMoreCommentsPatch(
    extensionPatch = ExtensionPatches.Sync,
    compatible = AppCompatibility.SyncForReddit,
)
