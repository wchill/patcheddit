/*
 * Copyright 2026 brealorg.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.homefab

import app.morphe.patches.reddit.customclients.morpheResourcePatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import org.w3c.dom.Document
import org.w3c.dom.Element
import app.morphe.patches.all.misc.PatchCategory

private const val MAIN_CONTENT_LAYOUT =
    "res/layout/content_main.xml"

private const val INBOX_LAYOUT =
    "res/layout/activity_inbox.xml"

private const val USER_LAYOUT =
    "res/layout/activity_user.xml"

private const val MATERIAL_FAB =
    "com.google.android.material.floatingactionbutton.FloatingActionButton"

private const val INCLUDED_FAB_BOTTOM_MARGIN =
    "0.0dp"

private const val DIRECT_FAB_BOTTOM_MARGIN =
    "@dimen/fab_margin"

private const val FAB_SIDE_MARGIN =
    "@dimen/fab_margin"

private data class IncludedFab(
    val layout: String,
    val gravity: String,
)

/*
 * Only includes whose root has no layout_behavior. Sizing an <include> makes
 * LayoutInflater build LayoutParams from the include alone, which would drop
 * fab_random's ScrollAwareFABBehaviorBottomNavigation and native margin.
 */
private val MAIN_INCLUDED_FABS =
    listOf(
        IncludedFab(
            layout = "@layout/fab_subreddit",
            gravity = "end|bottom",
        ),
    )

private val DIRECT_FAB_IDS =
    setOf(
        "@id/fab",
        "@id/fab_submit",
    )

private fun Document.raiseIncludedFabs(
    layoutName: String,
    targets: List<IncludedFab>,
) {
    val includeNodes = getElementsByTagName("include")
    val includes =
        (0 until includeNodes.length)
            .mapNotNull { includeNodes.item(it) as? Element }

    targets.forEach { target ->
        val matches =
            includes.filter {
                it.getAttribute("layout") == target.layout
            }

        require(matches.size == 1) {
            "Expected one ${target.layout} include in $layoutName, " +
                "found ${matches.size}"
        }

        matches.single().apply {
            /*
             * Native CoordinatorLayout already reserves the navigation height.
             * No extra include margin is needed for the native 16dp
             * FAB separation.
             */
            setAttribute(
                "android:layout_width",
                "wrap_content",
            )
            setAttribute(
                "android:layout_height",
                "wrap_content",
            )
            setAttribute(
                "android:layout_gravity",
                target.gravity,
            )
            setAttribute(
                "android:layout_marginBottom",
                INCLUDED_FAB_BOTTOM_MARGIN,
            )
        }
    }
}

private fun Document.raiseDirectFabs(
    layoutName: String,
) {
    val fabNodes = getElementsByTagName(MATERIAL_FAB)
    val matches =
        (0 until fabNodes.length)
            .mapNotNull { fabNodes.item(it) as? Element }
            .filter {
                it.getAttribute("android:id") in DIRECT_FAB_IDS
            }

    require(matches.size == DIRECT_FAB_IDS.size) {
        "Expected ${DIRECT_FAB_IDS.size} navigation FABs in $layoutName, " +
            "found ${matches.size}"
    }

    require(
        matches.map {
            it.getAttribute("android:id")
        }.toSet() == DIRECT_FAB_IDS
    ) {
        "Unexpected navigation FAB ids in $layoutName"
    }

    matches.forEach { fab ->
        require(
            fab.getAttribute("android:layout_margin") ==
                FAB_SIDE_MARGIN
        ) {
            "Unexpected native FAB margin for " +
                "${fab.getAttribute("android:id")} in $layoutName"
        }

        /*
         * MarginLayoutParams gives the generic layout_margin precedence over
         * directional margins. Expand it before restoring the native bottom
         * margin without adding the removed decor-navigation height.
         */
        fab.removeAttribute(
            "android:layout_margin",
        )
        listOf(
            "android:layout_marginLeft",
            "android:layout_marginTop",
            "android:layout_marginRight",
            "android:layout_marginStart",
            "android:layout_marginEnd",
        ).forEach { marginAttribute ->
            fab.setAttribute(
                marginAttribute,
                FAB_SIDE_MARGIN,
            )
        }

        fab.setAttribute(
            "android:layout_marginBottom",
            DIRECT_FAB_BOTTOM_MARGIN,
        )
    }
}

@Suppress("unused")
val fixBoostHomeFloatingActionMenuOverlapPatch = morpheResourcePatch(
    name = "Standardize Boost bottom-navigation FAB clearance",
    description =
        "Keeps the floating action buttons on Home, subreddits, Random, Inbox and Profile clear of the bottom navigation.",
    default = true,
    extensionPatch = ExtensionPatches.Boost,
    category = PatchCategory.NAVIGATION,
) {
    compatibleWith(*AppCompatibility.Boost)

    dependsOn(fixFabDependencyClearancePatch)

    execute {
        document(MAIN_CONTENT_LAYOUT).use { document ->
            document.raiseIncludedFabs(
                layoutName = MAIN_CONTENT_LAYOUT,
                targets = MAIN_INCLUDED_FABS,
            )
        }

        document(INBOX_LAYOUT).use { document ->
            document.raiseDirectFabs(INBOX_LAYOUT)
        }

        document(USER_LAYOUT).use { document ->
            document.raiseDirectFabs(USER_LAYOUT)
        }
    }
}
