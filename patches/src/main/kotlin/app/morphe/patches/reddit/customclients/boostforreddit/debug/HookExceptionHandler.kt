/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Modifications Copyright 2026 brealorg.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 */

package app.morphe.patches.reddit.customclients.boostforreddit.debug

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.all.misc.resources.addResourcesPatch
import app.morphe.patches.reddit.customclients.crashLogPatch
import app.morphe.patches.reddit.customclients.boostforreddit.bindings.BoostApi
import app.morphe.patches.reddit.customclients.boostforreddit.misc.settings.boostMorpheSettingsPatch
import app.morphe.patches.reddit.customclients.boostforreddit.misc.settings.boostMorpheSettingsResourcesPatch
import app.morphe.patches.reddit.customclients.boostforreddit.misc.settings.addBoostSettings
import app.morphe.util.ResourceGroup

const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/boost/debug/ExceptionHook;"

private val exceptionLogResourcesPatch = resourcePatch(default = false) {
    dependsOn(
        boostMorpheSettingsPatch,
        boostMorpheSettingsResourcesPatch,
        addResourcesPatch(
            "boost",
            ResourceGroup("xml", "morphe_boost_diagnostics_entry.xml"),
        ),
    )

    execute {
        addBoostSettings("morphe_boost_diagnostics_entry")
    }
}

@Suppress("unused")
val hookExceptionHandler = crashLogPatch(
    extensionPatch = ExtensionPatches.Boost,
    compatible = AppCompatibility.Boost,
    mainActivity = BoostApi.BaseActivity.onCreate.fingerprint(),
    exceptionHandler = ExceptionHandlerFingerprint,
    exceptionHookDescriptor = EXTENSION_CLASS_DESCRIPTOR,
    settingsPatch = exceptionLogResourcesPatch,
    description = "Logs reported errors and fatal crashes, viewable in Morphe settings.",
)
