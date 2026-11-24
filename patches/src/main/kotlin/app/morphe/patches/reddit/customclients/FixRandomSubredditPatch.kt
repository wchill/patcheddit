/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.PatchDefinition
import app.morphe.patches.all.misc.PatchCategory

private const val INTERCEPTOR = "Lapp/morphe/extension/shared/fixes/feed/RandomSubredditInterceptor;"

internal fun fixRandomSubredditPatch(
    extensionPatch: Patch,
    compatibility: Array<Compatibility>,
    block: PatchDefinition.(MorphePatchSetting) -> Unit = { setting ->
        execute {
            setting.returnEnabled(Fingerprint(definingClass = INTERCEPTOR, name = "isPatchIncluded").method)
        }
    },
) = morpheBytecodePatch(
    name = "Fix random subreddit",
    description = "Restores Random using Reddit search or the optional RedditRand service.",
    category = PatchCategory.FEED,
    extensionPatch = extensionPatch,
    optionsResource = "morphe_random_subreddit",
) { setting ->
    compatibleWith(*compatibility)
    block(setting)
}

@Suppress("unused")
val boostRandomSubredditPatch = fixRandomSubredditPatch(ExtensionPatches.Boost, AppCompatibility.Boost)

@Suppress("unused")
val joeyRandomSubredditPatch = fixRandomSubredditPatch(ExtensionPatches.Joey, AppCompatibility.JoeyForReddit)

@Suppress("unused")
val relayRandomSubredditPatch = fixRandomSubredditPatch(ExtensionPatches.Relay, AppCompatibility.Relay)

@Suppress("unused")
val rifRandomSubredditPatch = fixRandomSubredditPatch(ExtensionPatches.RIF, AppCompatibility.RedditIsFun)

@Suppress("unused")
val syncRandomSubredditPatch = fixRandomSubredditPatch(ExtensionPatches.Sync, AppCompatibility.SyncForReddit)
