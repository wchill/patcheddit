/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.profileabout

import app.morphe.patches.reddit.customclients.morpheResourcePatch
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import org.w3c.dom.Element

// Boost 1.14.2 applies these attributes natively; keep the patch opt-in.
@Suppress("unused")
val fixProfileAboutScrollingPatch = morpheResourcePatch(
    name = "Fix profile About scrolling",
    description = "Fixes the first swipe stopping early on profile About tabs without disabling horizontal scrolling.",
    default = false,
    extensionPatch = ExtensionPatches.Boost,
    category = PatchCategory.INTERFACE,
) {
    compatibleWith(*AppCompatibility.Boost)

    execute {
        document("res/layout/fragment_user_info.xml").use { document ->
            require(document.documentElement.tagName == "androidx.core.widget.NestedScrollView") {
                "Unexpected Boost profile About scroll container"
            }
            val expectedIds = setOf(
                "user_info_trophies_recyclerview",
                "user_info_top_karma_recyclerview",
                "user_info_moderated_recyclerview",
                "user_info_multis_recyclerview",
            )
            val nodes = document.getElementsByTagName("androidx.recyclerview.widget.RecyclerView")
            val lists = (0 until nodes.length).map { nodes.item(it) as Element }
            require(lists.size == expectedIds.size &&
                lists.map { it.getAttribute("android:id").substringAfterLast('/') }.toSet() == expectedIds
            ) {
                "Expected Boost's four profile About lists"
            }
            // Inner lists must not replace the outer view's AppBarLayout nested-scroll session.
            lists.forEach { it.setAttribute("android:nestedScrollingEnabled", "false") }
        }
    }
}
