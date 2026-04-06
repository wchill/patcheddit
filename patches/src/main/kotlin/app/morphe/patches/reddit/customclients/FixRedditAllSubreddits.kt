/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients

import app.morphe.patcher.patch.BytecodePatchBuilder
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.bytecodePatch

fun fixRedditAllSubreddits(
    extensionPatch: Patch<*>,
    block: BytecodePatchBuilder.() -> Unit = {},
) = bytecodePatch(
    name = "Fix /r/all",
    default = true
) {
    dependsOn(extensionPatch)

    block()
}
