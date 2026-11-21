/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.inlinemedia

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.boostforreddit.bindings.BoostApi
import app.morphe.patches.reddit.customclients.boostforreddit.wrapHook
import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val INLINE = "Lapp/morphe/extension/boost/media/InlineMedia;"

@Suppress("unused")
val loadAllInlineMediaPatch = morpheBytecodePatch(
    name = "Load all inline media",
    description = "Displays every linked image or GIF immediately after its link in comments and text posts.",
    extensionPatch = ExtensionPatches.Boost,
    category = PatchCategory.MEDIA,
    optionsResource = "morphe_boost_inline_media",
    resourceDirectory = "boost",
) {
    compatibleWith(*AppCompatibility.Boost)
    execute {
        for (binding in listOf(BoostApi.TableTextView.setTextHtml, BoostApi.TableTextView.clearTextExtras)) {
            binding.fingerprint().wrapHook(before = """
                invoke-static {p0}, $INLINE->prepare(Landroid/widget/LinearLayout;)V
            """)
        }
        val comments = BoostApi.CommentViewHolder.bind.fingerprint()
        val native = comments.classDef.methods.single { method ->
            method.parameterTypes == comments.method.parameterTypes && method.implementation?.instructions?.any {
                ((it as? ReferenceInstruction)?.reference as? StringReference)?.string == "pref_comments_inline_media"
            } == true
        }
        val preference = native.implementation!!.instructions.indexOfFirst {
            ((it as? ReferenceInstruction)?.reference as? StringReference)?.string == "pref_comments_inline_media"
        }
        val result = native.getInstruction<OneRegisterInstruction>(preference + 2)
        require(result.opcode == Opcode.MOVE_RESULT) { "Unsupported Boost inline-media preference gate" }
        // Bypass only the old single-preview path; keep the user's native inline-media preference.
        native.addInstructions(preference + 3, """
            invoke-static {v${result.registerA}}, $INLINE->useNativePreview(Z)Z
            move-result v${result.registerA}
        """)
        for ((binding, body) in listOf(
            BoostApi.CommentViewHolder.bind to BoostApi.CommentViewHolder.commentTv,
            BoostApi.SubmissionViewHolder.bind to BoostApi.SubmissionViewHolder.selfTv,
        )) binding.fingerprint().wrapHook(after = """
            iget-object v0, p0, $body
            invoke-static {v0, p5}, $INLINE->bind(Landroid/widget/LinearLayout;Ljava/lang/Object;)V
        """)
    }
}
