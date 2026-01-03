package app.morphe.patches.reddit.customclients.boostforreddit.fix.downloads

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.boostforreddit.http.interceptHttpRequests
import app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.sharedExtensionPatch
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.util.indexOfFirstInstructionReversedOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/revanced/extension/boostforreddit/http/reddit/RedditFixAudioInDownloadsInterceptor;"

@Suppress("unused")
val fixAudioMissingInDownloadsPatch = bytecodePatch(
    name = "Fix missing audio in video downloads",
    description = "Fixes audio missing in videos downloaded from v.redd.it.",
) {
    dependsOn(sharedExtensionPatch, interceptHttpRequests)
    compatibleWith("com.rubenmayayo.reddit")

    execute {
        downloadAudioFingerprint.method.apply {
            // Just need to set an enable flag since HTTP interceptor will already be enabled
            addInstructions(
                0,
                """
                    invoke-static {}, $EXTENSION_CLASS_DESCRIPTOR->enable()V
                    """
            )
        }
    }
}
