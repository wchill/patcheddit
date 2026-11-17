/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients

import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.Patch
import app.morphe.patches.all.misc.PatchCategory

internal const val COMMENT_VIDEO_LINKS = "Lapp/morphe/extension/shared/fixes/media/CommentVideoLinks;"

internal fun fixCommentVideoLinksPatch(
    extensionPatch: Patch<*>,
    compatible: Array<Compatibility>,
    hook: BytecodePatchContext.() -> Unit,
) = morpheBytecodePatch(
    name = "Fix comment video links",
    description = "Restores native playback of Reddit videos embedded in comments.",
    extensionPatch = extensionPatch,
    category = PatchCategory.MEDIA,
) { setting ->
    compatibleWith(*compatible)
    execute {
        setting.guardHook(COMMENT_VIDEO_LINKS, "baseUrl")
        hook()
    }
}
