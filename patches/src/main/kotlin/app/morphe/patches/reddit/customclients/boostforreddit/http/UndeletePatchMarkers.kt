/*
 * Copyright 2026 brealorg.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.http

import app.morphe.patcher.patch.resourcePatch

// Retain inclusion markers for native integrations. Morphe menu entries are now registered
// directly by the selected patches.
internal const val REDDIT_UNDELETE_APPLIED_RESOURCE = "morphe_boost_undelete_reddit_applied"
internal const val IMGUR_UNDELETE_APPLIED_RESOURCE = "morphe_boost_undelete_imgur_applied"

internal fun undeleteAppliedMarkerPatch(resourceName: String) = resourcePatch {
    execute {
        document("res/values/strings.xml").use { document ->
            val resources = document.documentElement
            resources.appendChild(document.createElement("string").apply {
                setAttribute("name", resourceName)
                textContent = "true"
            })
        }
    }
}
