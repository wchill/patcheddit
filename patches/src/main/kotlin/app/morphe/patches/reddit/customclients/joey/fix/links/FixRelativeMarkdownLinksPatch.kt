/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.joey.fix.links

import app.morphe.patcher.Fingerprint
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.RELATIVE_MARKDOWN_LINKS
import app.morphe.patches.reddit.customclients.fixRelativeMarkdownLinksPatch
import app.morphe.patches.reddit.customclients.wrapMorpheHook

@Suppress("unused")
val fixRelativeMarkdownLinksPatch =
    fixRelativeMarkdownLinksPatch(ExtensionPatches.Joey, AppCompatibility.JoeyForReddit) {
        Fingerprint(
            definingClass = "Lo/o/joey/CustomViews/HtmlDispaly;",
            name = "setTextHtml",
            parameters = listOf("Ljava/lang/String;", "Lo/o/joey/CustomViews/HtmlDispaly\$g;"),
            returnType = "V",
        ).wrapMorpheHook(before = """
            invoke-static/range {p1 .. p1}, $RELATIVE_MARKDOWN_LINKS->repairHtml(Ljava/lang/String;)Ljava/lang/String;
            move-result-object p1
        """)
    }
