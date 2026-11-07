/*
 * Copyright 2026 brealorg.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.widget

import app.morphe.patches.reddit.customclients.boostforreddit.bindings.BoostApi

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patcher.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import app.morphe.patches.all.misc.PatchCategory

private const val PENDING_INTENT_GET_ACTIVITY_REFERENCE =
    "Landroid/app/PendingIntent;->getActivity(Landroid/content/Context;ILandroid/content/Intent;I)Landroid/app/PendingIntent;"

private val BOOST_PENDING_INTENT_FLAGS_REFERENCE =
    BoostApi.Utils.imageWidgetThumbnailSize.toString()

@Suppress("unused")
val fixImageWidgetClickTargetPatch = morpheBytecodePatch(
    name = "Fix Boost image widget click target",
    description = "Makes the image widget open the post it shows instead of an older one.",
    default = true,
    extensionPatch = ExtensionPatches.Boost,
    category = PatchCategory.INTERFACE,
) { setting ->
    compatibleWith(*AppCompatibility.Boost)

    execute {
        setting.patchMethod(imageWidgetProviderUpdateWithSubmissionFingerprint) {
            val getActivityIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.INVOKE_STATIC &&
                    getReference<MethodReference>()?.toString() == PENDING_INTENT_GET_ACTIVITY_REFERENCE
            }
            val intentRegister = getInstruction<FiveRegisterInstruction>(getActivityIndex).registerE

            val pendingIntentFlagsIndex = implementation!!.instructions
                .withIndex()
                .filter { (index, instruction) ->
                    index < getActivityIndex &&
                        instruction.opcode == Opcode.INVOKE_STATIC &&
                        instruction.getReference<MethodReference>()?.toString() == BOOST_PENDING_INTENT_FLAGS_REFERENCE
                }
                .lastOrNull()
                ?.index
                ?: throw IllegalStateException(
                    "Could not find UpdateImageWidget flags call before CommentsActivity PendingIntent.getActivity"
                )

            if (pendingIntentFlagsIndex >= getActivityIndex) {
                throw IllegalStateException(
                    "Unexpected UpdateImageWidget instruction order: flags=$pendingIntentFlagsIndex getActivity=$getActivityIndex"
                )
            }

            addInstructions(
                pendingIntentFlagsIndex,
                """
                    new-instance v4, Ljava/lang/StringBuilder;
                    invoke-direct {v4}, Ljava/lang/StringBuilder;-><init>()V

                    const-string v5, "morphe-widget-image://open/"
                    invoke-virtual {v4, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

                    invoke-virtual {v4, p1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

                    const-string v5, "/"
                    invoke-virtual {v4, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

                    invoke-virtual {p2}, Ljava/lang/Object;->hashCode()I
                    move-result v5

                    invoke-virtual {v4, v5}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

                    invoke-virtual {v4}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
                    move-result-object v4

                    invoke-static {v4}, Landroid/net/Uri;->parse(Ljava/lang/String;)Landroid/net/Uri;
                    move-result-object v4

                    invoke-virtual {v$intentRegister, v4}, Landroid/content/Intent;->setData(Landroid/net/Uri;)Landroid/content/Intent;
                """,
            )
        }
    }
}
