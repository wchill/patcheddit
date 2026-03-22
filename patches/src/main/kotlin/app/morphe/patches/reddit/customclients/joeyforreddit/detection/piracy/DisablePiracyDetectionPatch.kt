package app.morphe.patches.reddit.customclients.joeyforreddit.detection.piracy

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch

val disablePiracyDetectionPatch = bytecodePatch(
    default = false,
) {
    execute {
        piracyDetectionFingerprint.method.addInstruction(0, "return-void")
    }
}
