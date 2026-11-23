/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.baconreader.fix.video

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.COMMENT_VIDEO_LINKS
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.fixCommentVideoLinksPatch
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val BROWSER = "Lcom/onelouder/baconreader/BrowserActivity;"
private const val HANDLER = "Lcom/onelouder/baconreader/PostMediaHandler;"
private const val LISTENER = "Lcom/onelouder/baconreader/PostMediaHandler\$PostMediaListener;"
private const val LINK = "Lcom/onelouder/baconreader/reddit/Link;"
private const val BRIDGE = "morpheStartCommentVideo"

@Suppress("unused")
val fixCommentVideoLinksPatch =
    fixCommentVideoLinksPatch(ExtensionPatches.BaconReader, AppCompatibility.BaconReader) {
        val browserCreate = Fingerprint(
            definingClass = BROWSER,
            name = "onCreate",
            parameters = listOf("Landroid/os/Bundle;"),
            returnType = "V",
            strings = listOf("link", "icons", "BaconReader can't open this link now"),
        )
        browserCreate.method.apply {
            val start = indexOfFirstInstructionOrThrow {
                opcode == Opcode.INVOKE_VIRTUAL &&
                    getReference<MethodReference>()?.toString() == "$HANDLER->start()V"
            }
            val receiver = getInstruction<Instruction35c>(start).registerC

            // Keep this call the same width and leave the handler's stock start() bytecode intact.
            replaceInstruction(start, "invoke-direct {p0, v$receiver}, $BROWSER->$BRIDGE($HANDLER)V")
        }
        browserCreate.classDef.methods.add(ImmutableMethod(
            BROWSER, BRIDGE, listOf(ImmutableMethodParameter(HANDLER, null, null)), "V",
            AccessFlags.PRIVATE.value, null, null, MutableMethodImplementation(5),
        ).toMutable().apply {
            // BrowserActivity has already built the Link and set up a fresh, unrestricted handler.
            // Use the same package-private accessors as the stock media callback.
            addInstructions(0, """
                iget-object v0, p0, $BROWSER->link:$LINK
                iget-object v1, v0, $LINK->urlOverriddenByDest:Ljava/lang/String;
                invoke-static {v1}, Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z
                move-result v2
                if-eqz v2, :morphe_resolve
                iget-object v1, v0, $LINK->url:Ljava/lang/String;
                :morphe_resolve
                invoke-static {v1}, $COMMENT_VIDEO_LINKS->dashUrl(Ljava/lang/String;)Ljava/lang/String;
                move-result-object v1
                if-eqz v1, :morphe_native
                invoke-virtual {v0}, $LINK->isVideo()Z
                move-result v2
                if-nez v2, :morphe_native
                iget-object v0, v0, $LINK->preview:Lcom/onelouder/baconreader/reddit/RedditPreview;
                if-nez v0, :morphe_native
                invoke-static {p1, v1}, $HANDLER->access${'$'}402(${HANDLER}Ljava/lang/String;)Ljava/lang/String;
                invoke-static {p1}, $HANDLER->access${'$'}300($HANDLER)$LISTENER
                move-result-object v0
                const/4 v2, 0x0
                invoke-virtual {v0, v2}, $LISTENER->onResolvedContentType(Z)V
                invoke-static {p1, p0, v1}, $HANDLER->access${'$'}2600(${HANDLER}Landroid/content/Context;Ljava/lang/String;)V
                return-void
                :morphe_native
                invoke-virtual {p1}, $HANDLER->start()V
                return-void
            """)
        })
    }
