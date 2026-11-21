/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.links

import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.fixRelativeMarkdownLinksPatch

@Suppress("unused")
val fixRelativeMarkdownLinksPatch =
    fixRelativeMarkdownLinksPatch(ExtensionPatches.Boost, AppCompatibility.Boost)
