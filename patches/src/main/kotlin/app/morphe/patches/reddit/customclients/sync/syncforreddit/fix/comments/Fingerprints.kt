/*
 * Copyright 2026 trulow.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.comments

import app.morphe.patcher.Fingerprint
import app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.NETWORK_RESPONSE
import app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.VOLLEY_RESPONSE

/**
 * Sync keeps its source file names, so classes are matched by them. Obfuscated names differ between
 * Sync and Sync Pro (y8/f and j8/f for the request, ya/d.V() and ja/d.z0() for the handler).
 */
private const val MORE_REQUEST_SOURCE = "OAuthCommentsMoreRequest.java"

/**
 * CommentsPresenterImpl's "load more" handler: logs "Requesting more: ", then builds the request from
 * its only parameter, the Post row of the "more" item.
 */
internal object RequestMoreFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("L"),
    strings = listOf("Requesting more: "),
    custom = { _, classDef -> classDef.sourceFile == "CommentsPresenterImpl.java" },
)

/**
 * OAuthCommentsMoreRequest(Context, String listId, String linkId, String moreId, String children,
 * int position, int level, String sort, Listener, ErrorListener).
 */
internal object MoreRequestConstructorFingerprint : Fingerprint(
    name = "<init>",
    returnType = "V",
    parameters = listOf(
        "Landroid/content/Context;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "I",
        "I",
        "Ljava/lang/String;",
        "Lcom/android/volley/Response\$Listener;",
        "Lcom/android/volley/Response\$ErrorListener;",
    ),
    strings = listOf("api/morechildren?always_show_media=1&profile_img=true"),
    custom = { _, classDef -> classDef.sourceFile == MORE_REQUEST_SOURCE },
)

internal object MoreParseResponseFingerprint : Fingerprint(
    classFingerprint = MoreRequestConstructorFingerprint,
    name = "parseNetworkResponse",
    returnType = VOLLEY_RESPONSE,
    parameters = listOf(NETWORK_RESPONSE),
)
