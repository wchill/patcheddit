package app.morphe.patches.reddit.customclients.boostforreddit.ads

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible

@Suppress("unused")
val disableAdsPatch = bytecodePatch(
    name = "Disable ads",
    default = true
) {
    compatibleWith(*BoostCompatible)

    execute {
        arrayOf(maxMediationFingerprint, admobMediationFingerprint).forEach { fingerprint ->
            fingerprint.method.addInstructions(0, "return-void")
        }
    }
}
