package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.user

import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import app.morphe.patcher.util.getReference
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

@Suppress("unused")
val useUserEndpointPatch = morpheBytecodePatch(
    name = "Use /user/ endpoint",
    description = "Replaces the deprecated endpoint for viewing user profiles /u with /user, that used to fix a bug.",
    default = true,
    extensionPatch = ExtensionPatches.Sync,
) { setting ->
    compatibleWith(*AppCompatibility.SyncForReddit)

    execute {
        userEndpointFingerprints.forEach { fingerprint ->
            fingerprint.matchAll().forEach { match ->
                val stringIndex = match.stringMatches.first().index

                val userPathStringInstruction = match.method.getInstruction<OneRegisterInstruction>(stringIndex)

                val fixedUserPathString = userPathStringInstruction.getReference<StringReference>()!!
                    .string.replace("u/", "user/")

                setting.replaceString(match.method, stringIndex, fixedUserPathString)
            }
        }
    }
}
