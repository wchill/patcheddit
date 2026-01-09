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
