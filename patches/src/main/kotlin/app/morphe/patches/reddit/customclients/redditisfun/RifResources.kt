/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.redditisfun

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.reddit.customclients.mergeMorpheResources
import app.morphe.util.inputStreamFromBundledResource

internal val rifFeatureResources = resourcePatch(default = false) {
    execute {
        checkNotNull(inputStreamFromBundledResource("redditisfun", "values/patcheddit_features.xml")).use { input ->
            document(input).use { source ->
                document("res/values/strings.xml").use { target -> mergeMorpheResources(target, source, "string") }
            }
        }
    }
}
