package app.morphe.patches.reddit.customclients.sync.syncforreddit.annoyances.startup

import app.morphe.patcher.Fingerprint

internal val mainActivityOnCreateFingerprint = Fingerprint(
    definingClass = "/MainActivity;",
    name = "onCreate",
)
