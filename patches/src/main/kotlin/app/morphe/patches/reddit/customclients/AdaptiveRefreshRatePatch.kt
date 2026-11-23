/*
 * Copyright 2026 brealorg.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.util.getReference
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val ADAPTIVE_REFRESH_RATE =
    "Lapp/morphe/extension/shared/fixes/refresh/AdaptiveRefreshRate;"

internal fun adaptiveRefreshRatePatch(
    extensionPatch: Patch,
    compatible: Array<Compatibility>,
    applicationOnCreate: Fingerprint,
    preferenceKey: String,
    description: String,
) = morpheBytecodePatch(
    name = "Prefer high refresh rate",
    description = description,
    extensionPatch = extensionPatch,
    category = PatchCategory.INTERFACE,
    preferenceKey = preferenceKey,
) { setting ->
    compatibleWith(*compatible)

    execute {
        setting.returnEnabled(Fingerprint(
            definingClass = ADAPTIVE_REFRESH_RATE,
            name = "isEnabled",
            parameters = emptyList(),
            returnType = "Z",
        ).method)
        applicationOnCreate.method.apply {
            val superIndex = indexOfFirstInstructionOrThrow {
                val call = getReference<MethodReference>()
                (opcode == Opcode.INVOKE_SUPER || opcode == Opcode.INVOKE_SUPER_RANGE) &&
                    call != null && call.name == "onCreate" && call.parameterTypes.isEmpty()
            }
            addInstructions(
                superIndex + 1,
                "invoke-static/range {p0 .. p0}, $ADAPTIVE_REFRESH_RATE->install(Landroid/app/Application;)V",
            )
        }
    }
}

private fun clientRefreshRatePatch(
    extensionPatch: Patch,
    compatible: Array<Compatibility>,
    applicationType: String,
) = adaptiveRefreshRatePatch(
    extensionPatch = extensionPatch,
    compatible = compatible,
    applicationOnCreate = Fingerprint(
        definingClass = applicationType,
        name = "onCreate",
        parameters = emptyList(),
        returnType = "V",
    ),
    preferenceKey = "morphe_patch_prefer_high_refresh_rate",
    description = "Requests a high display refresh rate on adaptive-refresh devices.",
)

val baconReaderRefreshRatePatch = clientRefreshRatePatch(
    ExtensionPatches.BaconReader, AppCompatibility.BaconReader, "Lcom/onelouder/baconreader/BaconReader;",
)
val joeyRefreshRatePatch = clientRefreshRatePatch(
    ExtensionPatches.Joey, AppCompatibility.JoeyForReddit, "Lo/o/joey/MyApplication;",
)
val relayRefreshRatePatch = clientRefreshRatePatch(
    ExtensionPatches.Relay, AppCompatibility.Relay, "Lreddit/news/RelayApplication;",
)
val rifRefreshRatePatch = clientRefreshRatePatch(
    ExtensionPatches.RIF, AppCompatibility.RedditIsFun, "Lcom/andrewshu/android/reddit/RedditIsFunApplication;",
)
val syncRefreshRatePatch = clientRefreshRatePatch(
    ExtensionPatches.Sync, AppCompatibility.SyncForReddit, "Lcom/laurencedawson/reddit_sync/RedditApplication;",
)
