/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.all.misc.resources

import app.morphe.patcher.patch.resourcePatch
import app.morphe.util.ResourceGroup
import app.morphe.util.inputStreamFromBundledResource
import java.nio.file.Files

internal fun addResourcesPatch(sourceDirectory: String, vararg resources: ResourceGroup) =
    resourcePatch(default = false) {
        execute {
            for (group in resources) {
                for (name in group.resources) {
                    val path = "${group.resourceDirectoryName}/$name"
                    val source = checkNotNull(inputStreamFromBundledResource(sourceDirectory, path)) {
                        "Bundled resource was not found: $sourceDirectory/$path"
                    }
                    source.use { input ->
                        // Track each file so on-demand decoding registers new values and ID declarations.
                        val target = this["res/$path", false]
                        Files.createDirectories(target.parentFile.toPath())
                        target.outputStream().use(input::copyTo)
                    }
                }
            }
        }
    }
