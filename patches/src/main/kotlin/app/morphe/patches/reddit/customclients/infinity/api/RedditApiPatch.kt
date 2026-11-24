/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.infinity.api

import app.morphe.patches.reddit.customclients.infinity.fix.login.modifyLoginWebView
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.fixMoreCommentsPatch
import app.morphe.patches.reddit.customclients.fixFailedGalleryImagesPatch
import app.morphe.patches.reddit.customclients.fixRedditApiLookupsPatch
import app.morphe.patches.reddit.customclients.redditApiPatch
import app.morphe.patches.reddit.customclients.legacySpoofClientPatch

internal val infinityMoreComments = fixMoreCommentsPatch(ExtensionPatches.Infinity, AppCompatibility.Infinity)
internal val infinityApiLookups = fixRedditApiLookupsPatch(
    ExtensionPatches.Infinity, AppCompatibility.Infinity, extraDependency = infinityLookupTransportPatch,
)
internal val infinityGallery = fixFailedGalleryImagesPatch(ExtensionPatches.Infinity, AppCompatibility.Infinity)

@Suppress("unused")
val redditApiPatch = redditApiPatch(
    spoofClientPatch, modifyLoginWebView, infinityMoreComments, infinityApiLookups, infinityShareLinks, infinityGallery,
)

@Suppress("unused")
val legacySpoofClientPatch = legacySpoofClientPatch(redditApiPatch)
