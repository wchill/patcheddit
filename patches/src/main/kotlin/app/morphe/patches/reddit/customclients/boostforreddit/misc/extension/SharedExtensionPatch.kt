package app.morphe.patches.reddit.customclients.boostforreddit.misc.extension

import app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.hooks.initHook
import app.morphe.patches.shared.misc.extension.sharedExtensionPatch

val sharedExtensionPatch = sharedExtensionPatch("boostforreddit", initHook)
