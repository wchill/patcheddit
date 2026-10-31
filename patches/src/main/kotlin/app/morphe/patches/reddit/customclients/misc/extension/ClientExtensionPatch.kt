/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.misc.extension

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patches.all.misc.extension.SHARED_UTILS_EXTENSION_CLASS
import app.morphe.util.returnEarly
import java.net.JarURLConnection

internal fun BytecodePatchContext.setExtensionReleaseVersion() {
    val marker = object {}.javaClass
    val resource = checkNotNull(marker.classLoader.getResource(marker.name.replace('.', '/') + ".class")) {
        "Cannot locate the patch bundle for its version"
    }
    val connection = resource.openConnection()
    require(connection is JarURLConnection) { "Patches are not running from a patch bundle" }
    val version = checkNotNull(connection.manifest.mainAttributes.getValue("Version")) {
        "Patch bundle has no version"
    }
    Fingerprint(
        definingClass = SHARED_UTILS_EXTENSION_CLASS,
        name = "getPatchesReleaseVersion",
        parameters = emptyList(),
        returnType = "Ljava/lang/String;",
    ).method.returnEarly(version)
}
