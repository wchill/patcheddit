package app.morphe.patches.reddit.customclients.slide.misc.extension

import app.morphe.patches.reddit.customclients.slide.misc.extension.hooks.initHook
import app.morphe.patches.shared.misc.extension.sharedExtensionPatch

val sharedExtensionPatch = sharedExtensionPatch("slide", initHook)
