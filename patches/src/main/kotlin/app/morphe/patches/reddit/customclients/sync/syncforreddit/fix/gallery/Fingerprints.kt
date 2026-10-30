/*
 * Copyright 2026 trulow.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.gallery

import app.morphe.patcher.Fingerprint
import app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.NETWORK_RESPONSE
import app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.VOLLEY_RESPONSE

/**
 * Sync keeps its source file names, so classes are matched by them. Obfuscated names differ between
 * Sync and Sync Pro (y8/q.b() and j8/q.c() for this method).
 */
private const val GALLERY_REQUEST_SOURCE = "OAuthGalleryRequest.java"

/** OAuthGalleryRequest's static URL builder (also the key Sync reads its gallery cache with). */
internal object GalleryUrlFingerprint : Fingerprint(
    isStatic = true,
    returnType = "Ljava/lang/String;",
    parameters = listOf("Ljava/lang/String;"),
    strings = listOf("https://oauth.reddit.com/api/info.json?id=t3_"),
    custom = { _, classDef -> classDef.sourceFile == GALLERY_REQUEST_SOURCE },
)

internal object GalleryParseResponseFingerprint : Fingerprint(
    classFingerprint = GalleryUrlFingerprint,
    name = "parseNetworkResponse",
    returnType = VOLLEY_RESPONSE,
    parameters = listOf(NETWORK_RESPONSE),
)
