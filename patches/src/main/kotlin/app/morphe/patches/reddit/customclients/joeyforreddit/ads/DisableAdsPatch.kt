package app.morphe.patches.reddit.customclients.joeyforreddit.ads

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.joeyforreddit.JoeyForRedditFreeCompatible
import app.morphe.patches.reddit.customclients.joeyforreddit.detection.piracy.disablePiracyDetectionPatch

@Suppress("unused")
val disableAdsPatch = bytecodePatch(
    name = "Disable ads",
    default = true
) {
    dependsOn(disablePiracyDetectionPatch)

    compatibleWith(JoeyForRedditFreeCompatible)

    execute {
        isAdFreeUserFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """,
        )
    }
}
