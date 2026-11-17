/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.infinity.api

import app.morphe.patches.reddit.customclients.infinity.fix.login.modifyLoginWebView
import app.morphe.patches.reddit.customclients.redditApiPatch
import app.morphe.patches.reddit.customclients.legacySpoofClientPatch

@Suppress("unused")
val redditApiPatch = redditApiPatch(spoofClientPatch, modifyLoginWebView)

@Suppress("unused")
val legacySpoofClientPatch = legacySpoofClientPatch(redditApiPatch)
