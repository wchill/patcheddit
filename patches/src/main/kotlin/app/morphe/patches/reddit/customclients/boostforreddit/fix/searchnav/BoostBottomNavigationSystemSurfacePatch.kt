/*
 * Copyright 2026 brealorg.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.searchnav

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.boostforreddit.wrapHook
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.patcher.util.registersUsed

private const val SYSTEM_SURFACE_EXTENSION_DESCRIPTOR =
    "Lapp/morphe/extension/boost/utils/BoostSearchBottomNavigation;"
private const val CONTENT_CLEARANCE_EXTENSION_DESCRIPTOR =
    "Lapp/morphe/extension/boost/utils/BoostNavContentClearance;"

@Suppress("unused")
val boostBottomNavigationSystemSurfacePatch = morpheBytecodePatch(
    name = "Standardize Boost bottom navigation",
    description = "Makes the bottom navigation look and behave the same on every screen.",
    default = true,
    extensionPatch = ExtensionPatches.Boost,
    category = PatchCategory.NAVIGATION,
) { setting ->
    compatibleWith(*AppCompatibility.Boost)
    dependsOn(ExtensionPatches.Boost)

    execute {
        // Native setup must select the tab without dispatching a user navigation callback.
        setting.patchMethod(drawerMaterialBottomNavigationFingerprint) {
            val match = drawerMaterialBottomNavigationSelectionFingerprint.match(this)
            val selection = match.instructionMatches.single()
            val registers = selection.instruction.registersUsed
            replaceInstruction(
                selection.index,
                "invoke-static {v${registers[0]}, v${registers[1]}}, $SYSTEM_SURFACE_EXTENSION_DESCRIPTOR->setSelectedItemSilently(Landroid/view/View;I)V",
            )
        }
        drawerMaterialBottomNavigationFingerprint.wrapHook(
            setting = setting,
            after = "invoke-static {p0, p1}, $SYSTEM_SURFACE_EXTENSION_DESCRIPTOR->standardizeMaterialNavigation(Landroid/app/Activity;I)V",
        )
        drawerBottomNavigationVisibilityFingerprint.wrapHook(
            setting = setting,
            after = "invoke-static {p0, p1}, $SYSTEM_SURFACE_EXTENSION_DESCRIPTOR->enforceMaterialOnlyRuntimeVisibility(Landroid/app/Activity;Z)V",
        )
        mainActivityOnResumeFingerprint.wrapHook(
            setting = setting,
            after = "invoke-static {p0}, $SYSTEM_SURFACE_EXTENSION_DESCRIPTOR->refreshMaterialNavigationPreference(Landroid/app/Activity;)V",
        )

        // Stock l0() pads lists by a fixed 56dp only while "Hide on scroll" is
        // off, once per list. Let the extension track the bar's real height.
        setting.patchMethod(emptyRecyclerViewBottomNavigationPaddingFingerprint) {
            addInstructions(
                0,
                """
                    invoke-static {p0}, $CONTENT_CLEARANCE_EXTENSION_DESCRIPTOR->apply(Landroid/view/View;)V
                    return-void
                """,
            )
        }
    }
}
