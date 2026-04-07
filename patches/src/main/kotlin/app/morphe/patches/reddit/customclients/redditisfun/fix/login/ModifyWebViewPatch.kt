/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.redditisfun.fix.login

import app.morphe.patches.reddit.customclients.redditisfun.RedditIsFunCompatible
import app.morphe.patches.reddit.customclients.redditisfun.misc.extension.sharedExtensionPatch
import app.morphe.patches.reddit.customclients.modifyWebViewPatch

internal val modifyLoginWebView = modifyWebViewPatch(
    extensionPatch = arrayOf(sharedExtensionPatch),
    compatible = RedditIsFunCompatible
)
