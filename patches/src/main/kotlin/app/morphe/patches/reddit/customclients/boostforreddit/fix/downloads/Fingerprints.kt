package app.morphe.patches.reddit.customclients.boostforreddit.fix.downloads

import app.morphe.patcher.fingerprint

internal val downloadAudioFingerprint = fingerprint {
    strings("/DASH_audio.mp4", "/audio")
}
