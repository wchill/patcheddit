package app.morphe.patches.reddit.layout.disablescreenshotpopup

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val disableScreenshotPopupPatch = bytecodePatch(
    name = "Disable screenshot popup",
    description = "Disables the popup that shows up when taking a screenshot.",
) {
    compatibleWith("com.reddit.frontpage")

    execute {
        disableScreenshotPopupFingerprint.method.addInstruction(0, "return-void")
    }
}
