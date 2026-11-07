/*
 * Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.codeblock

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.patcher.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val CODEBLOCK_NORMALIZER_DESCRIPTOR =
    "Lapp/morphe/extension/boost/codeblock/CodeBlockHtmlNormalizer;"

@Suppress("unused")
val fixCodeBlockRenderingPatch = morpheBytecodePatch(
    name = "Fix Boost code block rendering",
    description = "Fixes fenced code and parenthesized links in feed previews that bypass Boost's HTML preprocessing.",
    default = true,
    extensionPatch = ExtensionPatches.Boost,
    category = PatchCategory.COMMENTS,
) { setting ->
    dependsOn(ExtensionPatches.Boost)
    compatibleWith(*AppCompatibility.Boost)

    // TODO: Render all text bodies through Snudown or another Markdown parser to avoid dealing with inconsistent Reddit-provided HTML.
    execute {
        setting.guardHook(CODEBLOCK_NORMALIZER_DESCRIPTOR, "normalize", identity = true)
        // Unlike LinkTextView's parsed entry point, feed previews bypass RedditParser.
        // Other patches can wrap the binder, so locate the native conversion body.
        boostSubmissionViewHolderSelfPreviewHtmlFingerprint.classDef.methods.single { candidate ->
            candidate.parameters.map { it.type } ==
                boostSubmissionViewHolderSelfPreviewHtmlFingerprint.method.parameters.map { it.type } &&
                candidate.implementation?.instructions?.any {
                    it.getReference<MethodReference>()?.toString() ==
                        "Landroid/text/Html;->fromHtml(Ljava/lang/String;)Landroid/text/Spanned;"
                } == true
        }.apply {
            val htmlIndex = indexOfFirstInstructionOrThrow {
                getReference<MethodReference>()?.toString() ==
                    "Landroid/text/Html;->fromHtml(Ljava/lang/String;)Landroid/text/Spanned;"
            }
            val htmlRegister = getInstruction<FiveRegisterInstruction>(htmlIndex).registerC
            addInstructions(
                htmlIndex,
                """
                    invoke-static {v$htmlRegister}, $CODEBLOCK_NORMALIZER_DESCRIPTOR->normalize(Ljava/lang/String;)Ljava/lang/String;
                    move-result-object v$htmlRegister
                """,
            )
        }
    }
}
