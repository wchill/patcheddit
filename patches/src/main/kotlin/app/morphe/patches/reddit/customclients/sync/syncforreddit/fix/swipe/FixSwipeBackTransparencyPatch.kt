package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.swipe

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.reddit.customclients.AppCompatibility
import com.android.tools.smali.dexlib2.AccessFlags

private val convertToTranslucentFingerprint = Fingerprint(
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.STATIC),
    returnType = "V",
    parameters = listOf("Landroid/app/Activity;"),
    strings = listOf("getActivityOptions", "convertToTranslucent"),
)

private val convertFromTranslucentFingerprint = Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "V",
    parameters = listOf("Landroid/app/Activity;"),
    strings = listOf("convertFromTranslucent"),
)

@Suppress("unused")
val fixSwipeBackTransparencyPatch = bytecodePatch(
    name = "Fix swipe back transparency",
    description = "Shows the previous screen while swiping back from a post on newer Android versions.",
    default = true,
) {
    compatibleWith(*AppCompatibility.SyncForReddit)

    execute {
        listOf(
            convertToTranslucentFingerprint to true,
            convertFromTranslucentFingerprint to false,
        ).forEach { (fingerprint, translucent) ->
            fingerprint.method.apply {
                // Both native helpers have spare locals. Do not overwrite the Activity parameter.
                check(implementation!!.registerCount >= 3) {
                    "Sync transparency helper requires two local registers"
                }
                // Sync reflects into hidden Activity methods and silently ignores conversion failures.
                // API 30 introduced the supported equivalent. Keep the legacy path below API 30,
                // and as a fallback if the public API reports that conversion did not succeed.
                addInstructionsWithLabels(
                    0,
                    """
                        sget v0, Landroid/os/Build${'$'}VERSION;->SDK_INT:I
                        const/16 v1, 0x1e
                        if-lt v0, v1, :legacy
                        const/4 v0, ${if (translucent) "0x1" else "0x0"}
                        invoke-virtual { p0, v0 }, Landroid/app/Activity;->setTranslucent(Z)Z
                        move-result v0
                        if-eqz v0, :legacy
                        return-void
                    """,
                    ExternalLabel("legacy", getInstruction(0)),
                )
            }
        }
    }
}
