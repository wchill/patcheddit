package app.morphe.patches.reddit.customclients.infinity.misc.extension

import app.morphe.patches.reddit.customclients.infinity.misc.extension.hooks.initHook
import app.morphe.patches.all.misc.extension.sharedExtensionPatch

val sharedExtensionPatch = sharedExtensionPatch("infinity", initHook)
