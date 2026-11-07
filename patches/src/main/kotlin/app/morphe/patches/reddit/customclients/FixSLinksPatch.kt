/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 * Modifications Copyright 2026 brealorg.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients

import app.morphe.patcher.patch.BytecodePatchBuilder
import app.morphe.patcher.patch.Patch
import app.morphe.patches.all.misc.PatchCategory

const val RESOLVE_S_LINK_METHOD = "patchResolveSLink(Ljava/lang/String;)Z"
const val SET_ACCESS_TOKEN_METHOD = "patchSetAccessToken(Ljava/lang/String;)V"

fun fixSLinksPatch(
    extensionPatch: Patch<*>,
    block: BytecodePatchBuilder.() -> Unit = {},
) = morpheBytecodePatch(
    name = "Fix /s/ links",
    description = "Opens Reddit share links (/s/) by resolving them to the post they point to.",
    default = true,
    extensionPatch = extensionPatch,
    category = PatchCategory.LINKS,
) {
    dependsOn(extensionPatch)

    block()
}
