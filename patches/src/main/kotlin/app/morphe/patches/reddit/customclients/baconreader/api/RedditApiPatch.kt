/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.baconreader.api

import app.morphe.patches.reddit.customclients.baconreader.fix.apilookups.fixRedditApiLookups
import app.morphe.patches.reddit.customclients.baconreader.fix.gallery.fixFailedGalleryImagesPatch
import app.morphe.patches.reddit.customclients.baconreader.fix.login.modifyLoginWebView
import app.morphe.patches.reddit.customclients.baconreader.fix.morecomments.fixMoreComments
import app.morphe.patches.reddit.customclients.baconreader.fix.slink.fixSLinksPatch
import app.morphe.patches.reddit.customclients.redditApiPatch
import app.morphe.patches.reddit.customclients.legacySpoofClientPatch

@Suppress("unused")
val redditApiPatch = redditApiPatch(
    spoofClientPatch,
    modifyLoginWebView,
    fixRedditApiLookups,
    fixFailedGalleryImagesPatch,
    fixMoreComments,
    fixSLinksPatch,
)

@Suppress("unused")
val legacySpoofClientPatch = legacySpoofClientPatch(redditApiPatch)
