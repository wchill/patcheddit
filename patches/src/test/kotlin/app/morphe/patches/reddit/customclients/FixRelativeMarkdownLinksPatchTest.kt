/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients

import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction35c
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction3rc
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rc
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FixRelativeMarkdownLinksPatchTest {
    @Test
    fun preservesNormalAndRangeInvocationRegistersAndEveryNativeHtmlArgument() {
        val parameters = listOf("Ljava/lang/String;", "I", "Landroid/text/Html\$ImageGetter;",
            "Landroid/text/Html\$TagHandler;")
        val reference = ImmutableMethodReference("Landroid/text/Html;", "fromHtml", parameters, "Landroid/text/Spanned;")
        val normal = rewriteMarkdownHtmlInvocation(
            BuilderInstruction35c(Opcode.INVOKE_STATIC, 4, 8, 3, 5, 1, 0, reference),
        )
        assertNotNull(normal)
        assertIs<Instruction35c>(normal)
        assertEquals(listOf(8, 3, 5, 1), listOf(normal.registerC, normal.registerD, normal.registerE, normal.registerF))
        val range = rewriteMarkdownHtmlInvocation(BuilderInstruction3rc(Opcode.INVOKE_STATIC_RANGE, 270, 4, reference))
        assertNotNull(range)
        assertIs<Instruction3rc>(range)
        assertEquals(270, range.startRegister)
        assertEquals(4, range.registerCount)
        assertEquals(
            "Lapp/morphe/extension/shared/fixes/links/RelativeMarkdownLinksHtml;->fromHtml(${parameters.joinToString("")})Landroid/text/Spanned;",
            range.reference.toString(),
        )
    }

    @Test
    fun ignoresUnrelatedHtmlAndNonStaticOrUnsupportedSignatures() {
        for ((owner, name, returns) in listOf(
            Triple("Lnative/Html;", "fromHtml", "Landroid/text/Spanned;"),
            Triple("Landroid/text/Html;", "toHtml", "Ljava/lang/String;"),
            Triple("Landroid/text/Html;", "fromHtml", "Ljava/lang/String;"),
        )) {
            val reference = ImmutableMethodReference(owner, name, listOf("Ljava/lang/String;"), returns)
            assertNull(rewriteMarkdownHtmlInvocation(BuilderInstruction35c(Opcode.INVOKE_STATIC, 1, 0, 0, 0, 0, 0, reference)))
        }
        val reference = ImmutableMethodReference("Landroid/text/Html;", "fromHtml",
            listOf("Ljava/lang/String;"), "Landroid/text/Spanned;")
        assertNull(rewriteMarkdownHtmlInvocation(BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 1, 0, 0, 0, 0, 0, reference)))
    }
}
