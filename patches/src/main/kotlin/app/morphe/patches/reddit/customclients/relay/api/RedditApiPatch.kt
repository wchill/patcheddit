/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.relay.api

import app.morphe.patches.reddit.customclients.relay.fix.apilookups.fixRedditApiLookups
import app.morphe.patches.reddit.customclients.relay.fix.gallery.fixFailedGalleryImagesPatch
import app.morphe.patches.reddit.customclients.relay.fix.login.modifyLoginWebView
import app.morphe.patches.reddit.customclients.relay.fix.morecomments.fixMoreComments
import app.morphe.patches.reddit.customclients.relay.fix.slink.fixSLinksPatch
import app.morphe.patches.reddit.customclients.redditApiPatch

@Suppress("unused")
val redditApiPatch = redditApiPatch(
    spoofClientPatch,
    modifyLoginWebView,
    fixRedditApiLookups,
    fixFailedGalleryImagesPatch,
    fixMoreComments,
    fixSLinksPatch,
)
