/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.http

import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.interceptHttpRequestsPatch


internal const val BOOST_OKHTTP_HOOK_DESCRIPTOR = "Lapp/morphe/extension/boost/http/OkHttpRequestHook;"

@Suppress("unused")
val interceptHttpRequests = interceptHttpRequestsPatch(
    dependsOn = arrayOf(ExtensionPatches.Boost),
    compatibleWith = AppCompatibility.Boost,
    extensionClassDescriptor = BOOST_OKHTTP_HOOK_DESCRIPTOR
)
