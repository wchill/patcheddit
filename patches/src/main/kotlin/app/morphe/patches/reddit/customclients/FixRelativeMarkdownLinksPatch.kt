/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.Patch
import app.morphe.patches.all.misc.PatchCategory
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderInstruction
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction35c
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction3rc
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rc
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference

internal const val RELATIVE_MARKDOWN_LINKS =
    "Lapp/morphe/extension/shared/fixes/links/RelativeMarkdownLinks;"
private const val HTML_LINKS =
    "Lapp/morphe/extension/shared/fixes/links/RelativeMarkdownLinksHtml;"
private const val STRING = "Ljava/lang/String;"

internal fun fixRelativeMarkdownLinksPatch(
    extensionPatch: Patch<*>,
    compatible: Array<Compatibility>,
    frameworkRenderer: Boolean = true,
    additional: BytecodePatchContext.() -> Unit = {},
) = morpheBytecodePatch(
    name = "Fix broken markdown links",
    description = "Repairs malformed user and subreddit links while preserving code and ordinary links.",
    extensionPatch = extensionPatch,
    category = PatchCategory.LINKS,
) { setting ->
    compatibleWith(*compatible)
    execute {
        setting.guardHook(RELATIVE_MARKDOWN_LINKS, "repairHtml", identity = true)
        additional()
    }
    // Let other features finish locating and modifying their native HTML conversions first.
    finalize {
        if (!frameworkRenderer) return@finalize
        var count = 0
        for (definition in buildList { classDefForEach(::add) }) {
            if (definition.type.startsWith("Lapp/morphe/extension/")) continue
            for (method in definition.methods) {
                val replacements = method.implementation?.instructions?.mapIndexedNotNull { index, instruction ->
                    rewriteMarkdownHtmlInvocation(instruction)?.let { index to it }
                }.orEmpty()
                if (replacements.isEmpty()) continue
                val target = currentMethod(mutableClassDefBy(definition), method)
                for ((index, replacement) in replacements) target.replaceInstruction(index, replacement)
                count += replacements.size
            }
        }
        check(count > 0) { "No supported native HTML renderer was found for Markdown link repair." }
    }
}

internal fun rewriteMarkdownHtmlInvocation(instruction: Instruction): BuilderInstruction? {
    val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference ?: return null
    val parameters = reference.parameterTypes.map(CharSequence::toString)
    val images = "Landroid/text/Html\$ImageGetter;"
    val tags = "Landroid/text/Html\$TagHandler;"
    if (reference.definingClass != "Landroid/text/Html;" || reference.name != "fromHtml"
        || reference.returnType != "Landroid/text/Spanned;"
        || parameters !in listOf(listOf(STRING), listOf(STRING, "I"),
            listOf(STRING, images, tags), listOf(STRING, "I", images, tags))) return null
    val target = ImmutableMethodReference(HTML_LINKS, "fromHtml", parameters, reference.returnType)
    return when {
        instruction.opcode == Opcode.INVOKE_STATIC && instruction is Instruction35c -> BuilderInstruction35c(
            Opcode.INVOKE_STATIC, instruction.registerCount,
            instruction.registerC, instruction.registerD, instruction.registerE,
            instruction.registerF, instruction.registerG, target,
        )
        instruction.opcode == Opcode.INVOKE_STATIC_RANGE && instruction is Instruction3rc -> BuilderInstruction3rc(
            Opcode.INVOKE_STATIC_RANGE, instruction.startRegister, instruction.registerCount, target,
        )
        else -> null
    }
}
