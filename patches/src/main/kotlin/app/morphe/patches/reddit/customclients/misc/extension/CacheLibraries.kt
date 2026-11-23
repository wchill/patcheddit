/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.misc.extension

import app.morphe.patcher.patch.patch
import app.morphe.patcher.patch.resourceFile

private const val CACHE_LIBRARY_PACKAGE = "app.morphe.extension.vendored"
private const val CACHE_LIBRARY_PREFIX = "Lapp/morphe/extension/vendored/"
private val cacheLibraryPackages = listOf(
    "Landroidx/room/", "Landroidx/sqlite/", "Landroidx/arch/", "Landroidx/collection/", "Lkotlinx/coroutines/",
)

internal fun privateCacheLibraryType(type: String): String =
    if (cacheLibraryPackages.any(type::startsWith)) CACHE_LIBRARY_PREFIX + type.removePrefix("L") else type

internal val cacheLibraryResourcesPatch = patch(default = false) {
    execute {
        val coroutineServices = mapOf(
            "kotlinx.coroutines.internal.MainDispatcherFactory" to "kotlinx.coroutines.android.AndroidDispatcherFactory",
            "kotlinx.coroutines.CoroutineExceptionHandler" to "kotlinx.coroutines.android.AndroidExceptionPreHandler",
        )
        for ((service, provider) in coroutineServices) {
            resourceFile("META-INF/services/$CACHE_LIBRARY_PACKAGE.$service", copy = false)
                .writeText("$CACHE_LIBRARY_PACKAGE.$provider\n")
        }
    }
}
