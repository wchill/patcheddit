/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.misc.extension

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.all.misc.extension.SHARED_UTILS_EXTENSION_CLASS
import app.morphe.patches.all.misc.extension.activityOnCreateExtensionHook
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore
import com.android.tools.smali.dexlib2.writer.pool.DexPool
import app.morphe.util.returnEarly
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.JarURLConnection

internal fun clientExtensionPatch(
    extensionName: String,
    hookedClasses: Array<out String>,
    prepareShared: (InputStream) -> InputStream = ::withoutHttpLibraries,
) = bytecodePatch(default = false) {
    extendWith {
        val input = checkNotNull(object {}.javaClass.classLoader.getResourceAsStream("extensions/shared.mpe")) {
            "Shared extension payload was not found"
        }
        prepareShared(input)
    }
    extendWith("extensions/$extensionName.mpe")
    execute {
        classDefBy(SHARED_UTILS_EXTENSION_CLASS)
    }
    finalize {
        for (type in hookedClasses) {
            activityOnCreateExtensionHook(type)(SHARED_UTILS_EXTENSION_CLASS)
        }
        setExtensionReleaseVersion()
    }
}

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

private fun withoutHttpLibraries(input: InputStream): InputStream {
    val dex = input.use { DexBackedDexFile.fromInputStream(null, it.buffered()) }
    val pool = DexPool(dex.opcodes)
    dex.classes.filterNot {
        it.type.startsWith("Lokhttp3/") || it.type.startsWith("Lokio/")
    }.forEach(pool::internClass)
    val output = MemoryDataStore()
    pool.writeTo(output)
    return ByteArrayInputStream(output.data)
}
