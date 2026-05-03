package app.morphe.patches.reddit.customclients.boostforreddit.fix.slink

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.RESOLVE_S_LINK_METHOD
import app.morphe.patches.reddit.customclients.SET_ACCESS_TOKEN_METHOD
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.fixSLinksPatch

const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/boost/FixSLinksPatch;"

@Suppress("unused")
val fixSlinksPatch = fixSLinksPatch(
    extensionPatch = ExtensionPatches.Boost,
) {
    compatibleWith(*AppCompatibility.Boost)

    execute {
        // region Patch navigation handler.

        HandleNavigationFingerprint.method.apply {
            val urlRegister = "p1"
            val tempRegister = "v1"

            addInstructionsWithLabels(
                0,
                """
                    invoke-static { $urlRegister }, $EXTENSION_CLASS_DESCRIPTOR->$RESOLVE_S_LINK_METHOD
                    move-result $tempRegister
                    if-eqz $tempRegister, :continue
                    return $tempRegister
                """,
                ExternalLabel("continue", getInstruction(0)),
            )
        }

        // endregion

        // region Patch set access token.

        GetOAuthAccessTokenFingerprint.method.addInstruction(
            3,
            "invoke-static { v0 }, $EXTENSION_CLASS_DESCRIPTOR->$SET_ACCESS_TOKEN_METHOD",
        )

        // endregion
    }
}
