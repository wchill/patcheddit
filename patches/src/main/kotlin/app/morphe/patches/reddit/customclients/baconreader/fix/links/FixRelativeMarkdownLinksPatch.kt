/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.baconreader.fix.links

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.RELATIVE_MARKDOWN_LINKS
import app.morphe.patches.reddit.customclients.fixRelativeMarkdownLinksPatch
import com.android.tools.smali.dexlib2.Opcode

@Suppress("unused")
val fixRelativeMarkdownLinksPatch =
    fixRelativeMarkdownLinksPatch(ExtensionPatches.BaconReader, AppCompatibility.BaconReader) {
        Fingerprint(
            definingClass = "Lcom/onelouder/baconreader/parser/RedditParser;",
            name = "<init>",
            parameters = listOf("Ljava/lang/String;"),
            returnType = "V",
        ).method.apply {
            check(implementation?.instructions?.map { it.opcode } ==
                    listOf(Opcode.INVOKE_DIRECT, Opcode.IPUT_OBJECT, Opcode.RETURN_VOID)) {
                "Unsupported BaconReader HTML parser constructor."
            }
            addInstructions(1, """
                invoke-static/range {p1 .. p1}, $RELATIVE_MARKDOWN_LINKS->repairHtml(Ljava/lang/String;)Ljava/lang/String;
                move-result-object p1
            """)
        }
    }
