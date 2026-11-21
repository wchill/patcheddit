/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.imagesearch

import app.morphe.patches.all.misc.PatchCategory
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.boostforreddit.http.links.boostLinkMenuPatch
import app.morphe.patches.reddit.customclients.morpheBytecodePatch

@Suppress("unused")
val imageSearchPatch = morpheBytecodePatch(
    name = "Fix image search",
    description = "Updates image search engines in the link menu, including Google Lens.",
    extensionPatch = ExtensionPatches.Boost,
    category = PatchCategory.MEDIA,
) { setting ->
    dependsOn(boostLinkMenuPatch)
    compatibleWith(*AppCompatibility.Boost)
    execute {
        setting.returnEnabled(mutableClassDefBy("Lapp/morphe/extension/boost/utils/ImageSearchMenu;")
            .methods.single { it.name == "isEnabled" })
    }
}
