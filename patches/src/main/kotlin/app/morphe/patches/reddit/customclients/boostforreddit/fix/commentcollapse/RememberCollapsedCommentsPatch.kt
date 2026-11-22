/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.commentcollapse

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.boostforreddit.bindings.BoostApi
import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction22t
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

@Suppress("unused")
val rememberCollapsedCommentsPatch = morpheBytecodePatch(
    name = "Remember collapsed comments",
    description = "Keeps collapsed replies closed when you reopen their parent comment.",
    extensionPatch = ExtensionPatches.Boost,
    category = PatchCategory.COMMENTS,
) { setting ->
    compatibleWith(*AppCompatibility.Boost)

    execute {
        setting.patchMethod(BoostApi.CommentsFragment.expandCommentBranch.fingerprint()) {
            preserveCollapsedReplies()
        }
    }
}

private fun MutableMethod.preserveCollapsedReplies() {
    val row = BoostApi.CommentViewModel
    val showIndex = indexOfFirstInstructionOrThrow {
        opcode == Opcode.IPUT_BOOLEAN && getReference<FieldReference>()?.let(row.isVisible::matches) == true
    }
    val show = getInstruction<TwoRegisterInstruction>(showIndex)
    val expand = getInstruction<TwoRegisterInstruction>(showIndex + 1)
    val unfold = getInstruction<TwoRegisterInstruction>(showIndex + 2)
    val skip = getInstruction<BuilderInstruction22t>(showIndex - 1)
    val depth = getInstruction<TwoRegisterInstruction>(showIndex - 2)
    check(
        expand.opcode == Opcode.IPUT_BOOLEAN && expand.getReference<FieldReference>()?.let(row.isExpanded::matches) == true &&
            unfold.opcode == Opcode.IPUT_BOOLEAN && unfold.getReference<FieldReference>()?.let(row.isFullyCollapsed::matches) == true &&
            expand.registerA == show.registerA && expand.registerB == show.registerB &&
            unfold.registerB == show.registerB && skip.opcode == Opcode.IF_LE &&
            depth.opcode == Opcode.IGET && depth.getReference<FieldReference>()?.let(row.depth::matches) == true &&
            depth.registerA == skip.registerA && depth.registerB == show.registerB,
    ) { "Unexpected Boost comment expansion loop" }

    val visible = show.registerA
    val comment = show.registerB
    val childDepth = skip.registerA
    val parentDepth = skip.registerB
    // Reuse the native false register once the parent has been unfolded and the result initialized.
    val collapsedDepth = unfold.registerA
    val initializeResult = indexOfFirstInstructionOrThrow {
        opcode == Opcode.MOVE && (this as TwoRegisterInstruction).registerB == collapsedDepth
    }
    check(initializeResult < showIndex - 2) { "Unexpected Boost comment expansion setup" }
    val nextRow = getInstruction(skip.target.location.index)

    removeInstruction(showIndex + 2)
    removeInstruction(showIndex + 1)
    // Rows are in depth-first order. After showing a collapsed child, skip deeper rows
    // until reaching its sibling (or an ancestor's sibling).
    addInstructionsWithLabels(
        showIndex,
        """
            if-eq v$collapsedDepth, v$parentDepth, :restore_child
            if-gt v$childDepth, v$collapsedDepth, :next_row
            move v$collapsedDepth, v$parentDepth
            :restore_child
            iget-boolean v$visible, v$comment, ${row.isExpanded}
            if-nez v$visible, :show_child
            move v$collapsedDepth, v$childDepth
            :show_child
            const/4 v$visible, 0x1
        """,
        ExternalLabel("next_row", nextRow),
    )
    addInstruction(initializeResult + 1, "move v$collapsedDepth, v$parentDepth")
}
