package app.morphe.patches.reddit.customclients

import app.morphe.patcher.patch.BytecodePatchBuilder
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.bytecodePatch

const val RESOLVE_S_LINK_METHOD = "patchResolveSLink(Ljava/lang/String;)Z"
const val SET_ACCESS_TOKEN_METHOD = "patchSetAccessToken(Ljava/lang/String;)V"

fun fixSLinksPatch(
    extensionPatch: Patch<*>,
    block: BytecodePatchBuilder.() -> Unit = {},
) = bytecodePatch(name = "Fix /s/ links") {
    dependsOn(extensionPatch)

    block()
}
