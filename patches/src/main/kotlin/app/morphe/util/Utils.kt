package app.morphe.util

import java.net.URLDecoder
import java.util.jar.JarFile

internal object Utils {
    internal fun String.trimIndentMultiline() =
        this.split("\n")
            .joinToString("\n") { it.trimIndent() } // Remove the leading whitespace from each line.
            .trimIndent() // Remove the leading newline.
}

internal fun Boolean.toHexString(): String = if (this) "0x1" else "0x0"

/**
 * @return The file path for the jar this classfile is contained inside.
 */
fun getCurrentJarFilePath(): String {
    val className = object {}::class.java.enclosingClass.name.replace('.', '/') + ".class"
    val classUrl = object {}::class.java.classLoader?.getResource(className)
    if (classUrl != null) {
        val urlString = classUrl.toString()

        if (urlString.startsWith("jar:file:")) {
            val end = urlString.lastIndexOf('!')

            return URLDecoder.decode(urlString.substring("jar:file:".length, end), "UTF-8")
        }
    }
    throw IllegalStateException("Not running from inside a JAR file.")
}

/**
 * @return The value for the manifest entry,
 *         or "Unknown" if the entry does not exist or is blank.
 */
@Suppress("SameParameterValue")
fun getPatchesManifestEntry(attributeKey: String) = JarFile(getCurrentJarFilePath()).use { jarFile ->
    jarFile.manifest.mainAttributes.entries.firstOrNull { it.key.toString() == attributeKey }?.value?.toString()
        ?: "Unknown"
}

fun getPatchesBundleVersion(): String = getPatchesManifestEntry("Version")
