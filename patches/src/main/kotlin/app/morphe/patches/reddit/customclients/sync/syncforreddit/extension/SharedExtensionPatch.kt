package app.morphe.patches.reddit.customclients.sync.syncforreddit.extension

import app.morphe.patches.reddit.customclients.sync.syncforreddit.extension.hooks.initHook
import app.morphe.patches.shared.misc.extension.sharedExtensionPatch

val sharedExtensionPatch = sharedExtensionPatch("syncforreddit", initHook)
