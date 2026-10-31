/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.http.decoder

import app.morphe.patcher.Fingerprint

private const val INTERCEPTOR_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/boost/decoder/CommentDecoderInterceptor;"

internal object EnableCommentDecoderFingerprint : Fingerprint(
    definingClass = INTERCEPTOR_CLASS_DESCRIPTOR,
    name = "isPatchIncluded"
)

internal object GetAllowedSubredditsFingerprint : Fingerprint(
    definingClass = INTERCEPTOR_CLASS_DESCRIPTOR,
    name = "getAllowedSubreddits"
)
