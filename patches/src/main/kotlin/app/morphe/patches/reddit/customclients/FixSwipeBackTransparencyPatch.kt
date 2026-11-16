/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * Copyright 2026 Computron010.
 * Adapted from https://github.com/wchill/patcheddit/pull/135.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.all.misc.PatchCategory
import com.android.tools.smali.dexlib2.AccessFlags

internal fun fixSwipeBackTransparencyPatch(
    extensionPatch: Patch<*>,
    compatible: Array<Compatibility>,
    restoreOpaque: Boolean,
) = morpheBytecodePatch(
    name = "Fix swipe back transparency",
    description = "Shows the previous screen while swiping back on newer Android versions.",
    extensionPatch = extensionPatch,
    category = PatchCategory.INTERFACE,
) { setting ->
    compatibleWith(*compatible)
    execute {
        val helpers = mutableListOf(Fingerprint(
            accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.STATIC),
            returnType = "V",
            parameters = listOf("Landroid/app/Activity;"),
            strings = listOf("getActivityOptions", "convertToTranslucent"),
        ) to true)
        if (restoreOpaque) helpers += Fingerprint(
            accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
            returnType = "V",
            parameters = listOf("Landroid/app/Activity;"),
            strings = listOf("convertFromTranslucent"),
        ) to false
        for ((fingerprint, translucent) in helpers) {
            fingerprint.method.apply {
                require(checkNotNull(implementation).registerCount >= 3) {
                    "Transparency helper requires two local registers"
                }
                // Android 11 exposes the public equivalent of these hidden reflection calls.
                addInstructionsWithLabels(0, """
                    ${setting.checkEnabled("v0", "legacy")}
                    sget v0, Landroid/os/Build${'$'}VERSION;->SDK_INT:I
                    const/16 v1, 0x1e
                    if-lt v0, v1, :legacy
                    const/4 v0, ${if (translucent) "0x1" else "0x0"}
                    invoke-virtual {p0, v0}, Landroid/app/Activity;->setTranslucent(Z)Z
                    move-result v0
                    if-eqz v0, :legacy
                    return-void
                """.trimIndent(), ExternalLabel("legacy", getInstruction(0)))
            }
        }
    }
}
