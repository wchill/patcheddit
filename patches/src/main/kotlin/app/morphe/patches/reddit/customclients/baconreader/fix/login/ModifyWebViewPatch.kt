/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.baconreader.fix.login

import app.morphe.patches.reddit.customclients.baconreader.BaconReaderCompatible
import app.morphe.patches.reddit.customclients.baconreader.misc.extension.sharedExtensionPatch
import app.morphe.patches.reddit.customclients.modifyWebViewPatch

internal val modifyLoginWebView = modifyWebViewPatch(
    extensionPatch = arrayOf(sharedExtensionPatch),
    compatible = BaconReaderCompatible
)
