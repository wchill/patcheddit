/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.homefab

import app.morphe.patches.reddit.customclients.boostforreddit.bindings.BoostApi

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.all.misc.PatchCategory

private const val SNACKBAR_ANCHOR_EXTENSION_DESCRIPTOR =
    "Lapp/morphe/extension/boost/utils/BoostSnackbarAnchor;"

// SnackbarBaseLayout: the Snackbar's own view.

// BaseTransientBottomBar.show() is inlined into Snackbar.m() in 1.14.0.
private val snackbarShowFingerprint = BoostApi.Snackbar.snackbarShow.fingerprint()

// AHBottomNavigationBehavior.updateSnackbar(View child, View snackbar): runs
// from layoutDependsOn on every layout and sets the Snackbar's bottom margin
// to the legacy AHBottomNavigation height. That navigation is hidden by the
// canonical bottom navigation patches, so the margin is forced to 0 (dropping
// Material's anchor offset) and a layout is requested on every frame.
private val legacyNavigationSnackbarMarginFingerprint = BoostApi.AHBottomNavigationBehavior.legacyNavigationSnackbarMargin.fingerprint()

@Suppress("unused")
val anchorSnackbarAboveNavigationPatch = morpheBytecodePatch(
    name = "Anchor Boost Snackbars above bottom navigation",
    description =
        "Shows Boost Snackbars above the visible bottom navigation instead of covering it.",
    default = true,
    extensionPatch = ExtensionPatches.Boost,
    category = PatchCategory.NAVIGATION,
) { setting ->
    dependsOn(ExtensionPatches.Boost)
    compatibleWith(*AppCompatibility.Boost)

    execute {
        setting.patchMethod(legacyNavigationSnackbarMarginFingerprint) {
            addInstructionsWithLabels(
                0,
                """
                    invoke-virtual {p1}, Landroid/view/View;->isShown()Z
                    move-result v0
                    if-nez v0, :morphe_update_legacy_snackbar
                    return v0
                """,
                ExternalLabel("morphe_update_legacy_snackbar", getInstruction(0)),
            )
        }

        setting.patchMethod(snackbarShowFingerprint) {
            addInstructionsWithLabels(
                0,
                """
                    invoke-virtual {p0}, ${BoostApi.BaseTransientBottomBar.getAnchorView}
                    move-result-object v1
                    iget-object v0, p0, ${BoostApi.BaseTransientBottomBar.targetParent}
                    iget-object v2, p0, ${BoostApi.BaseTransientBottomBar.view}
                    invoke-static {v0, v1, v2}, Lapp/morphe/extension/boost/utils/BoostSnackbarAnchor;->anchorFor(Landroid/view/ViewGroup;Landroid/view/View;Landroid/view/View;)Landroid/view/View;
                    move-result-object v0
                    if-eqz v0, :morphe_show_snackbar
                    invoke-virtual {p0, v0}, ${BoostApi.BaseTransientBottomBar.setAnchorView}
                """,
                ExternalLabel("morphe_show_snackbar", getInstruction(0)),
            )
        }
    }
}
