/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.youtube

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.classDefForEach
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.YOUTUBE_PLAYER_ACTIVITY_DESCRIPTOR
import app.morphe.patches.reddit.customclients.youtubePlayerPatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val SYNC_ORIGINAL =
    "Lcom/laurencedawson/reddit_sync/ui/activities/CustomYouTubeVideoActivity;"

val fixSyncYouTubePlaybackPatch = patch(
    name = "Fix embedded YouTube playback",
    description = "Plays YouTube links inside the app without relying on the retired Android YouTube player.",
) {
    category(PatchCategory.INTERFACE)
    compatibleWith(*AppCompatibility.SyncForReddit)
    dependsOn(youtubePlayerPatch)

    execute {
        val classifier = Fingerprint(
            accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
            parameters = listOf("Ljava/lang/String;"),
            returnType = "Z",
            strings = listOf("/shorts/", "youtube.com", "youtu.be", "/clip/"),
        ).method
        require(checkNotNull(classifier.implementation).registerCount >= 2) {
            "Sync's YouTube link classifier requires one local register."
        }
        // Recognize Shorts before Sync's explicit path exclusion. Keep its native playback preference.
        classifier.addInstructionsWithLabels(
            0,
            """
                invoke-static/range {p0 .. p0}, $YOUTUBE_PLAYER_ACTIVITY_DESCRIPTOR->isShortsLink(Ljava/lang/String;)Z
                move-result v0
                if-eqz v0, :native
                return v0
            """,
            ExternalLabel("native", classifier.getInstruction(0)),
        )

        val routes = buildList {
            classDefForEach { owner ->
                for (method in owner.methods) {
                    method.implementation?.instructions?.forEachIndexed { index, instruction ->
                        if (instruction.opcode == Opcode.CONST_CLASS &&
                            instruction.getReference<TypeReference>()?.type == SYNC_ORIGINAL
                        ) {
                            add(Triple(method, index, (instruction as OneRegisterInstruction).registerA))
                        }
                    }
                }
            }
        }
        require(routes.size == 2) { "Expected two native Sync YouTube activity launch sites." }
        for ((method, index, register) in routes) {
            Fingerprint(
                definingClass = method.definingClass,
                name = method.name,
                parameters = method.parameterTypes.map(CharSequence::toString),
                returnType = method.returnType,
            ).method.replaceInstruction(index, "const-class v$register, $YOUTUBE_PLAYER_ACTIVITY_DESCRIPTOR")
        }
    }
}
