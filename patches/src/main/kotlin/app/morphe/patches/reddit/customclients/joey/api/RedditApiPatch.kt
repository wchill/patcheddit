/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.joey.api

import app.morphe.patches.reddit.customclients.joey.fix.apilookups.fixRedditApiLookups
import app.morphe.patches.reddit.customclients.joey.fix.login.modifyLoginWebView
import app.morphe.patches.reddit.customclients.joey.fix.morecomments.fixMoreComments
import app.morphe.patches.reddit.customclients.redditApiPatch

@Suppress("unused")
val redditApiPatch = redditApiPatch(
    spoofClientPatch,
    modifyLoginWebView,
    fixRedditApiLookups,
    fixMoreComments,
)
