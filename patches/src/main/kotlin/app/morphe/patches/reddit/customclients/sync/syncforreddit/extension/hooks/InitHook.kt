package app.morphe.patches.reddit.customclients.sync.syncforreddit.extension.hooks

import app.morphe.patcher.Fingerprint
import app.morphe.patches.shared.misc.extension.ExtensionHook

internal val initHook = ExtensionHook(
    Fingerprint(
        custom = { method, _ ->
            method.definingClass == "Lcom/laurencedawson/reddit_sync/RedditApplication;" && method.name == "onCreate"
        }
    ),
    insertIndexResolver = { 1 }
)
