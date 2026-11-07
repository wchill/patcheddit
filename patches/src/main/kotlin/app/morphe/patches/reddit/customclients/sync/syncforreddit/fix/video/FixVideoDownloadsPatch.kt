package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.video

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/syncforreddit/FixRedditVideoDownloadPatch;"
private const val GET_LINKS_METHOD = "getLinks([B)[Ljava/lang/String;"

@Suppress("unused")
val fixVideoDownloadsPatch = morpheBytecodePatch(
    name = "Fix video downloads",
    description = "Fixes a bug in Sync's MPD parser resulting in only the audio-track being saved.",
    default = true,
    extensionPatch = ExtensionPatches.Sync,
) { setting ->
    dependsOn(ExtensionPatches.Sync)

    compatibleWith(*AppCompatibility.SyncForReddit)

    execute {
        val scanResult = ParseRedditVideoNetworkResponseFingerprint.instructionMatches
        val newInstanceIndex = scanResult.first().index
        val invokeDirectIndex = scanResult.last().index - 1

        val buildResponseInstruction =
            ParseRedditVideoNetworkResponseFingerprint.method.getInstruction<Instruction35c>(invokeDirectIndex)

        setting.patchMethod(ParseRedditVideoNetworkResponseFingerprint) {
            addInstructions(
                newInstanceIndex + 1,
                """
                    iget-object v2, p1, Lcom/android/volley/NetworkResponse;->data:[B
                    invoke-static {v2}, $EXTENSION_CLASS_DESCRIPTOR->$GET_LINKS_METHOD
                    move-result-object v2

                    const/4 v5, 0x0
                    aget-object v${buildResponseInstruction.registerE}, v2, v5

                    const/4 v6, 0x1
                    aget-object v${buildResponseInstruction.registerF}, v2, v6
                """,
            )
        }
    }
}
