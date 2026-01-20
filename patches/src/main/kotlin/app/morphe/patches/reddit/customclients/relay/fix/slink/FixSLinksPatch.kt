package app.morphe.patches.reddit.customclients.relay.fix.slink

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.all.misc.transformation.transformInstructionsPatch
import app.morphe.patches.reddit.customclients.RESOLVE_S_LINK_METHOD
import app.morphe.patches.reddit.customclients.SET_ACCESS_TOKEN_METHOD
import app.morphe.patches.reddit.customclients.fixSLinksPatch
import app.morphe.patches.reddit.customclients.relay.misc.extension.sharedExtensionPatch
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/relay/FixSLinksPatch;"

@Suppress("unused")
val fixSLinksPatch = fixSLinksPatch(
    extensionPatch = sharedExtensionPatch,
) {
    dependsOn(
        transformInstructionsPatch(
            filterMap = { classDef, method, instruction, index ->
                // Find all instructions that set a Reddit refresh token.
                if (classDef.type.startsWith("Lapp/morphe")) {
                    return@transformInstructionsPatch null
                }

                val ref = instruction.getReference<FieldReference>()
                if (instruction.opcode != Opcode.IPUT_OBJECT || ref == null) {
                    return@transformInstructionsPatch null
                }

                if (ref.definingClass != "Lreddit/news/oauth/reddit/model/RedditAccessToken;" || ref.name != "refreshToken") {
                    return@transformInstructionsPatch null
                }

                return@transformInstructionsPatch instruction to index
            },
            transform = { method, entry ->
                // Now inject a call to our extension method to also set the access token there.
                val (instruction, index) = entry
                val register = (instruction as TwoRegisterInstruction).registerA
                method.addInstruction(
                    index,
                    """
                        invoke-static { v$register }, $EXTENSION_CLASS_DESCRIPTOR->$SET_ACCESS_TOKEN_METHOD
                    """
                )
            }
        )
    )

    compatibleWith(
        "free.reddit.news"("10.2.40"),
        "reddit.news"("10.2.40"),
    )

    execute {
        // region Patch navigation handler.

        intentFilterActivityFingerprint.method.apply {
            val index = intentFilterActivityFingerprint.instructionMatches.last().index
            val register = getInstruction<OneRegisterInstruction>(index).registerA

            addInstructionsWithLabels(
                index + 1,
                """
                    invoke-static { v$register }, $EXTENSION_CLASS_DESCRIPTOR->$RESOLVE_S_LINK_METHOD
                    move-result v5
                    if-eqz v5, :continue
                    return-void
                """,
                ExternalLabel("continue", getInstruction(index + 1)),
            )
        }

        // endregion
    }
}
