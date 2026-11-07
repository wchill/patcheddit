/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patches.reddit.customclients.MorphePatchSetting
import app.morphe.patches.reddit.customclients.wrapMorpheHook

/**
 * R8 reuses parameter registers inside Boost 1.14 methods. A wrapper keeps the
 * original arguments alive for hooks without changing the native register map.
 * The native result, when present, is available to [after] in v0 (v0/v1 for wide).
 */
context(patchContext: BytecodePatchContext)
internal fun Fingerprint.wrapHook(
    before: String = "",
    after: String = "",
    setting: MorphePatchSetting? = null,
) {
    wrapMorpheHook(before, after, setting)
}
