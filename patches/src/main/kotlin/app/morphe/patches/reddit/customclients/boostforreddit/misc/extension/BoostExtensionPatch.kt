/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.misc.extension

import app.morphe.patches.reddit.customclients.boostforreddit.bindings.BoostApi

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.all.misc.extension.SHARED_UTILS_EXTENSION_CLASS
import app.morphe.patches.all.misc.extension.activityOnCreateExtensionHook
import app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.mapping.verifyBoostLibraryReferences
import app.morphe.patches.reddit.customclients.misc.extension.setExtensionReleaseVersion

internal val boostExtensionPatch = bytecodePatch {
    dependsOn(boostNativeLibrariesPatch, boostPrivateLibraryResourcesPatch)
    extendWith {
        val input = checkNotNull(object {}.javaClass.classLoader.getResourceAsStream("extensions/boost.mpe")) {
            "Boost extension payload was not found"
        }
        prepareBoostExtension(input)
    }

    execute {
        classDefBy(SHARED_UTILS_EXTENSION_CLASS)
    }

    finalize {
        activityOnCreateExtensionHook(BoostApi.MyApplication.type)(SHARED_UTILS_EXTENSION_CLASS)
        setExtensionReleaseVersion()
        val classes = buildList { classDefForEach(::add) }
        val nativeTypes = BoostNativeLibraries.classes.mapTo(HashSet()) { it.type }
        val extensionTypes = classes.filter { it.type !in nativeTypes }.mapTo(HashSet()) { it.type }
        verifyBoostLibraryReferences(classes, extensionTypes)
    }
}
