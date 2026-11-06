/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (82d70f7).
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.homefab

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.ExtensionPatches

private const val FAB_CLEARANCE_EXTENSION_DESCRIPTOR =
    "Lapp/morphe/extension/boost/utils/BoostFabClearance;"

/**
 * Preserve Boost's system-inset and dependency clearance while also following
 * Snackbars hosted outside the FAB's coordinator by the navigation patches.
 *
 * Applied through [fixBoostHomeFloatingActionMenuOverlapPatch], which removes
 * the static FAB margin and therefore relies on this translation.
 */
internal val fixFabDependencyClearancePatch = bytecodePatch {
    dependsOn(ExtensionPatches.Boost)

    execute {
        listOf(
            fabMenuDependentViewChangedFingerprint,
            fabBottomNavigationDependentViewChangedFingerprint,
        ).forEach { fingerprint ->
            fingerprint.method.addInstructions(
                0,
                """
                    invoke-static {p1, p2}, $FAB_CLEARANCE_EXTENSION_DESCRIPTOR->onDependentViewChanged(Landroid/view/ViewGroup;Landroid/view/View;)V
                    const/4 p0, 0x1
                    return p0
                """,
            )
        }

        listOf(
            fabMenuDependentViewRemovedFingerprint,
            fabBottomNavigationDependentViewRemovedFingerprint,
        ).forEach { fingerprint ->
            fingerprint.method.apply {
                addInstructions(
                    0,
                    """
                        invoke-static {p1, p2, p3}, $FAB_CLEARANCE_EXTENSION_DESCRIPTOR->onDependentViewRemoved(Landroid/view/ViewGroup;Landroid/view/View;Landroid/view/View;)V
                        return-void
                    """,
                )
            }
        }
    }
}
