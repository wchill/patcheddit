package app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.hooks

import app.morphe.patcher.Fingerprint
import app.morphe.patches.shared.misc.extension.ExtensionHook

internal val initHook = ExtensionHook(
    Fingerprint(
        definingClass = "Lcom/rubenmayayo/reddit/MyApplication;",
        name = "onCreate",
    ),
    insertIndexResolver = { 1 }
)
