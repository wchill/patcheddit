/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.menus

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val BEHAVIOR = "Lcom/google/android/material/bottomsheet/BottomSheetBehavior;"
private const val LAYOUT_PARAMS = "Landroidx/coordinatorlayout/widget/CoordinatorLayout\$f;"
private const val COORDINATOR_BEHAVIOR = "Landroidx/coordinatorlayout/widget/CoordinatorLayout\$c;"
private const val VIEW = "Landroid/view/View;"

@Suppress("unused")
val fixToolbarMenuCrashPatch = patch(
    name = "Fix Sync toolbar menu crashes",
    description = "Prevents Sort and More options from crashing on feeds and comment threads.",
) {
    category(PatchCategory.INTERFACE)
    compatibleWith(*AppCompatibility.SyncForReddit)

    execute {
        val constructor = Fingerprint(
            definingClass = "Lcom/google/android/material/bottomsheet/BottomSheetDialog\$EdgeToEdgeCallback;",
            name = "<init>",
            parameters = listOf(VIEW, "Landroidx/core/view/n;"),
            returnType = "V",
            filters = listOf(
                methodCall(definingClass = BEHAVIOR, name = "n0"),
            ),
        ).match()
        with(constructor.method) {
            val fallback = indexOfFirstInstructionOrThrow {
                val reference = getReference<MethodReference>()
                reference != null && reference.definingClass == "Landroidx/core/view/h;" &&
                    reference.parameterTypes.singleOrNull() == VIEW &&
                    reference.returnType == "Landroid/content/res/ColorStateList;"
            }
            // Sync detaches the behavior for top-aligned menus. Use Material's existing view-tint
            // fallback then. The constructor has already saved the insets in p2 to a field.
            addInstructionsWithLabels(
                constructor.instructionMatches[0].index,
                """
                    invoke-virtual {p1}, $VIEW->getLayoutParams()Landroid/view/ViewGroup${'$'}LayoutParams;
                    move-result-object p2
                    instance-of p2, p2, $LAYOUT_PARAMS
                    if-eqz p2, :view_tint
                    invoke-virtual {p1}, $VIEW->getLayoutParams()Landroid/view/ViewGroup${'$'}LayoutParams;
                    move-result-object p2
                    check-cast p2, $LAYOUT_PARAMS
                    invoke-virtual {p2}, $LAYOUT_PARAMS->f()$COORDINATOR_BEHAVIOR
                    move-result-object p2
                    instance-of p2, p2, $BEHAVIOR
                    if-eqz p2, :view_tint
                """,
                ExternalLabel("view_tint", getInstruction(fallback)),
            )
        }
    }
}
