/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.http

import app.morphe.patches.reddit.customclients.misc.extension.mapping.ProguardMapping
import java.io.Reader
import java.io.StringReader

internal class HttpLibraryProfile private constructor(
    val mapping: ProguardMapping,
    private val nativePrefixes: List<String>,
    private val nativeHelpers: Set<String>,
    private val callerPrefixes: List<String>,
    private val callerClasses: Set<String>,
) {
    fun isNative(type: String): Boolean {
        if (type in callerClasses || callerPrefixes.any(type::startsWith)) return false
        return type in nativeHelpers || type in mapping.classes || nativePrefixes.any(type::startsWith)
    }

    companion object {
        fun read(input: Reader): HttpLibraryProfile {
            val text = input.readText()
            fun headers(name: String) = text.lineSequence()
                .filter { it.startsWith("# $name: ") }
                .map { it.substringAfter(": ").trim() }.toList()
            return HttpLibraryProfile(
                ProguardMapping.read(StringReader(text)),
                headers("native-prefix"),
                headers("native-helper").toSet(),
                headers("caller-prefix"),
                headers("caller-class").toSet(),
            )
        }
    }
}
