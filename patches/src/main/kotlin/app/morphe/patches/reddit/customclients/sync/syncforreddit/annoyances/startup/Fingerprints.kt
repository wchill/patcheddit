package app.morphe.patches.reddit.customclients.sync.syncforreddit.annoyances.startup

import app.morphe.patcher.Fingerprint

internal object MainActivityOnCreateFingerprint : Fingerprint(
    definingClass = "/MainActivity;",
    name = "onCreate",
)
