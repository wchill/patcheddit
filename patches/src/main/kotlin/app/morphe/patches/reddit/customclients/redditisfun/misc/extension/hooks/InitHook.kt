/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.redditisfun.misc.extension.hooks

import app.morphe.patcher.Fingerprint
import app.morphe.patches.shared.misc.extension.ExtensionHook

internal val initHook = ExtensionHook(
    Fingerprint(
        custom = { method, _ ->
            method.definingClass == "Lcom/andrewshu/android/reddit/RedditIsFunApplication;" && method.name == "onCreate"
        }
    ),
    insertIndexResolver = { 1 }
)
