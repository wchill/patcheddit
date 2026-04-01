package app.morphe.patches.reddit.customclients.relay.misc.extension

import app.morphe.patches.reddit.customclients.relay.misc.extension.hooks.initHook
import app.morphe.patches.all.misc.extension.sharedExtensionPatch

val sharedExtensionPatch = sharedExtensionPatch("relay", initHook)
