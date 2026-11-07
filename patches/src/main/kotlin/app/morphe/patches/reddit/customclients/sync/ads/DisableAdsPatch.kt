package app.morphe.patches.reddit.customclients.sync.ads

import app.morphe.patcher.patch.BytecodePatchBuilder
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.util.returnEarly
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.morpheBytecodePatch

fun disableAdsPatch(block: BytecodePatchBuilder.() -> Unit = {}) = disableAdsPatch(ExtensionPatches.Sync, block)

fun disableAdsPatch(
    extensionPatch: Patch<*>,
    block: BytecodePatchBuilder.() -> Unit = {},
) = morpheBytecodePatch(
    name = "Disable ads",
    default = true,
    extensionPatch = extensionPatch,
) { setting ->
    execute {
        setting.patchMethod(IsAdsEnabledFingerprint) { returnEarly(false) }
    }

    block()
}
