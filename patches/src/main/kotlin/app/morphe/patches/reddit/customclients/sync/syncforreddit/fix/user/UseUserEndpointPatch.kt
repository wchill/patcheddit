package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.user

import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.patch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patcher.util.getReference
import com.android.tools.smali.dexlib2.formatter.DexFormatter
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

@Suppress("unused")
val useUserEndpointPatch = patch(
    description = "Replaces the deprecated endpoint for viewing user profiles /u with /user, that used to fix a bug.",
    default = true,
) {
    dependsOn(ExtensionPatches.Sync)
    compatibleWith(*AppCompatibility.SyncForReddit)

    execute {
        userEndpointFingerprints.forEach { fingerprint ->
            fingerprint.matchAll().forEach { match ->
                val stringIndex = match.stringMatches.first().index

                val userPathStringInstruction = match.method.getInstruction<OneRegisterInstruction>(stringIndex)

                val fixedUserPathString = userPathStringInstruction.getReference<StringReference>()!!
                    .string.replace("u/", "user/")

                match.method.replaceInstruction(
                    stringIndex,
                    "const-string v${userPathStringInstruction.registerA}, ${DexFormatter.INSTANCE.getQuotedString(fixedUserPathString)}",
                )
            }
        }
    }
}
