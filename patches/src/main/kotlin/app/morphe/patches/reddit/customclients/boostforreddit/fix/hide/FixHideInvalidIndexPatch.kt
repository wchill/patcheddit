/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.hide

import app.morphe.patches.reddit.customclients.boostforreddit.bindings.BoostApi

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patcher.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.util.indexOfFirstInstructionReversedOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import app.morphe.patches.all.misc.PatchCategory

@Suppress("unused")
val fixHideInvalidIndexPatch = morpheBytecodePatch(
    name = "Fix Hide crash",
    description = "Fixes crashes and jumps in the feed when hiding read posts.",
    default = true,
    extensionPatch = ExtensionPatches.Boost,
    category = PatchCategory.FEED,
) { setting ->
    compatibleWith(*AppCompatibility.Boost)

    execute {
        // Preserve the backend operation, but bound-check the resolved list
        // position immediately before the activity mutates its own feed.
        setting.patchMethod(subredditBaseHideToggleFingerprint) {
            val removeIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.INVOKE_INTERFACE &&
                    getReference<MethodReference>()?.toString() ==
                    "Ljava/util/List;->remove(I)Ljava/lang/Object;"
            }
            val remove = getInstruction<FiveRegisterInstruction>(removeIndex)
            addInstructionsWithLabels(
                removeIndex,
                """
                    if-ltz v${remove.registerD}, :morphe_skip_activity_hide_remove
                    invoke-interface {v${remove.registerC}}, Ljava/util/List;->size()I
                    move-result v0
                    if-ge v${remove.registerD}, v0, :morphe_skip_activity_hide_remove
                """,
                ExternalLabel("morphe_skip_activity_hide_remove", getInstruction(removeIndex + 1)),
            )
        }

        // The presenter callback can change the backing list. Resolve the
        // submission again after it returns instead of removing a stale index.
        setting.patchMethod(hideFragmentFingerprint) {
            val callbackIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.INVOKE_INTERFACE &&
                    getReference<MethodReference>()?.toString() ==
                    BoostApi.ThingFragmentListener.onHideToggle.toString()
            }
            val removeIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.INVOKE_VIRTUAL &&
                    getReference<MethodReference>()?.toString() ==
                    "Ljava/util/ArrayList;->remove(I)Ljava/lang/Object;"
            }
            check(callbackIndex < removeIndex)
            val returnIndex = indexOfFirstInstructionReversedOrThrow {
                opcode == Opcode.RETURN_VOID
            }
            addInstructionsWithLabels(
                removeIndex,
                """
                    invoke-static {v1, p1}, ${BoostApi.CodecSpecificDataUtil.findSubmissionIndex}
                    move-result v0
                    if-ltz v0, :morphe_skip_fragment_hide_remove
                    invoke-virtual {v1}, Ljava/util/ArrayList;->size()I
                    move-result v2
                    if-ge v0, v2, :morphe_skip_fragment_hide_remove
                """,
                ExternalLabel("morphe_skip_fragment_hide_remove", getInstruction(returnIndex)),
            )
        }

        // 1.14.0 no longer has the per-layout ad wrappers/O2 conversion.
        // The read-post action consumes the backing-list index directly.
        setting.patchMethod(feedActionS1InvalidIndexFingerprint) {
            val returnIndex = indexOfFirstInstructionReversedOrThrow {
                opcode == Opcode.RETURN_VOID
            }

            addInstructionsWithLabels(
                0,
                """
                    if-ltz p1, :morphe_skip_s1_invalid_index
                    iget-object v0, p0, ${BoostApi.SubmissionFragment.submissions}
                    if-eqz v0, :morphe_skip_s1_invalid_index
                    invoke-virtual {v0}, Ljava/util/ArrayList;->size()I
                    move-result v1
                    if-ge p1, v1, :morphe_skip_s1_invalid_index
                """,
                ExternalLabel("morphe_skip_s1_invalid_index", getInstruction(returnIndex)),
            )
        }

    }
}
