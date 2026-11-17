/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.sync.syncforreddit.api

import app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.apilookups.fixRedditApiLookups
import app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.gallery.fixFailedGalleryImagesPatch
import app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.login.modifyLoginWebView
import app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.morecomments.fixMoreComments
import app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.slink.fixSLinksPatch
import app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.user.useUserEndpointPatch
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
    useUserEndpointPatch,
)

@Suppress("unused")
val legacySpoofClientPatch = legacySpoofClientPatch(redditApiPatch)
