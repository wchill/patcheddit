/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.insets

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patcher.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.patches.reddit.customclients.boostforreddit.wrapHook

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/boost/utils/BoostSystemBarInsetsFix;"

@Suppress("unused")
val fixNavigationBarOverlapPatch = bytecodePatch(
    name = "Fix Boost navigation bar overlap",
    description = "Keeps Boost's bottom controls and drawer clear of the system navigation bar on Android 15 and newer.",
    default = true
) {
    category(PatchCategory.NAVIGATION)
    dependsOn(ExtensionPatches.Boost)
    compatibleWith(*AppCompatibility.Boost)

    execute {
        myApplicationOnCreateFingerprint.method.apply {
            val superOnCreateIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.INVOKE_SUPER &&
                    getReference<MethodReference>()?.name == "onCreate"
            }

            addInstructions(
                superOnCreateIndex + 1,
                """
                    invoke-static {p0}, $EXTENSION_CLASS_DESCRIPTOR->install(Landroid/app/Application;)V
                    """
            )
        }

        mainActivityOnResumeFingerprint.wrapHook(
            after = "invoke-static {p0}, $EXTENSION_CLASS_DESCRIPTOR->applyMainInsets(Landroid/app/Activity;)V",
        )
    }
}
