package app.morphe.patches.reddit.customclients.sync.syncforreddit.annoyances.startup

import app.morphe.patcher.fingerprint

internal val mainActivityOnCreateFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.endsWith("MainActivity;") && method.name == "onCreate"
    }
}
