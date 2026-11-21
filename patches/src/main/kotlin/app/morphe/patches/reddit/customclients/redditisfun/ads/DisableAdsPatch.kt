/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.redditisfun.ads

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.MorphePatchSetting
import app.morphe.patches.reddit.customclients.currentMethod
import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import com.android.tools.smali.dexlib2.iface.Method

@Suppress("unused")
val disableAdsPatch = morpheBytecodePatch(
    name = "Block ads",
    description = "Prevents native ad slots and feed, banner, and image-album ad loading.",
    extensionPatch = ExtensionPatches.RIF,
    category = PatchCategory.ADS,
    settingsSummary = "Blocks new ad rows and loads independently of Fake reddit premium. " +
        "Refresh feeds and reopen albums after changing this switch; existing SDK views are not removed.",
) { setting ->
    compatibleWith(AppCompatibility.RedditIsFunFree)
    execute {
        val targets = resolveRifAdsTargets(buildList { classDefForEach(::add) })
        suppressRifAds(targets, setting) { mutableClassDefBy(it) }
    }
}

internal fun suppressRifAds(
    targets: RifAdsTargets,
    setting: MorphePatchSetting,
    mutableClass: (String) -> MutableClass,
) {
    fun patch(method: Method, instructions: String) {
        val owner = mutableClass(method.definingClass)
        setting.patchMethod(owner, currentMethod(owner, method)) { addInstructions(0, instructions) }
    }
    for (gate in targets.disabledGates) patch(gate, "const/4 v0, 0x0\nreturn v0")
    for (loader in targets.disabledLoaders) patch(loader, "return-void")
    // A cached loader can refill or retry after blocking is switched on.
    patch(targets.dequeueNativeAd, "const/4 v0, 0x0\nreturn-object v0")
    // Keep the native image adapter: the ad wrapper can load during row binding, without an explicit load trigger.
    patch(targets.albumAdapterFactory, "return-object p2")
}
