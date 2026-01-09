package app.morphe.patches.reddit.customclients.redditisfun.misc.extension

import app.morphe.patches.reddit.customclients.redditisfun.misc.extension.hooks.initHook
import app.morphe.patches.shared.misc.extension.sharedExtensionPatch

val sharedExtensionPatch = sharedExtensionPatch("redditisfun", initHook)
