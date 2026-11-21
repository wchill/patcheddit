/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.Patch
import app.morphe.patches.all.misc.PatchCategory

internal const val ESCAPED_URLS_PATCH = "Lapp/morphe/extension/shared/fixes/links/EscapedUrlsPatch;"
internal const val ESCAPED_URLS = "Lapp/morphe/extension/shared/fixes/links/EscapedUrls;"

internal fun fixEscapedUrlsPatch(
    extensionPatch: Patch,
    compatible: Array<Compatibility>,
    additional: BytecodePatchContext.(MorphePatchSetting) -> Unit = {},
) = morpheBytecodePatch(
    name = "Fix escaped URLs",
    description = "Fixes URLs broken by backslash-escaped punctuation in Reddit posts and comments.",
    extensionPatch = extensionPatch,
    category = PatchCategory.LINKS,
) { setting ->
    compatibleWith(*compatible)
    execute {
        setting.returnEnabled(Fingerprint(
            definingClass = ESCAPED_URLS_PATCH,
            name = "isPatchIncluded",
        ).method)
        additional(setting)
    }
}
