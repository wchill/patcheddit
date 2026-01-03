package app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.hooks

import app.morphe.patcher.Fingerprint
import app.morphe.patches.shared.misc.extension.ExtensionHook

internal val initHook = ExtensionHook(
    Fingerprint(
        custom = { method, _ ->
            method.definingClass == "Lcom/rubenmayayo/reddit/MyApplication;" && method.name == "onCreate"
        }
    ),
    insertIndexResolver = { 1 }
)
