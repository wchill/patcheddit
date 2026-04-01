package app.morphe.patches.reddit.customclients.continuum.misc.extension

import app.morphe.patches.reddit.customclients.continuum.misc.extension.hooks.initHook
import app.morphe.patches.all.misc.extension.sharedExtensionPatch

val sharedExtensionPatch = sharedExtensionPatch("continuum", initHook)
