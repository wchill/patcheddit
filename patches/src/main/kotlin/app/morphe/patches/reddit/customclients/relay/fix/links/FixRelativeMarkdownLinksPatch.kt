/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.relay.fix.links

import app.morphe.patcher.Fingerprint
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.RELATIVE_MARKDOWN_LINKS
import app.morphe.patches.reddit.customclients.fixRelativeMarkdownLinksPatch
import app.morphe.patches.reddit.customclients.wrapMorpheHook

@Suppress("unused")
val fixRelativeMarkdownLinksPatch =
    fixRelativeMarkdownLinksPatch(ExtensionPatches.Relay, AppCompatibility.Relay) {
        Fingerprint(
            definingClass = "Lreddit/news/utils/RedditUtils;",
            name = "j",
            parameters = listOf("Ljava/lang/String;", "Z", "Ljava/lang/String;"),
            returnType = "Landroid/text/Spanned;",
        ).wrapMorpheHook(before = """
            invoke-static/range {p0 .. p0}, $RELATIVE_MARKDOWN_LINKS->repairHtml(Ljava/lang/String;)Ljava/lang/String;
            move-result-object p0
        """)
    }
