package app.morphe.patches.reddit.customclients.relay.fix.slink

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.reddit.customclients.RESOLVE_S_LINK_METHOD
import app.morphe.patches.reddit.customclients.SET_ACCESS_TOKEN_METHOD
import app.morphe.patches.reddit.customclients.fixSLinksPatch
import app.morphe.patches.reddit.customclients.relay.RelayCompatible
import app.morphe.patches.reddit.customclients.relay.misc.extension.sharedExtensionPatch
import app.morphe.util.findInstructionIndicesReversedOrThrow
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/relay/FixSLinksPatch;"

@Suppress("unused")
val fixSLinksPatch = fixSLinksPatch(
    extensionPatch = sharedExtensionPatch,
) {
    compatibleWith(*RelayCompatible)

    execute {
        refreshTokenAccessFingerprint.matchAll().forEach { match ->
            match.method.findInstructionIndicesReversedOrThrow(refreshTokenAccessFilter).forEach { index ->
                // Now inject a call to our extension method to also set the access token there.
                val instruction = match.method.getInstruction(index)
                val register = (instruction as TwoRegisterInstruction).registerA
                match.method.addInstruction(
                    index,
                    """
                        invoke-static { v$register }, $EXTENSION_CLASS_DESCRIPTOR->$SET_ACCESS_TOKEN_METHOD
                    """
                )
            }
        }

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
