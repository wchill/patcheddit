package app.morphe.patches.reddit.customclients.relay.misc.extension.hooks

import app.morphe.patcher.Fingerprint
import app.morphe.patches.shared.misc.extension.ExtensionHook

internal val initHook = ExtensionHook(
    Fingerprint(
        custom = { method, classDef ->
            classDef.type == "Lreddit/news/RelayApplication;" && method.name == "onCreate"
        }
    )
)
