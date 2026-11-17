/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.redditisfun.fix.links

import app.morphe.patcher.Fingerprint
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.RELATIVE_MARKDOWN_LINKS
import app.morphe.patches.reddit.customclients.fixRelativeMarkdownLinksPatch
import app.morphe.patches.reddit.customclients.redditisfun.bindings.RifNativeSku
import app.morphe.patches.reddit.customclients.redditisfun.bindings.resolveRifNativeSku
import app.morphe.patches.reddit.customclients.wrapMorpheHook

@Suppress("unused")
val fixRelativeMarkdownLinksPatch =
    // RIF strips font attributes during preprocessing; repair the original HTML only once.
    fixRelativeMarkdownLinksPatch(ExtensionPatches.RIF, AppCompatibility.RedditIsFun, frameworkRenderer = false) {
        val (owner, context) = when (resolveRifNativeSku(buildList { classDefForEach(::add) })) {
            RifNativeSku.FREE -> "Lo5/z;" to "Lb5/i;"
            RifNativeSku.PLATINUM -> "Lp5/z;" to "Lc5/i;"
        }
        Fingerprint(
            definingClass = owner,
            name = "f",
            parameters = listOf("Ljava/lang/String;", context),
            returnType = "Ljava/lang/String;",
            strings = listOf("org.ccil.cowan.tagsoup.Parser", "Error parsing HTML"),
        ).wrapMorpheHook(before = """
            invoke-static/range {p0 .. p0}, $RELATIVE_MARKDOWN_LINKS->repairHtml(Ljava/lang/String;)Ljava/lang/String;
            move-result-object p0
        """)
    }
