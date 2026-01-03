package app.morphe.patches.reddit.customclients.boostforreddit.fix.downloads

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string

internal val downloadAudioFingerprint = Fingerprint(
    filters = listOf(
        string("/DASH_audio.mp4"),
        string("/audio")
    )
)

