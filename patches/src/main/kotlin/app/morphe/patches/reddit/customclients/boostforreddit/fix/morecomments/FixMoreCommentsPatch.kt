/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.morecomments

import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.boostforreddit.http.interceptHttpRequests
import app.morphe.patches.reddit.customclients.fixMoreCommentsPatch

@Suppress("unused")
val fixMoreComments = fixMoreCommentsPatch(
    extensionPatch = interceptHttpRequests,
    compatible = AppCompatibility.Boost,
    // Fixed natively in Boost 1.14.2.
    default = false,
)
