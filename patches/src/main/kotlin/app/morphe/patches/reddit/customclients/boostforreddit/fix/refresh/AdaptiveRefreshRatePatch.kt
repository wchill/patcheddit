/*
 * Copyright 2026 brealorg.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.refresh

import app.morphe.patches.reddit.customclients.boostforreddit.bindings.BoostApi

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.boostforreddit.misc.settings.boostMorpheSettingsPatch
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.patcher.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val ADAPTIVE_REFRESH_RATE_EXTENSION_DESCRIPTOR =
    "Lapp/morphe/extension/boost/utils/AdaptiveRefreshRate;"

private val boostApplicationOnCreateFingerprint = BoostApi.MyApplication.onCreate.fingerprint()

@Suppress("unused")
val preferHighRefreshRatePatch = morpheBytecodePatch(
    name = "Prefer high refresh rate",
    description = "Requests a high display refresh rate for Boost on adaptive-refresh devices.",
    default = true,
    extensionPatch = ExtensionPatches.Boost,
    category = PatchCategory.INTERFACE,
    preferenceKey = "morphe_boost_prefer_high_refresh_rate",
) {
    dependsOn(ExtensionPatches.Boost, boostMorpheSettingsPatch)
    compatibleWith(*AppCompatibility.Boost)

    execute {
        boostApplicationOnCreateFingerprint.method.apply {
            val superIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.INVOKE_SUPER &&
                    getReference<MethodReference>()?.name == "onCreate"
            }
            addInstructions(
                superIndex + 1,
                "invoke-static {p0}, $ADAPTIVE_REFRESH_RATE_EXTENSION_DESCRIPTOR->install(Landroid/app/Application;)V",
            )
        }
    }
}
