package app.morphe.patches.reddit.customclients.sync.ads

import app.morphe.patcher.patch.BytecodePatchBuilder
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.returnEarly

fun disableAdsPatch(block: BytecodePatchBuilder.() -> Unit = {}) = bytecodePatch(
    name = "Disable ads",
) {
    execute {
        isAdsEnabledFingerprint.method.returnEarly()
    }

    block()
}
