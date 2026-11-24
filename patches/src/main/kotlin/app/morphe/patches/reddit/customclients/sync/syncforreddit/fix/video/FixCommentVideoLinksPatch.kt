/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.video

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.COMMENT_VIDEO_LINKS
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.fixCommentVideoLinksPatch
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val VIEWER = "Lcom/laurencedawson/reddit_sync/ui/fragments/ImageViewerFragment;"
private val openRedditVideoParameters = listOf(
    "Landroid/content/Context;",
    "[Lr0/d;",
    "Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/String;",
    "Z", "Z",
)

@Suppress("unused")
val fixCommentVideoLinksPatch =
    fixCommentVideoLinksPatch(ExtensionPatches.Sync, AppCompatibility.SyncForReddit) {
        val openRedditVideo = Fingerprint(
            accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
            parameters = openRedditVideoParameters,
            returnType = "V",
            strings = listOf("Opening reddit video link internally: ", "Opening reddit video link externally: "),
        ).method
        Fingerprint(
            accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
            parameters = openRedditVideoParameters + "Z",
            returnType = "Z",
            strings = listOf("Link title: ", "Opening strange reddit video link"),
        ).method.apply {
            val routeIndex = indexOfFirstInstructionOrThrow {
                getReference<StringReference>()?.string == "reddit.com/link/"
            }
            val scratch = "v${getInstruction<OneRegisterInstruction>(routeIndex).registerA}"

            // This branch runs after the /s/ entry hook. Keep the original link for the native
            // external-browser fallback and forward all of Sync's native video launch arguments.
            addInstructionsAtControlFlowLabel(routeIndex, """
                invoke-static/range {p3 .. p3}, $COMMENT_VIDEO_LINKS->baseUrl(Ljava/lang/String;)Ljava/lang/String;
                move-result-object $scratch
                if-eqz $scratch, :morphe_native
                invoke-static/range {p0 .. p8}, $openRedditVideo
                const/4 $scratch, 0x1
                return $scratch
                :morphe_native
                nop
            """)
        }

        Fingerprint(
            definingClass = VIEWER,
            parameters = listOf("Ljava/lang/String;"),
            returnType = "V",
            strings = listOf("mediadownloads.mlb.com", "Detected reddit streaming video"),
        ).method.apply {
            val classify = indexOfFirstInstructionOrThrow {
                getReference<StringReference>()?.string == "mediadownloads.mlb.com"
            }
            val play = indexOfFirstInstructionOrThrow {
                getReference<StringReference>()?.string == "Detected reddit streaming video"
            }
            val scratch = "v${getInstruction<OneRegisterInstruction>(classify).registerA}"

            // Sync's ordinary Reddit URL parser discards the parent path. Supply the full manifest
            // at its native HLS playback branch, leaving the ordinary v.redd.it case unchanged.
            addInstructionsWithLabels(classify, """
                invoke-static/range {p1 .. p1}, $COMMENT_VIDEO_LINKS->hlsUrl(Ljava/lang/String;)Ljava/lang/String;
                move-result-object $scratch
                if-eqz $scratch, :morphe_native
                move-object/16 p1, $scratch
                goto/16 :morphe_play
            """, ExternalLabel("morphe_native", getInstruction(classify)),
                ExternalLabel("morphe_play", getInstruction(play)))
        }
    }
