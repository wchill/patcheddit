/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.redditisfun.fix.youtube

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.classDefForEach
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.YOUTUBE_PLAYER_ACTIVITY_DESCRIPTOR
import app.morphe.patches.reddit.customclients.redditisfun.bindings.RifNativeSku
import app.morphe.patches.reddit.customclients.redditisfun.bindings.resolveRifNativeSku
import app.morphe.patches.reddit.customclients.youtubePlayerPatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

val fixRifYouTubePlaybackPatch = patch(
    name = "Fix embedded YouTube playback",
    description = "Updates the in-app YouTube player without changing your external link settings.",
) {
    category(PatchCategory.INTERFACE)
    compatibleWith(*AppCompatibility.RedditIsFun)
    dependsOn(youtubePlayerPatch)

    execute {
        val definitions = buildList { classDefForEach(::add) }
        val (owner, whitelist, source) = when (resolveRifNativeSku(definitions)) {
            RifNativeSku.FREE -> Triple("Ll3/g;", "Lcom/andrewshu/android/reddit/ads/WhitelistStatus;", "Lu1/b;")
            RifNativeSku.PLATINUM -> Triple("Lm3/g;", "Lt1/f;", "Lv1/b;")
        }
        val dispatcher = Fingerprint(
            definingClass = owner,
            name = "r",
            accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
            parameters = listOf("Landroid/net/Uri;", "Ljava/lang/String;", "Ljava/lang/String;",
                "Ljava/lang/String;", "Z", whitelist, "Landroidx/fragment/app/FragmentManager;",
                "Landroid/content/Context;", source),
            returnType = "V",
            strings = listOf("com.andrewshu.android.reddit.EXTRA_BROWSER_DISPLAY_URL"),
        ).method
        require(checkNotNull(dispatcher.implementation).registerCount >= 11) {
            "The RIF embedded link dispatcher requires two local registers."
        }
        val applicationContext = dispatcher.getInstruction(1).getReference<MethodReference>()
        require(dispatcher.getInstruction(0).opcode == Opcode.IF_NEZ &&
                dispatcher.getInstruction(1).opcode == Opcode.INVOKE_STATIC &&
                applicationContext?.definingClass == "Lcom/andrewshu/android/reddit/RedditIsFunApplication;" &&
                applicationContext.parameterTypes.isEmpty() &&
                applicationContext.returnType == "Landroid/content/Context;") {
            "Couldn't resolve RIF's native fallback context for in-app links."
        }
        dispatcher.addInstructionsWithLabels(
            0,
            """
                move-object/from16 v0, p7
                if-nez v0, :has_context
                invoke-static {}, $applicationContext
                move-result-object v0
                :has_context
                move-object/from16 v1, p0
                invoke-static {v0, v1}, $YOUTUBE_PLAYER_ACTIVITY_DESCRIPTOR->openEmbedded(Landroid/content/Context;Landroid/net/Uri;)Z
                move-result v0
                if-eqz v0, :original
                return-void
            """,
            ExternalLabel("original", dispatcher.getInstruction(0)),
        )
    }
}
