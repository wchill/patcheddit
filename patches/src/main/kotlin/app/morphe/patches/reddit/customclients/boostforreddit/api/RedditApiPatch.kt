/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.api

import app.morphe.patches.reddit.customclients.boostforreddit.fix.apilookups.fixRedditApiLookups
import app.morphe.patches.reddit.customclients.boostforreddit.fix.feed.fixRAll
import app.morphe.patches.reddit.customclients.boostforreddit.fix.gallery.fixFailedGalleryImagesPatch
import app.morphe.patches.reddit.customclients.boostforreddit.fix.login.modifyLoginWebView
import app.morphe.patches.reddit.customclients.boostforreddit.fix.slink.fixSlinksPatch
import app.morphe.patches.reddit.customclients.redditApiPatch
import app.morphe.patches.reddit.customclients.legacySpoofClientPatch

@Suppress("unused")
val redditApiPatch = redditApiPatch(
    spoofClientPatch,
    modifyLoginWebView,
    fixRedditApiLookups,
    fixFailedGalleryImagesPatch,
    fixRAll,
    fixSlinksPatch,
)

@Suppress("unused")
val legacySpoofClientPatch = legacySpoofClientPatch(redditApiPatch)
