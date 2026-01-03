package app.morphe.patches.reddit.customclients.sync.syncforreddit.annoyances.startup

import app.morphe.patcher.Fingerprint

internal val mainActivityOnCreateFingerprint = Fingerprint(
    custom = { method, classDef ->
        classDef.endsWith("MainActivity;") && method.name == "onCreate"
    }
)
