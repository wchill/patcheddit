package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.slink

import app.morphe.patcher.Fingerprint

internal object LinkHelperOpenLinkFingerprint : Fingerprint(
    strings = listOf("Link title: ")
)
