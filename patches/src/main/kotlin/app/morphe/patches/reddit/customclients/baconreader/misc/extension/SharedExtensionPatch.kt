package app.morphe.patches.reddit.customclients.baconreader.misc.extension

import app.morphe.patches.reddit.customclients.baconreader.misc.extension.hooks.initHook
import app.morphe.patches.shared.misc.extension.sharedExtensionPatch

val sharedExtensionPatch = sharedExtensionPatch("baconreader", initHook)
